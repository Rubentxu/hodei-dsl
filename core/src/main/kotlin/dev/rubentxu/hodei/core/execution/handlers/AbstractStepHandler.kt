package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract base class for step handlers providing common functionality
 * 
 * Implements Template Method pattern to provide consistent step execution
 * lifecycle while allowing specialized implementations for each step type.
 */
public abstract class AbstractStepHandler<T : Step> : StepHandler<T> {
    
    /**
     * Template method for complete step execution with lifecycle management
     * 
     * This method orchestrates the full step execution lifecycle:
     * validation → preparation → execution → cleanup
     */
    public suspend fun executeWithLifecycle(
        step: T, 
        context: ExecutionContext,
        config: StepExecutorConfig = StepExecutorConfig()
    ): StepResult {
        val stepStartTime = Instant.now()
        val stepName = getStepName(step)
        
        try {
            // Validate step
            val validationErrors = validate(step, context)
            if (validationErrors.isNotEmpty()) {
                return createValidationFailureResult(stepName, validationErrors, stepStartTime)
            }
            
            // Prepare step
            prepare(step, context)
            
            // Execute with timeout if specified
            val result = if (step.timeout != null) {
                val timeout = step.timeout!!
                withTimeout(timeout.inWholeMilliseconds) {
                    execute(step, context)
                }
            } else if (config.defaultTimeout.isPositive()) {
                withTimeout(config.defaultTimeout.inWholeMilliseconds) {
                    execute(step, context)
                }
            } else {
                execute(step, context)
            }
            
            // Cleanup
            cleanup(step, context, result)
            
            // Enhance result with metadata
            return enhanceResult(result, stepStartTime, context)
            
        } catch (e: TimeoutCancellationException) {
            return createTimeoutResult(stepName, step.timeout ?: config.defaultTimeout, stepStartTime, e)
        } catch (e: Exception) {
            return createFailureResult(stepName, stepStartTime, e)
        }
    }
    
    /**
     * Default validation implementation - can be overridden by specific handlers
     */
    override fun validate(step: T, context: ExecutionContext): List<ValidationError> {
        return emptyList() // Default: no validation errors
    }
    
    /**
     * Default preparation implementation - can be overridden by specific handlers
     */
    override suspend fun prepare(step: T, context: ExecutionContext) {
        // Default: no preparation needed
    }
    
    /**
     * Default cleanup implementation - can be overridden by specific handlers
     */
    override suspend fun cleanup(step: T, context: ExecutionContext, result: StepResult) {
        // Default: no cleanup needed
    }
    
    /**
     * Gets the display name for this step type - must be implemented by subclasses
     */
    protected abstract fun getStepName(step: T): String
    
    /**
     * Enhances the step result with execution metadata
     */
    private suspend fun enhanceResult(result: StepResult, startTime: Instant, context: ExecutionContext): StepResult {
        return result.copy(
            duration = Duration.between(startTime, Instant.now()),
            metadata = result.metadata + mapOf(
                "dispatcher" to (currentCoroutineContext()[CoroutineDispatcher]?.toString() ?: "unknown"),
                "thread" to Thread.currentThread().name,
                "launcher" to (context.launcher::class.simpleName ?: "unknown")
            )
        )
    }
    
    /**
     * Creates a validation failure result
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
     * Creates a timeout result
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
     * Creates a failure result
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