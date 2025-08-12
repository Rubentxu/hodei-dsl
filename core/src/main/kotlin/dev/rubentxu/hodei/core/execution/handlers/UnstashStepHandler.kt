package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import dev.rubentxu.hodei.core.execution.StashStorage
import dev.rubentxu.hodei.core.execution.FileSystemStashStorage
import java.time.Duration
import java.time.Instant

/**
 * Handler for Unstash steps
 * 
 * Implements the Strategy Pattern for unstashing files,
 * following Single Responsibility Principle.
 */
public class UnstashStepHandler(
    private val stashStorage: StashStorage = FileSystemStashStorage(
        java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).resolve("hodei-stash")
    )
) : AbstractStepHandler<Step.Unstash>() {
    
    override fun validate(step: Step.Unstash, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.name.isBlank()) {
            errors.add(ValidationError.required("name"))
        }
        
        return errors
    }
    
    override suspend fun execute(step: Step.Unstash, context: ExecutionContext): StepResult {
        context.logger.info("Unstashing: ${step.name}")
        val startTime = Instant.now()
        
        return try {
            val unstashResult = stashStorage.unstash(
                stashName = step.name,
                workspaceRoot = context.workspace.rootDir
            )
            
            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime)
            
            StepResult(
                stepName = getStepName(step),
                status = StepStatus.SUCCESS,
                duration = duration,
                output = "Unstashed: ${step.name}",
                metadata = mapOf(
                    "unstashName" to unstashResult.stashName,
                    "restoredFiles" to unstashResult.restoredFiles,
                    "fileCount" to unstashResult.fileCount
                )
            )
        } catch (e: Exception) {
            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime)
            
            StepResult(
                stepName = getStepName(step),
                status = StepStatus.FAILURE,
                duration = duration,
                output = "Failed to unstash: ${e.message}",
                error = e
            )
        }
    }
    
    override fun getStepName(step: Step.Unstash): String = "unstash"
}