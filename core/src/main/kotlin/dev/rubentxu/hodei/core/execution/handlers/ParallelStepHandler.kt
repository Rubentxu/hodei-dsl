package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import kotlinx.coroutines.*
import java.time.Duration

/**
 * Handler for parallel execution operations
 * 
 * Executes multiple branches of steps concurrently using structured concurrency.
 * All branches must complete successfully for the step to succeed.
 */
class ParallelStepHandler : AbstractStepHandler<Step.Parallel>() {
    
    override fun validate(step: Step.Parallel, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.branches.isEmpty()) {
            errors.add(ValidationError(
                field = "branches",
                message = "Parallel step must have at least one branch",
                code = "EMPTY_BRANCHES"
            ))
        }
        
        // Validate branch names
        step.branches.keys.forEach { branchName ->
            if (branchName.isBlank()) {
                errors.add(ValidationError(
                    field = "branchName",
                    message = "Branch name cannot be empty",
                    code = "EMPTY_BRANCH_NAME"
                ))
            }
        }
        
        // Validate that each branch has steps
        step.branches.forEach { (branchName, steps) ->
            if (steps.isEmpty()) {
                errors.add(ValidationError(
                    field = "branchSteps",
                    message = "Branch '$branchName' must contain at least one step",
                    code = "EMPTY_BRANCH_STEPS"
                ))
            }
        }
        
        return errors
    }
    
    override suspend fun prepare(step: Step.Parallel, context: ExecutionContext) {
        context.logger.info("Preparing parallel execution for ${step.branches.size} branches:")
        step.branches.keys.forEach { branchName ->
            context.logger.info("  - Branch: $branchName (${step.branches[branchName]?.size} steps)")
        }
    }
    
    override suspend fun execute(step: Step.Parallel, context: ExecutionContext): StepResult = coroutineScope {
        context.logger.info("Executing ${step.branches.size} branches in parallel")
        
        try {
            // Execute all branches concurrently
            val branchJobs = step.branches.map { (branchName, steps) ->
                async(CoroutineName("parallel-branch-$branchName")) {
                    executeBranch(branchName, steps, context)
                }
            }
            
            // Wait for all branches to complete
            val branchResults = branchJobs.awaitAll()
            
            // Check if all branches succeeded
            val failedBranches = branchResults.filter { !it.success }
            
            if (failedBranches.isNotEmpty()) {
                val failedBranchNames = failedBranches.map { it.branchName }
                context.logger.error("Parallel execution failed. Failed branches: ${failedBranchNames.joinToString(", ")}")
                
                return@coroutineScope StepResult(
                    stepName = getStepName(step),
                    status = StepStatus.FAILURE,
                    duration = Duration.ofMillis(100),
                    output = "",
                    error = RuntimeException("Failed branches: ${failedBranchNames.joinToString(", ")}")
                )
            }
            
            context.logger.info("Successfully executed all ${branchResults.size} branches in parallel")
            
            val totalSteps = branchResults.sumOf { it.stepsExecuted }
            val executionTimes = branchResults.associate { it.branchName to it.executionTime }
            
            return@coroutineScope StepResult(
                stepName = getStepName(step),
                status = StepStatus.SUCCESS,
                duration = Duration.ofMillis(100),
                output = "Executed ${branchResults.size} branches in parallel (total: $totalSteps steps)",
                metadata = mapOf(
                    "branchCount" to branchResults.size,
                    "totalStepsExecuted" to totalSteps,
                    "branchExecutionTimes" to executionTimes,
                    "successfulBranches" to branchResults.map { it.branchName }
                )
            )
            
        } catch (e: CancellationException) {
            context.logger.warn("Parallel execution was cancelled")
            throw e
        } catch (e: Exception) {
            context.logger.error("Error during parallel execution: ${e.message}")
            return@coroutineScope StepResult(
                stepName = getStepName(step),
                status = StepStatus.FAILURE,
                duration = Duration.ofMillis(50),
                output = "",
                error = e
            )
        }
    }
    
    override suspend fun cleanup(step: Step.Parallel, context: ExecutionContext, result: StepResult) {
        context.logger.debug("Parallel step cleanup completed")
    }
    
    override fun getStepName(step: Step.Parallel): String = "parallel"
    
    /**
     * Executes a single branch of steps
     */
    private suspend fun executeBranch(
        branchName: String,
        steps: List<Step>,
        context: ExecutionContext
    ): BranchResult {
        val startTime = System.currentTimeMillis()
        
        try {
            context.logger.info("Starting branch: $branchName")
            
            for ((index, step) in steps.withIndex()) {
                context.logger.debug("Executing step ${index + 1}/${steps.size} in branch '$branchName': ${step.type}")
                
                val stepResult = context.stepExecutor.execute(step, context)
                
                if (!stepResult.isSuccessful) {
                    val executionTime = System.currentTimeMillis() - startTime
                    context.logger.error("Step failed in branch '$branchName': ${stepResult.error}")
                    
                    return BranchResult(
                        branchName = branchName,
                        success = false,
                        stepsExecuted = index + 1,
                        executionTime = executionTime,
                        error = stepResult.error
                    )
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            context.logger.info("Branch '$branchName' completed successfully (${steps.size} steps, ${executionTime}ms)")
            
            return BranchResult(
                branchName = branchName,
                success = true,
                stepsExecuted = steps.size,
                executionTime = executionTime
            )
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            context.logger.error("Branch '$branchName' failed with exception: ${e.message}")
            
            return BranchResult(
                branchName = branchName,
                success = false,
                stepsExecuted = 0,
                executionTime = executionTime,
                error = e
            )
        }
    }
    
    /**
     * Represents the result of executing a single branch
     */
    private data class BranchResult(
        val branchName: String,
        val success: Boolean,
        val stepsExecuted: Int,
        val executionTime: Long,
        val error: Throwable? = null
    )
}