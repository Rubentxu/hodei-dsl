package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.ValidationError
import dev.rubentxu.hodei.core.execution.StepStatus
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Handler for directory change operations
 * 
 * Executes steps within a specific directory context, changing the working directory
 * for the execution scope and restoring it afterwards.
 */
class DirStepHandler : AbstractStepHandler<Step.Dir>() {
    
    override fun validate(step: Step.Dir, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.path.isBlank()) {
            errors.add(ValidationError(
                field = "path",
                message = "Directory path cannot be empty",
                code = "EMPTY_PATH"
            ))
        }
        
        if (step.steps.isEmpty()) {
            errors.add(ValidationError(
                field = "steps",
                message = "Dir step must contain at least one nested step",
                code = "EMPTY_STEPS"
            ))
        }
        
        // Validate that the target directory exists or can be created
        val targetPath = resolvePath(step.path, context.workDir)
        if (targetPath.parent?.exists() == false) {
            errors.add(ValidationError(
                field = "path",
                message = "Parent directory '${targetPath.parent}' does not exist",
                code = "PARENT_NOT_EXISTS"
            ))
        }
        
        return errors
    }
    
    override suspend fun prepare(step: Step.Dir, context: ExecutionContext) {
        val targetPath = resolvePath(step.path, context.workDir)
        
        // Create directory if it doesn't exist
        if (!targetPath.exists()) {
            context.logger.info("Creating directory: $targetPath")
            try {
                targetPath.toFile().mkdirs()
            } catch (e: Exception) {
                throw RuntimeException("Failed to create directory '$targetPath': ${e.message}", e)
            }
        }
        
        // Verify it's a directory
        if (!targetPath.isDirectory()) {
            throw RuntimeException("Path '$targetPath' exists but is not a directory")
        }
    }
    
    override suspend fun execute(step: Step.Dir, context: ExecutionContext): StepResult {
        val targetPath = resolvePath(step.path, context.workDir)
        val originalWorkDir = context.workDir
        
        context.logger.info("Executing steps in directory: $targetPath")
        
        // Create new context with updated working directory
        val dirContext = context.copy(workDir = targetPath)
        
        try {
            // Execute all nested steps in the directory context
            val stepResults = mutableListOf<StepResult>()
            
            for (nestedStep in step.steps) {
                val stepResult = context.stepExecutor.execute(nestedStep, dirContext)
                stepResults.add(stepResult)
                
                // Stop execution if a step fails
                if (!stepResult.isSuccessful) {
                    context.logger.error("Step failed in directory '$targetPath': ${stepResult.error}")
                    return StepResult(
                        stepName = getStepName(step),
                        status = StepStatus.FAILURE,
                        duration = Duration.ofMillis(System.currentTimeMillis()),
                        output = "",
                        error = stepResult.error ?: RuntimeException("Dir step failed: nested step '${nestedStep.type}' failed")
                    )
                }
            }
            
            context.logger.info("Successfully executed ${stepResults.size} steps in directory: $targetPath")
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.SUCCESS,
                duration = Duration.ofMillis(50),
                output = "Executed ${stepResults.size} steps in directory '$targetPath'",
                metadata = mapOf(
                    "directory" to targetPath.toString(),
                    "stepsExecuted" to stepResults.size,
                    "originalWorkDir" to originalWorkDir.toString()
                )
            )
            
        } catch (e: Exception) {
            context.logger.error("Error executing steps in directory '$targetPath': ${e.message}")
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.FAILURE,
                duration = Duration.ofMillis(System.currentTimeMillis()),
                output = "",
                error = e
            )
        }
    }
    
    override suspend fun cleanup(step: Step.Dir, context: ExecutionContext, result: StepResult) {
        // Directory context is automatically restored since we used a copied context
        // No explicit cleanup needed
        context.logger.debug("Dir step cleanup completed")
    }
    
    override fun getStepName(step: Step.Dir): String = "dir"
    
    /**
     * Resolves the target path relative to the current working directory
     */
    private fun resolvePath(path: String, workDir: Path): Path {
        return if (path.startsWith("/")) {
            // Absolute path
            Paths.get(path)
        } else {
            // Relative path - resolve against current working directory
            workDir.resolve(path)
        }
    }
}