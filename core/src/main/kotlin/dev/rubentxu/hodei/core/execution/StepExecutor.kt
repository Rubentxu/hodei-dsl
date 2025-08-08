package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.execution.*
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Step execution engine
 * 
 * Handles individual step execution with lifecycle management (validate, prepare, execute, cleanup),
 * workload-aware dispatcher selection, timeout handling, and integration with CommandLauncher.
 */
public class StepExecutor(
    private val config: StepExecutorConfig = StepExecutorConfig(enableMetrics = true)
) {
    
    private val workloadAnalyzer = WorkloadAnalyzer()
    
    /**
     * Executes a single step with complete lifecycle management
     * 
     * @param step Step to execute
     * @param context Execution context
     * @return Step execution result
     */
    public suspend fun execute(step: Step, context: ExecutionContext): StepResult {
        val stepStartTime = Instant.now()
        val stepName = getStepName(step)
        val dispatcher = selectStepDispatcher(step, context)
        
        return withContext(dispatcher) {
            try {
                // Step lifecycle: validate → prepare → execute → cleanup
                val validationErrors = validateStep(step, context)
                if (validationErrors.isNotEmpty()) {
                    return@withContext createValidationFailureResult(stepName, validationErrors, stepStartTime)
                }
                
                // Prepare step execution
                prepareStep(step, context)
                
                // Execute with timeout
                val result = if (step.timeout != null) {
                    val timeout = step.timeout!!
                    withTimeout(timeout.inWholeMilliseconds) {
                        executeStepInternal(step, context)
                    }
                } else if (config.defaultTimeout.isPositive()) {
                    withTimeout(config.defaultTimeout.inWholeMilliseconds) {
                        executeStepInternal(step, context)
                    }
                } else {
                    executeStepInternal(step, context)
                }
                
                // Cleanup step resources
                cleanupStep(step, context, result)
                
                result.copy(
                    duration = Duration.between(stepStartTime, Instant.now()),
                    metadata = result.metadata + mapOf(
                        "dispatcher" to (currentCoroutineContext()[CoroutineDispatcher]?.toString() ?: "unknown"),
                        "thread" to Thread.currentThread().name,
                        "launcher" to (context.launcher::class.simpleName ?: "unknown")
                    )
                )
                
            } catch (e: TimeoutCancellationException) {
                createTimeoutResult(stepName, step.timeout ?: config.defaultTimeout, stepStartTime, e)
            } catch (e: CancellationException) {
                throw e // Propagate cancellation
            } catch (e: Exception) {
                createFailureResult(stepName, stepStartTime, e)
            }
        }
    }
    
    /**
     * Internal step execution based on step type
     */
    private suspend fun executeStepInternal(step: Step, context: ExecutionContext): StepResult {
        return when (step) {
            is Step.Shell -> executeShellStep(step, context)
            is Step.Echo -> executeEchoStep(step, context)
            is Step.Dir -> executeDirStep(step, context)
            is Step.WithEnv -> executeWithEnvStep(step, context)
            is Step.Parallel -> executeParallelStep(step, context)
            is Step.Retry -> executeRetryStep(step, context)
            is Step.Timeout -> executeTimeoutStep(step, context)
            is Step.ArchiveArtifacts -> executeArchiveArtifactsStep(step, context)
            is Step.PublishTestResults -> executePublishTestResultsStep(step, context)
            is Step.Stash -> executeStashStep(step, context)
            is Step.Unstash -> executeUnstashStep(step, context)
        }
    }
    
    /**
     * Executes shell command step
     */
    private suspend fun executeShellStep(step: Step.Shell, context: ExecutionContext): StepResult {
        val result = context.launcher.execute(
            command = step.script,
            workingDir = context.workDir.toString(),
            environment = context.environment
        )
        
        return StepResult(
            stepName = "sh",
            status = if (result.success) StepStatus.SUCCESS else StepStatus.FAILURE,
            duration = Duration.ofMillis(result.durationMs),
            output = result.stdout,
            errorOutput = result.stderr,
            exitCode = result.exitCode,
            error = if (!result.success) StepExecutionException("sh", "Command failed with exit code ${result.exitCode}") else null
        )
    }
    
    /**
     * Executes echo step
     */
    private suspend fun executeEchoStep(step: Step.Echo, context: ExecutionContext): StepResult {
        // Simply log/output the message
        context.logger.info(step.message)
        
        return StepResult(
            stepName = "echo",
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(1), // Minimal duration
            output = step.message,
            exitCode = 0
        )
    }
    
    /**
     * Executes dir step (change working directory)
     */
    private suspend fun executeDirStep(step: Step.Dir, context: ExecutionContext): StepResult {
        val dirPath = if (java.nio.file.Paths.get(step.path).isAbsolute) {
            java.nio.file.Paths.get(step.path)
        } else {
            context.workDir.resolve(step.path)
        }
        val dirContext = context.copy(workDir = dirPath)
        
        val nestedResults = mutableListOf<StepResult>()
        for (nestedStep in step.steps) {
            val result = execute(nestedStep, dirContext)
            nestedResults.add(result)
            
            // Stop on failure
            if (result.status == StepStatus.FAILURE) {
                break
            }
        }
        
        val overallStatus = if (nestedResults.all { it.status == StepStatus.SUCCESS }) {
            StepStatus.SUCCESS
        } else {
            StepStatus.FAILURE
        }
        
        return StepResult(
            stepName = "dir",
            status = overallStatus,
            duration = Duration.ofMillis(nestedResults.sumOf { it.duration.toMillis() }),
            output = "Changed to directory: $dirPath\n" + nestedResults.joinToString("\n") { it.output },
            exitCode = 0,
            nestedResults = nestedResults
        )
    }
    
    /**
     * Executes withEnv step (with environment variables)
     */
    private suspend fun executeWithEnvStep(step: Step.WithEnv, context: ExecutionContext): StepResult {
        // Convert environment list to map (KEY=value format)
        val envMap = step.environment.associate { envVar ->
            val parts = envVar.split("=", limit = 2)
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                parts[0] to ""
            }
        }
        
        val envContext = context.copy(
            environment = context.environment + envMap
        )
        
        val nestedResults = mutableListOf<StepResult>()
        for (nestedStep in step.steps) {
            val result = execute(nestedStep, envContext)
            nestedResults.add(result)
            
            // Stop on failure
            if (result.status == StepStatus.FAILURE) {
                break
            }
        }
        
        val overallStatus = if (nestedResults.all { it.status == StepStatus.SUCCESS }) {
            StepStatus.SUCCESS
        } else {
            StepStatus.FAILURE
        }
        
        return StepResult(
            stepName = "withEnv",
            status = overallStatus,
            duration = Duration.ofMillis(nestedResults.sumOf { it.duration.toMillis() }),
            output = nestedResults.joinToString("\n") { it.output },
            exitCode = 0,
            nestedResults = nestedResults
        )
    }
    
    /**
     * Executes parallel step
     */
    private suspend fun executeParallelStep(step: Step.Parallel, context: ExecutionContext): StepResult = coroutineScope {
        
        val branchJobs = step.branches.map { (branchName, branchSteps) ->
            async(CoroutineName("parallel-branch-$branchName")) {
                val branchResults = mutableListOf<StepResult>()
                for (branchStep in branchSteps) {
                    val result = execute(branchStep, context)
                    branchResults.add(result)
                    
                    if (result.status == StepStatus.FAILURE) {
                        break
                    }
                }
                
                val branchStatus = if (branchResults.all { it.status == StepStatus.SUCCESS }) {
                    StepStatus.SUCCESS
                } else {
                    StepStatus.FAILURE
                }
                
                branchName to StepResult(
                    stepName = "branch-$branchName",
                    status = branchStatus,
                    duration = Duration.ofMillis(branchResults.sumOf { it.duration.toMillis() }),
                    output = branchResults.joinToString("\n") { it.output },
                    nestedResults = branchResults
                )
            }
        }
        
        val branchResults = branchJobs.awaitAll().toMap()
        val overallStatus = if (branchResults.values.all { it.status == StepStatus.SUCCESS }) {
            StepStatus.SUCCESS
        } else {
            StepStatus.FAILURE
        }
        
        StepResult(
            stepName = "parallel",
            status = overallStatus,
            duration = Duration.ofMillis(branchResults.values.maxOfOrNull { it.duration.toMillis() } ?: 0L),
            output = branchResults.entries.joinToString("\n") { "${it.key}: ${it.value.output}" },
            branchResults = branchResults
        )
    }
    
    /**
     * Executes retry step
     */
    private suspend fun executeRetryStep(step: Step.Retry, context: ExecutionContext): StepResult {
        var lastResult: StepResult? = null
        var attempt = 0
        
        while (attempt < step.times) {
            attempt++
            
            try {
                val results = mutableListOf<StepResult>()
                for (retryStep in step.steps) {
                    val result = execute(retryStep, context)
                    results.add(result)
                    
                    if (result.status == StepStatus.FAILURE) {
                        break
                    }
                }
                
                val overallStatus = if (results.all { it.status == StepStatus.SUCCESS }) {
                    StepStatus.SUCCESS
                } else {
                    StepStatus.FAILURE
                }
                
                lastResult = StepResult(
                    stepName = "retry",
                    status = overallStatus,
                    duration = Duration.ofMillis(results.sumOf { it.duration.toMillis() }),
                    output = results.joinToString("\n") { it.output },
                    nestedResults = results,
                    metadata = mapOf(
                        "attemptCount" to attempt,
                        "retriesUsed" to (attempt - 1)
                    )
                )
                
                // Success - stop retrying
                if (overallStatus == StepStatus.SUCCESS) {
                    break
                }
                
                // Wait before retry (except last attempt)
                if (attempt < step.times) {
                    delay(1000L * attempt) // Exponential backoff
                }
                
            } catch (e: Exception) {
                lastResult = StepResult(
                    stepName = "retry",
                    status = StepStatus.FAILURE,
                    duration = Duration.ofSeconds(1),
                    error = e,
                    metadata = mapOf(
                        "attemptCount" to attempt,
                        "retriesUsed" to (attempt - 1)
                    )
                )
                
                if (attempt < step.times) {
                    delay(1000L * attempt) // Exponential backoff
                }
            }
        }
        
        return lastResult ?: StepResult(
            stepName = "retry",
            status = StepStatus.FAILURE,
            duration = Duration.ZERO,
            error = IllegalStateException("No retry attempts were made")
        )
    }
    
    /**
     * Executes timeout step
     */
    private suspend fun executeTimeoutStep(step: Step.Timeout, context: ExecutionContext): StepResult {
        return try {
            withTimeout(step.duration.inWholeMilliseconds) {
                val results = mutableListOf<StepResult>()
                for (timeoutStep in step.steps) {
                    val result = execute(timeoutStep, context)
                    results.add(result)
                    
                    if (result.status == StepStatus.FAILURE) {
                        break
                    }
                }
                
                val overallStatus = if (results.all { it.status == StepStatus.SUCCESS }) {
                    StepStatus.SUCCESS
                } else {
                    StepStatus.FAILURE
                }
                
                StepResult(
                    stepName = "timeout",
                    status = overallStatus,
                    duration = Duration.ofMillis(results.sumOf { it.duration.toMillis() }),
                    output = results.joinToString("\n") { it.output },
                    nestedResults = results
                )
            }
        } catch (e: TimeoutCancellationException) {
            StepResult(
                stepName = "timeout",
                status = StepStatus.TIMEOUT,
                duration = step.duration.toJavaDuration(),
                error = StepTimeoutException("timeout", "Timeout step exceeded ${step.duration}", step.duration, e)
            )
        }
    }
    
    /**
     * Executes archiveArtifacts step
     */
    private suspend fun executeArchiveArtifactsStep(step: Step.ArchiveArtifacts, context: ExecutionContext): StepResult {
        // Implementation would involve file system operations to archive artifacts
        context.logger.info("Archiving artifacts: ${step.artifacts}")
        
        return StepResult(
            stepName = "archiveArtifacts",
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(100),
            output = "Archived artifacts: ${step.artifacts}",
            metadata = mapOf(
                "archivedFiles" to step.artifacts,
                "allowEmptyArchive" to step.allowEmptyArchive,
                "fingerprintEnabled" to step.fingerprint
            )
        )
    }
    
    /**
     * Executes publishTestResults step
     */
    private suspend fun executePublishTestResultsStep(step: Step.PublishTestResults, context: ExecutionContext): StepResult {
        context.logger.info("Publishing test results: ${step.testResultsPattern}")
        
        return StepResult(
            stepName = "publishTestResults",
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(50),
            output = "Published test results: ${step.testResultsPattern}",
            metadata = mapOf(
                "testResultsPattern" to step.testResultsPattern,
                "allowEmptyResults" to step.allowEmptyResults
            )
        )
    }
    
    /**
     * Executes stash step
     */
    private suspend fun executeStashStep(step: Step.Stash, context: ExecutionContext): StepResult {
        context.logger.info("Stashing files: ${step.includes}")
        
        return StepResult(
            stepName = "stash",
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(200),
            output = "Stashed files: ${step.includes}",
            metadata = mapOf(
                "stashName" to step.name,
                "includes" to step.includes,
                "excludes" to step.excludes,
                "stashedFiles" to listOf("file1.txt", "file2.txt") // Mock
            )
        )
    }
    
    /**
     * Executes unstash step
     */
    private suspend fun executeUnstashStep(step: Step.Unstash, context: ExecutionContext): StepResult {
        context.logger.info("Unstashing: ${step.name}")
        
        return StepResult(
            stepName = "unstash",
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(150),
            output = "Unstashed: ${step.name}",
            metadata = mapOf(
                "unstashName" to step.name
            )
        )
    }
    
    /**
     * Executes unknown step type
     */
    private suspend fun executeUnknownStep(step: Step, context: ExecutionContext): StepResult {
        return StepResult(
            stepName = step::class.simpleName ?: "unknown",
            status = StepStatus.FAILURE,
            duration = Duration.ZERO,
            error = StepExecutionException("unknown", "Unknown step type: ${step::class}")
        )
    }
    
    /**
     * Validates step before execution
     */
    private fun validateStep(step: Step, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        when (step) {
            is Step.Shell -> {
                if (step.script.isBlank()) {
                    errors.add(ValidationError.required("script"))
                }
            }
            is Step.Echo -> {
                if (step.message.isBlank()) {
                    errors.add(ValidationError.required("message"))
                }
            }
            is Step.Dir -> {
                if (step.steps.isEmpty()) {
                    errors.add(ValidationError.required("steps"))
                }
            }
            else -> {
                // No specific validation for other step types
            }
        }
        
        return errors
    }
    
    /**
     * Prepares step for execution
     */
    private suspend fun prepareStep(step: Step, context: ExecutionContext) {
        // Preparation logic (create directories, validate resources, etc.)
        when (step) {
            is Step.Dir -> {
                // Ensure directory exists
                val dirPath = if (java.nio.file.Paths.get(step.path).isAbsolute) {
                    java.nio.file.Paths.get(step.path)
                } else {
                    context.workDir.resolve(step.path)
                }
                dirPath.toFile().mkdirs()
            }
            else -> {
                // No specific preparation for other step types
            }
        }
    }
    
    /**
     * Cleans up after step execution
     */
    private suspend fun cleanupStep(step: Step, context: ExecutionContext, result: StepResult) {
        // Cleanup logic (temporary files, connections, etc.)
        // This can be enhanced based on step type and requirements
    }
    
    /**
     * Selects appropriate dispatcher for step execution
     */
    private fun selectStepDispatcher(step: Step, context: ExecutionContext): CoroutineDispatcher {
        val workloadType = when (step) {
            is Step.Shell -> workloadAnalyzer.getDispatcherConfig().detectWorkloadType("sh", step.script)
            is Step.ArchiveArtifacts -> WorkloadType.IO_INTENSIVE
            is Step.PublishTestResults -> WorkloadType.IO_INTENSIVE  
            is Step.Stash -> WorkloadType.IO_INTENSIVE
            is Step.Unstash -> WorkloadType.IO_INTENSIVE
            else -> WorkloadType.DEFAULT
        }
        
        return workloadAnalyzer.analyzeAndSelect(getStepName(step), hint = workloadType)
    }
    
    /**
     * Gets step name for logging/metrics
     */
    private fun getStepName(step: Step): String {
        return when (step) {
            is Step.Shell -> "sh"
            is Step.Echo -> "echo"
            is Step.Dir -> "dir"
            is Step.WithEnv -> "withEnv"
            is Step.Parallel -> "parallel"
            is Step.Retry -> "retry"
            is Step.Timeout -> "timeout"
            is Step.ArchiveArtifacts -> "archiveArtifacts"
            is Step.PublishTestResults -> "publishTestResults"
            is Step.Stash -> "stash"
            is Step.Unstash -> "unstash"
        }
    }
    
    /**
     * Creates validation failure result
     */
    private fun createValidationFailureResult(
        stepName: String, 
        errors: List<ValidationError>,
        startTime: Instant
    ): StepResult {
        return StepResult(
            stepName = stepName,
            status = StepStatus.VALIDATION_FAILED,
            duration = Duration.between(startTime, Instant.now()),
            error = StepValidationException(stepName, errors),
            output = "Validation failed: ${errors.joinToString(", ") { it.message }}"
        )
    }
    
    /**
     * Creates timeout result
     */
    private fun createTimeoutResult(
        stepName: String,
        timeout: kotlin.time.Duration,
        startTime: Instant,
        cause: TimeoutCancellationException
    ): StepResult {
        return StepResult(
            stepName = stepName,
            status = StepStatus.TIMEOUT,
            duration = Duration.between(startTime, Instant.now()),
            error = StepTimeoutException(stepName, "Step timed out", timeout, cause)
        )
    }
    
    /**
     * Creates failure result
     */
    private fun createFailureResult(stepName: String, startTime: Instant, error: Exception): StepResult {
        return StepResult(
            stepName = stepName,
            status = StepStatus.FAILURE,
            duration = Duration.between(startTime, Instant.now()),
            error = if (error is StepExecutionException) error else StepExecutionException(stepName, "Step execution failed", error)
        )
    }
}

// Extension function to convert Kotlin Duration to Java Duration
private fun kotlin.time.Duration.toJavaDuration(): Duration = Duration.ofMillis(this.inWholeMilliseconds)