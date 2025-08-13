package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import kotlinx.coroutines.*
import java.time.Duration

/**
 * Handler for timeout operations
 * 
 * Executes nested steps within a specified timeout duration, cancelling execution
 * if the timeout is exceeded.
 */
class TimeoutStepHandler : AbstractStepHandler<Step.Timeout>() {
    
    override fun validate(step: Step.Timeout, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.duration.isNegative() || step.duration.isInfinite()) {
            errors.add(ValidationError(
                field = "duration",
                message = "Timeout duration must be positive and finite",
                code = "INVALID_DURATION"
            ))
        }
        
        if (step.duration.inWholeMilliseconds == 0L) {
            errors.add(ValidationError(
                field = "duration",
                message = "Timeout duration cannot be zero",
                code = "ZERO_DURATION"
            ))
        }
        
        if (step.duration.inWholeHours > 24) {
            errors.add(ValidationError(
                field = "duration",
                message = "Timeout duration cannot exceed 24 hours",
                code = "DURATION_TOO_LONG"
            ))
        }
        
        if (step.steps.isEmpty()) {
            errors.add(ValidationError(
                field = "steps",
                message = "Timeout step must contain at least one nested step",
                code = "EMPTY_STEPS"
            ))
        }
        
        return errors
    }
    
    override suspend fun prepare(step: Step.Timeout, context: ExecutionContext) {
        context.logger.info("Preparing timeout execution (timeout: ${step.duration})")
        context.logger.info("Will execute ${step.steps.size} steps with timeout protection")
    }
    
    override suspend fun execute(step: Step.Timeout, context: ExecutionContext): StepResult {
        val timeoutMs = step.duration.inWholeMilliseconds
        
        context.logger.info("Executing steps with timeout: ${step.duration}")
        
        try {
            // Execute steps with timeout
            val result = withTimeout(timeoutMs) {
                executeStepsWithTracking(step.steps, context)
            }
            
            context.logger.info("Successfully executed all steps within timeout (${step.duration})")
            
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.SUCCESS,
                duration = Duration.ofMillis(result.executionTime),
                output = "Executed ${step.steps.size} steps within timeout ${step.duration}",
                metadata = mapOf(
                    "timeoutDuration" to step.duration.toString(),
                    "stepsExecuted" to result.stepsExecuted,
                    "executionTime" to "${result.executionTime}ms",
                    "timedOut" to false
                )
            )
            
        } catch (e: TimeoutCancellationException) {
            context.logger.error("Steps execution timed out after ${step.duration}")
            
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.TIMEOUT,
                duration = Duration.ofMillis(timeoutMs),
                output = "",
                error = e,
                metadata = mapOf(
                    "timeoutDuration" to step.duration.toString(),
                    "timedOut" to true,
                    "executionTime" to "${timeoutMs}ms"
                )
            )
        } catch (e: CancellationException) {
            context.logger.warn("Steps execution was cancelled")
            throw e
        } catch (e: Exception) {
            context.logger.error("Error during timeout execution: ${e.message}")
            
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.FAILURE,
                duration = Duration.ofMillis(50),
                output = "",
                error = e,
                metadata = mapOf(
                    "timeoutDuration" to step.duration.toString(),
                    "timedOut" to false
                )
            )
        }
    }
    
    override suspend fun cleanup(step: Step.Timeout, context: ExecutionContext, result: StepResult) {
        context.logger.debug("Timeout step cleanup completed")
    }
    
    override fun getStepName(step: Step.Timeout): String = "timeout"
    
    /**
     * Executes steps while tracking execution metrics
     */
    private suspend fun executeStepsWithTracking(
        steps: List<Step>,
        context: ExecutionContext
    ): ExecutionResult {
        val startTime = System.currentTimeMillis()
        
        for ((index, step) in steps.withIndex()) {
            context.logger.debug("Executing step ${index + 1}/${steps.size} with timeout protection: ${step.type}")
            
            val stepResult = context.stepExecutor.execute(step, context)
            
            if (!stepResult.isSuccessful) {
                val executionTime = System.currentTimeMillis() - startTime
                
                throw RuntimeException(
                    "Step ${index + 1} failed during timeout execution: ${stepResult.error?.message}",
                    stepResult.error
                )
            }
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return ExecutionResult(
            stepsExecuted = steps.size,
            executionTime = executionTime
        )
    }
    
    /**
     * Represents the result of executing steps with tracking
     */
    private data class ExecutionResult(
        val stepsExecuted: Int,
        val executionTime: Long
    )
}