package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.execution.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Main pipeline execution engine
 * 
 * Implements structured concurrency with comprehensive error handling,
 * timeout management, and event-driven communication. Supports parallel
 * execution, resource cleanup, and performance monitoring.
 */
public class PipelineExecutor(
    private val config: ExecutorConfig = ExecutorConfig.default()
) {
    
    private val stageExecutor = StageExecutor(config)
    private val eventBus = DefaultPipelineEventBus(config)
    private val executionSemaphore = Semaphore(config.maxConcurrentPipelines)
    
    // Fault tolerance components
    private val faultTolerantExecutor = if (config.faultToleranceConfig.enablePipelineFaultTolerance) {
        FaultTolerantExecutor(
            name = "pipeline-executor",
            circuitBreaker = CircuitBreaker(
                name = "pipeline-circuit-breaker",
                failureThreshold = config.faultToleranceConfig.circuitBreakerConfig.failureThreshold,
                timeoutWindow = config.faultToleranceConfig.circuitBreakerConfig.timeoutWindow,
                halfOpenRetryTimeout = config.faultToleranceConfig.circuitBreakerConfig.halfOpenRetryTimeout
            ),
            retryPolicy = RetryPolicy(
                maxAttempts = config.faultToleranceConfig.retryPolicyConfig.maxAttempts,
                baseDelay = config.faultToleranceConfig.retryPolicyConfig.baseDelay,
                maxDelay = config.faultToleranceConfig.retryPolicyConfig.maxDelay,
                multiplier = config.faultToleranceConfig.retryPolicyConfig.multiplier,
                jitter = config.faultToleranceConfig.retryPolicyConfig.jitter
            ),
            bulkhead = Bulkhead(
                name = "pipeline-bulkhead",
                maxConcurrentCalls = config.faultToleranceConfig.bulkheadConfig.maxConcurrentPipelines
            )
        )
    } else null
    
    // Supervisor job for handling failures gracefully
    private val supervisorJob = SupervisorJob()
    
    // Main execution scope with exception handler
    private val executorScope = CoroutineScope(
        supervisorJob + 
        config.dispatcherConfig.systemDispatcher +
        CoroutineExceptionHandler { _, exception ->
            // Log unhandled exceptions but don't crash the executor
            println("Unhandled exception in pipeline executor: ${exception.message}")
        }
    )
    
    init {
        // Register event listener if provided
        config.eventListener?.let { eventBus.subscribe(it) }
    }
    
    /**
     * Executes a complete pipeline with structured concurrency
     * 
     * @param pipeline Pipeline to execute
     * @param context Execution context
     * @return Pipeline execution result
     */
    public suspend fun execute(pipeline: Pipeline, context: ExecutionContext): PipelineResult {
        // Acquire execution permit
        executionSemaphore.acquire()
        
        return try {
            executeWithPermit(pipeline, context)
        } finally {
            executionSemaphore.release()
        }
    }
    
    private suspend fun executeWithPermit(pipeline: Pipeline, context: ExecutionContext): PipelineResult {
        val executionId = UUID.randomUUID().toString()
        val startTime = Instant.now()
        
        // Publish pipeline started event
        eventBus.publish(PipelineEvent.PipelineStarted(executionId, pipeline.id))
        
        // Record metrics if enabled
        config.metricsCollector?.recordPipelineStart(executionId, pipeline.id)
        
        return try {
            // Execute with fault tolerance if enabled
            val stageResults = if (faultTolerantExecutor != null) {
                faultTolerantExecutor.execute {
                    executeStagesWithTimeout(pipeline, context, executionId)
                }
            } else {
                executeStagesWithTimeout(pipeline, context, executionId)
            }
            
            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime)
            val status = determineOverallStatus(stageResults)
            
            val pipelineResult = PipelineResult(
                executionId = executionId,
                status = status,
                stages = stageResults,
                duration = duration,
                startedAt = startTime,
                finishedAt = endTime,
                metadata = buildPipelineMetadata(pipeline, context, stageResults)
            )
            
            // Publish completion event
            eventBus.publish(PipelineEvent.PipelineCompleted(executionId, status, duration))
            
            // Record completion metrics
            config.metricsCollector?.recordPipelineCompletion(executionId, duration, status)
            
            pipelineResult
            
        } catch (e: TimeoutCancellationException) {
            val duration = Duration.between(startTime, Instant.now())
            val timeoutResult = PipelineResult(
                executionId = executionId,
                status = PipelineStatus.TIMEOUT,
                stages = emptyList(),
                duration = duration,
                startedAt = startTime,
                finishedAt = Instant.now(),
                error = PipelineTimeoutException("Pipeline timed out after ${config.globalTimeout}", config.globalTimeout!!, e)
            )
            
            eventBus.publish(PipelineEvent.PipelineCompleted(executionId, PipelineStatus.TIMEOUT, duration))
            config.metricsCollector?.recordPipelineCompletion(executionId, duration, PipelineStatus.TIMEOUT)
            
            throw timeoutResult.error!!
            
        } catch (e: CancellationException) {
            eventBus.publish(PipelineEvent.CancellationRequested(executionId, "Pipeline execution cancelled"))
            throw e
        } catch (e: Exception) {
            val duration = Duration.between(startTime, Instant.now())
            val error = PipelineExecutionException("Pipeline execution failed", e)
            
            eventBus.publish(PipelineEvent.ErrorOccurred(executionId, "pipeline", error))
            eventBus.publish(PipelineEvent.PipelineCompleted(executionId, PipelineStatus.FAILURE, duration))
            config.metricsCollector?.recordPipelineCompletion(executionId, duration, PipelineStatus.FAILURE)
            
            PipelineResult(
                executionId = executionId,
                status = PipelineStatus.FAILURE,
                stages = emptyList(),
                duration = duration,
                startedAt = startTime,
                finishedAt = Instant.now(),
                error = error
            )
        }
    }
    
    /**
     * Executes all stages with timeout wrapper
     */
    private suspend fun executeStagesWithTimeout(
        pipeline: Pipeline,
        context: ExecutionContext,
        executionId: String
    ): List<StageResult> {
        return if (config.globalTimeout != null) {
            withTimeout(config.globalTimeout.inWholeMilliseconds) {
                executeStages(pipeline, context, executionId)
            }
        } else {
            executeStages(pipeline, context, executionId)
        }
    }
    
    /**
     * Executes all stages in sequence, respecting dependencies and conditions
     */
    private suspend fun executeStages(
        pipeline: Pipeline,
        context: ExecutionContext,
        executionId: String
    ): List<StageResult> = coroutineScope {
        
        val results = mutableListOf<StageResult>()
        val pipelineContext = context.copy(
            environment = context.environment + pipeline.globalEnvironment + mapOf("EXECUTION_ID" to executionId)
        )
        
        for ((index, stage) in pipeline.stages.withIndex()) {
            // Publish stage started event
            eventBus.publish(PipelineEvent.StageStarted(executionId, stage.name, index))
            
            try {
                val stageResult = stageExecutor.execute(stage, pipelineContext, eventBus)
                results.add(stageResult)
                
                // Publish stage completed event
                eventBus.publish(PipelineEvent.StageCompleted(executionId, stage.name, stageResult))
                
                // Check if we should continue based on result and fail-fast setting
                if (shouldStopExecution(stageResult)) {
                    break
                }
                
            } catch (e: CancellationException) {
                // Cooperative cancellation - propagate up
                throw e
            } catch (e: Exception) {
                val errorResult = StageResult(
                    stageName = stage.name,
                    status = StageStatus.FAILURE,
                    steps = emptyList(),
                    duration = Duration.ZERO,
                    agent = stage.agent,
                    error = e
                )
                results.add(errorResult)
                
                eventBus.publish(PipelineEvent.ErrorOccurred(executionId, "stage:${stage.name}", e))
                eventBus.publish(PipelineEvent.StageCompleted(executionId, stage.name, errorResult))
                
                // Stop if fail-fast is enabled
                if (config.defaultFailFast) {
                    break
                }
            }
        }
        
        results
    }
    
    /**
     * Determines if execution should stop based on stage result
     */
    private fun shouldStopExecution(stageResult: StageResult): Boolean {
        return when (stageResult.status) {
            StageStatus.FAILURE -> config.defaultFailFast
            StageStatus.TIMEOUT -> true
            StageStatus.CANCELLED -> true
            else -> false
        }
    }
    
    /**
     * Determines overall pipeline status from stage results
     */
    private fun determineOverallStatus(stageResults: List<StageResult>): PipelineStatus {
        if (stageResults.isEmpty()) return PipelineStatus.SUCCESS
        
        val statuses = stageResults.map { it.status }
        
        return when {
            statuses.any { it == StageStatus.CANCELLED } -> PipelineStatus.CANCELLED
            statuses.any { it == StageStatus.TIMEOUT } -> PipelineStatus.TIMEOUT
            statuses.all { it == StageStatus.SUCCESS } -> PipelineStatus.SUCCESS
            statuses.any { it == StageStatus.FAILURE } -> {
                if (statuses.any { it == StageStatus.SUCCESS }) PipelineStatus.PARTIAL_SUCCESS
                else PipelineStatus.FAILURE
            }
            statuses.any { it == StageStatus.PARTIAL_SUCCESS || it == StageStatus.PARTIAL_FAILURE } -> PipelineStatus.PARTIAL_SUCCESS
            else -> PipelineStatus.SUCCESS
        }
    }
    
    /**
     * Builds pipeline metadata for result analysis
     */
    private fun buildPipelineMetadata(
        pipeline: Pipeline,
        context: ExecutionContext,
        stageResults: List<StageResult>
    ): Map<String, Any> {
        return mapOf(
            "pipelineId" to pipeline.id,
            "totalStages" to pipeline.stages.size,
            "executedStages" to stageResults.size,
            "successfulStages" to stageResults.count { it.status == StageStatus.SUCCESS },
            "failedStages" to stageResults.count { it.status == StageStatus.FAILURE },
            "environment" to pipeline.globalEnvironment,
            "agent" to (pipeline.agent?.toString() ?: "any"),
            "executorConfig" to mapOf(
                "maxConcurrentPipelines" to config.maxConcurrentPipelines,
                "defaultStageTimeout" to config.defaultStageTimeout.toString(),
                "globalTimeout" to config.globalTimeout?.toString()
            )
        )
    }
    
    /**
     * Closes the executor and releases resources
     */
    public suspend fun close() {
        supervisorJob.cancel()
        eventBus.close()
    }
}

/**
 * Default implementation of PipelineEventBus
 */
private class DefaultPipelineEventBus(
    private val config: ExecutorConfig
) : PipelineEventBus {
    
    private val listeners = mutableSetOf<PipelineEventListener>()
    private val eventChannel = Channel<PipelineEvent>(config.eventChannelBufferSize)
    private val processingJob: Job
    
    init {
        processingJob = CoroutineScope(config.dispatcherConfig.systemDispatcher).launch {
            for (event in eventChannel) {
                listeners.forEach { listener ->
                    try {
                        listener.onEvent(event)
                    } catch (e: Exception) {
                        // Log but don't fail event processing
                        println("Event listener failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    override suspend fun publish(event: PipelineEvent) {
        eventChannel.trySend(event)
    }
    
    override fun subscribe(listener: PipelineEventListener) {
        listeners.add(listener)
    }
    
    override fun unsubscribe(listener: PipelineEventListener) {
        listeners.remove(listener)
    }
    
    override suspend fun close() {
        eventChannel.close()
        processingJob.cancel()
    }
}