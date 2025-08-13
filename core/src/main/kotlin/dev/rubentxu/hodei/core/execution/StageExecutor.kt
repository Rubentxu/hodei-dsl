package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.execution.*
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant

/**
 * Stage execution engine
 * 
 * Handles different stage types including sequential, parallel, and conditional
 * stages with proper resource management and error handling.
 */
public class StageExecutor(
    private val config: ExecutorConfig = ExecutorConfig.default()
) {
    
    private val stepExecutor = StepExecutor()
    private val workloadAnalyzer = WorkloadAnalyzer()
    
    // Fault tolerance for stage execution
    private val faultTolerantExecutor = if (config.faultToleranceConfig.enableStageFaultTolerance) {
        FaultTolerantExecutor(
            name = "stage-executor",
            circuitBreaker = CircuitBreaker(
                name = "stage-circuit-breaker",
                failureThreshold = config.faultToleranceConfig.circuitBreakerConfig.failureThreshold,
                timeoutWindow = config.faultToleranceConfig.circuitBreakerConfig.timeoutWindow,
                halfOpenRetryTimeout = config.faultToleranceConfig.circuitBreakerConfig.halfOpenRetryTimeout
            ),
            bulkhead = Bulkhead(
                name = "stage-bulkhead", 
                maxConcurrentCalls = config.faultToleranceConfig.bulkheadConfig.maxConcurrentStages
            )
        )
    } else null
    
    /**
     * Executes a stage with appropriate strategy based on stage type
     * 
     * @param stage Stage to execute  
     * @param context Execution context
     * @param eventBus Event bus for publishing events
     * @return Stage execution result
     */
    public suspend fun execute(
        stage: Stage,
        context: ExecutionContext,
        eventBus: PipelineEventBus? = null
    ): StageResult {
        
        val stageStartTime = Instant.now()
        val stageContext = prepareStageContext(stage, context)
        
        return try {
            // Execute with fault tolerance if enabled
            val result = if (faultTolerantExecutor != null) {
                faultTolerantExecutor.execute {
                    executeSequentialStage(stage, stageContext, eventBus)
                }
            } else {
                executeSequentialStage(stage, stageContext, eventBus)
            }
            
            // Execute post actions if stage completed
            executePostActions(stage, stageContext, result)
            
            result
            
        } catch (e: TimeoutCancellationException) {
            val duration = Duration.between(stageStartTime, Instant.now())
            StageResult(
                stageName = stage.name,
                status = StageStatus.TIMEOUT,
                steps = emptyList(),
                duration = duration,
                agent = stage.agent,
                error = StageTimeoutException(stage.name, "Stage timed out", config.defaultStageTimeout, e)
            )
        } catch (e: CancellationException) {
            // Cooperative cancellation
            throw e
        } catch (e: Exception) {
            val duration = Duration.between(stageStartTime, Instant.now())
            StageResult(
                stageName = stage.name,
                status = StageStatus.FAILURE,
                steps = emptyList(),
                duration = duration,
                agent = stage.agent,
                error = StageExecutionException(stage.name, "Stage execution failed: ${e.message}", e)
            )
        } finally {
            cleanupStageContext(stage, stageContext)
        }
    }
    
    /**
     * Executes a sequential stage with steps in order
     */
    private suspend fun executeSequentialStage(
        stage: Stage,
        context: ExecutionContext,
        eventBus: PipelineEventBus?
    ): StageResult {
        val stageStartTime = Instant.now()
        val dispatcher = selectStageDispatcher(stage)
        
        return withContext(dispatcher) {
            val stageResult = executeStepsSequentially(stage.steps, context, eventBus)
            
            val duration = Duration.between(stageStartTime, Instant.now())
            val status = determineStageStatus(stageResult, config.defaultFailFast)
            
            StageResult(
                stageName = stage.name,
                status = status,
                steps = stageResult,
                duration = duration,
                agent = stage.agent,
                metadata = mapOf(
                    "dispatcher" to (currentCoroutineContext()[CoroutineDispatcher]?.toString() ?: "unknown"),
                    "thread" to Thread.currentThread().name,
                    "stageType" to "sequential"
                )
            )
        }
    }
    
    
    
    
    /**
     * Executes steps sequentially within a stage
     */
    private suspend fun executeStepsSequentially(
        steps: List<Step>,
        context: ExecutionContext,
        eventBus: PipelineEventBus?
    ): List<StepResult> {
        
        val stepResults = mutableListOf<StepResult>()
        
        for ((index, step) in steps.withIndex()) {
            eventBus?.publish(PipelineEvent.StepStarted(context.executionId, context.currentStage ?: "unknown", step.toString(), index))
            
            try {
                val stepResult = stepExecutor.execute(step, context)
                stepResults.add(stepResult)
                
                eventBus?.publish(PipelineEvent.StepCompleted(context.currentStage ?: "unknown", step.toString(), context.executionId, stepResult))
                
                // Stop on failure if not recoverable
                if (stepResult.status == StepStatus.FAILURE && !isRecoverableFailure(step, stepResult)) {
                    break
                }
                
            } catch (e: CancellationException) {
                throw e // Propagate cancellation
            } catch (e: Exception) {
                val errorResult = StepResult(
                    stepName = step.toString(),
                    status = StepStatus.FAILURE,
                    duration = Duration.ZERO,
                    error = e
                )
                stepResults.add(errorResult)
                
                eventBus?.publish(PipelineEvent.ErrorOccurred(context.executionId, "step:${step}", e))
                eventBus?.publish(PipelineEvent.StepCompleted(context.currentStage ?: "unknown", step.toString(), context.executionId, errorResult))
                
                if (!isRecoverableFailure(step, errorResult)) {
                    break
                }
            }
        }
        
        return stepResults
    }
    
    /**
     * Prepares stage-specific execution context
     * 
     * Creates a new context with stage-specific environment variables.
     * Stage environment variables override global ones, but are isolated
     * to this stage execution only. This ensures proper environment isolation
     * between stages while maintaining global variables.
     */
    private fun prepareStageContext(stage: Stage, context: ExecutionContext): ExecutionContext {
        // Create stage environment by merging pipeline context + stage-specific variables
        // The context already contains: system env + pipeline global env + EXECUTION_ID
        // We add stage-specific environment variables on top of this base
        val stageEnvironment = context.environment + stage.environment + mapOf(
            "STAGE_NAME" to stage.name
        )
        
        return context.copy(
            environment = stageEnvironment,
            launcher = selectCommandLauncher(stage, context)
        )
    }
    
    /**
     * Selects appropriate command launcher based on stage agent
     */
    private fun selectCommandLauncher(stage: Stage, context: ExecutionContext): CommandLauncher {
        return when (stage.agent) {
            is Agent.Docker -> context.launcher // TODO: Implement Docker launcher
            is Agent.Kubernetes -> context.launcher // TODO: Implement K8s launcher
            else -> context.launcher
        }
    }
    
    /**
     * Selects appropriate dispatcher for stage execution
     */
    private fun selectStageDispatcher(stage: Stage): CoroutineDispatcher {
        return when (stage.agent) {
            is Agent.Docker -> config.dispatcherConfig.selectDispatcher(WorkloadType.NETWORK)
            is Agent.Kubernetes -> config.dispatcherConfig.selectDispatcher(WorkloadType.NETWORK)
            else -> workloadAnalyzer.analyzeAndSelect("stage", hint = WorkloadType.DEFAULT)
        }
    }
    
    /**
     * Evaluates when condition for conditional stages
     */
    private fun evaluateWhenCondition(condition: WhenCondition, context: ExecutionContext): Boolean {
        // Use the built-in evaluate method with environment as context
        return condition.evaluate(context.environment)
    }
    
    /**
     * Determines stage status from step results
     */
    private fun determineStageStatus(stepResults: List<StepResult>, failFast: Boolean): StageStatus {
        if (stepResults.isEmpty()) return StageStatus.SUCCESS
        
        val statuses = stepResults.map { it.status }
        
        return when {
            statuses.any { it == StepStatus.CANCELLED } -> StageStatus.CANCELLED
            statuses.any { it == StepStatus.TIMEOUT } -> StageStatus.TIMEOUT
            statuses.all { it == StepStatus.SUCCESS || it == StepStatus.SKIPPED } -> StageStatus.SUCCESS
            statuses.any { it == StepStatus.FAILURE } -> {
                if (failFast || statuses.all { it == StepStatus.FAILURE || it == StepStatus.SKIPPED }) {
                    StageStatus.FAILURE
                } else {
                    StageStatus.PARTIAL_SUCCESS
                }
            }
            else -> StageStatus.SUCCESS
        }
    }
    
    
    
    /**
     * Checks if a step failure is recoverable
     */
    private fun isRecoverableFailure(step: Step, result: StepResult): Boolean {
        // Most failures are not recoverable by default
        // This can be enhanced based on step type and error analysis
        return false
    }
    
    /**
     * Executes post actions for a stage
     */
    private suspend fun executePostActions(stage: Stage, context: ExecutionContext, result: StageResult) {
        // Execute post actions from the stage
        for (action in stage.post) {
            try {
                when (action.condition) {
                    PostCondition.ALWAYS -> executePostAction(action, context)
                    PostCondition.SUCCESS -> if (result.isSuccessful) executePostAction(action, context)
                    PostCondition.FAILURE -> if (!result.isSuccessful) executePostAction(action, context)
                    else -> executePostAction(action, context)
                }
            } catch (e: Exception) {
                // Log post action failures but don't fail the stage
                // TODO: Replace with proper logging framework
                System.err.println("Post action failed: ${e.message}")
            }
        }
    }
    
    /**
     * Executes a single post action
     */
    private suspend fun executePostAction(action: PostAction, context: ExecutionContext) {
        for (step in action.steps) {
            stepExecutor.execute(step, context)
        }
    }
    
    /**
     * Cleans up stage-specific resources
     */
    private fun cleanupStageContext(stage: Stage, context: ExecutionContext) {
        // Perform any stage-specific cleanup
        // This can be enhanced with specific cleanup logic
    }
}

// Extension property to get current stage from context
private val ExecutionContext.currentStage: String?
    get() = environment["CURRENT_STAGE"]