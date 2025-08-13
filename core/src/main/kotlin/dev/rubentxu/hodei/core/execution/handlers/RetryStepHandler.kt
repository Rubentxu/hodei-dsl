package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Handler for retry operations
 * 
 * Executes nested steps with retry logic, attempting execution up to the specified
 * number of times with exponential backoff between attempts.
 */
class RetryStepHandler : AbstractStepHandler<Step.Retry>() {
    
    companion object {
        private const val DEFAULT_BACKOFF_BASE_MS = 1000L
        private const val MAX_BACKOFF_MS = 30000L
    }
    
    override fun validate(step: Step.Retry, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.times <= 0) {
            errors.add(ValidationError(
                field = "times",
                message = "Retry times must be greater than 0",
                code = "INVALID_RETRY_TIMES"
            ))
        }
        
        if (step.times > 10) {
            errors.add(ValidationError(
                field = "times",
                message = "Retry times cannot exceed 10 to prevent infinite loops",
                code = "TOO_MANY_RETRIES"
            ))
        }
        
        if (step.steps.isEmpty()) {
            errors.add(ValidationError(
                field = "steps",
                message = "Retry step must contain at least one nested step",
                code = "EMPTY_STEPS"
            ))
        }
        
        return errors
    }
    
    override suspend fun prepare(step: Step.Retry, context: ExecutionContext) {
        context.logger.info("Preparing retry execution (max attempts: ${step.times})")
        context.logger.info("Will execute ${step.steps.size} steps with retry logic")
    }
    
    override suspend fun execute(step: Step.Retry, context: ExecutionContext): StepResult {
        context.logger.info("Executing steps with retry logic (max attempts: ${step.times})")
        
        var lastError: Throwable? = null
        val attemptResults = mutableListOf<AttemptResult>()
        
        for (attempt in 1..step.times) {
            try {
                context.logger.info("Retry attempt $attempt of ${step.times}")
                
                val attemptStartTime = System.currentTimeMillis()
                val stepResults = mutableListOf<StepResult>()
                var attemptSuccessful = true
                
                // Execute all nested steps
                for ((stepIndex, nestedStep) in step.steps.withIndex()) {
                    val stepResult = context.stepExecutor.execute(nestedStep, context)
                    stepResults.add(stepResult)
                    
                    if (!stepResult.isSuccessful) {
                        context.logger.warn("Step ${stepIndex + 1} failed in retry attempt $attempt: ${stepResult.error}")
                        lastError = stepResult.error
                        attemptSuccessful = false
                        break
                    }
                }
                
                val attemptTime = System.currentTimeMillis() - attemptStartTime
                
                if (attemptSuccessful) {
                    context.logger.info("Retry succeeded on attempt $attempt")
                    
                    attemptResults.add(AttemptResult(attempt, true, attemptTime, stepResults.size))
                    
                    return StepResult(
                        stepName = getStepName(step),
                        status = StepStatus.SUCCESS,
                        duration = Duration.ofMillis(attemptTime),
                        output = "Retry succeeded on attempt $attempt of ${step.times} (${stepResults.size} steps executed)",
                        metadata = mapOf(
                            "maxAttempts" to step.times,
                            "successfulAttempt" to attempt,
                            "stepsExecuted" to stepResults.size,
                            "attemptResults" to attemptResults,
                            "totalExecutionTime" to attemptResults.sumOf { it.executionTime }
                        )
                    )
                }
                
                attemptResults.add(AttemptResult(attempt, false, attemptTime, stepResults.size))
                
                // Wait before next attempt (except for the last attempt)
                if (attempt < step.times) {
                    val backoffTime = calculateBackoffTime(attempt)
                    context.logger.info("Waiting ${backoffTime}ms before retry attempt ${attempt + 1}")
                    delay(backoffTime)
                }
                
            } catch (e: Exception) {
                val attemptTime = 50L
                attemptResults.add(AttemptResult(attempt, false, attemptTime, 0))
                
                context.logger.error("Exception in retry attempt $attempt: ${e.message}")
                lastError = e
                
                // Wait before next attempt (except for the last attempt)
                if (attempt < step.times) {
                    val backoffTime = calculateBackoffTime(attempt)
                    context.logger.info("Waiting ${backoffTime}ms before retry attempt ${attempt + 1}")
                    delay(backoffTime)
                }
            }
        }
        
        // All attempts failed
        context.logger.error("All ${step.times} retry attempts failed")
        
        return StepResult(
            stepName = getStepName(step),
            status = StepStatus.FAILURE,
            duration = Duration.ofMillis(attemptResults.sumOf { it.executionTime }),
            output = "",
            error = lastError ?: RuntimeException("All retry attempts failed"),
            metadata = mapOf(
                "maxAttempts" to step.times,
                "attemptResults" to attemptResults,
                "totalExecutionTime" to attemptResults.sumOf { it.executionTime }
            )
        )
    }
    
    override suspend fun cleanup(step: Step.Retry, context: ExecutionContext, result: StepResult) {
        context.logger.debug("Retry step cleanup completed")
    }
    
    override fun getStepName(step: Step.Retry): String = "retry"
    
    /**
     * Calculates backoff time with exponential backoff strategy
     */
    private fun calculateBackoffTime(attempt: Int): Long {
        val backoffTime = DEFAULT_BACKOFF_BASE_MS * (1L shl (attempt - 1))
        return minOf(backoffTime, MAX_BACKOFF_MS)
    }
    
    /**
     * Represents the result of a single retry attempt
     */
    private data class AttemptResult(
        val attemptNumber: Int,
        val success: Boolean,
        val executionTime: Long,
        val stepsExecuted: Int
    )
}