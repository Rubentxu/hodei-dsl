package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.ValidationError

/**
 * Strategy Pattern interface for handling step execution
 * 
 * Each step type implements this interface to provide specialized execution logic
 * following the Single Responsibility Principle.
 * 
 * @param T The specific step type this handler processes
 */
public interface StepHandler<T : Step> {
    
    /**
     * Validates a step before execution
     * 
     * @param step The step to validate
     * @param context Execution context
     * @return List of validation errors, empty if valid
     */
    public fun validate(step: T, context: ExecutionContext): List<ValidationError>
    
    /**
     * Prepares the step for execution (setup resources, directories, etc.)
     * 
     * @param step The step to prepare
     * @param context Execution context
     */
    public suspend fun prepare(step: T, context: ExecutionContext)
    
    /**
     * Executes the step and returns the result
     * 
     * @param step The step to execute
     * @param context Execution context
     * @return Step execution result
     */
    public suspend fun execute(step: T, context: ExecutionContext): StepResult
    
    /**
     * Cleans up after step execution (temporary files, resources, etc.)
     * 
     * @param step The executed step
     * @param context Execution context
     * @param result The execution result
     */
    public suspend fun cleanup(step: T, context: ExecutionContext, result: StepResult)
}