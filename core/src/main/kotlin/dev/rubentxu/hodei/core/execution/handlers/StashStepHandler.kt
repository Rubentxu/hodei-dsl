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
 * Handler for Stash steps
 * 
 * Implements the Strategy Pattern for stashing files,
 * following Single Responsibility Principle.
 */
public class StashStepHandler(
    private val stashStorage: StashStorage = FileSystemStashStorage(
        java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")).resolve("hodei-stash")
    )
) : AbstractStepHandler<Step.Stash>() {
    
    override fun validate(step: Step.Stash, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.name.isBlank()) {
            errors.add(ValidationError.required("name"))
        }
        
        if (step.includes.isBlank()) {
            errors.add(ValidationError.required("includes"))
        }
        
        return errors
    }
    
    override suspend fun execute(step: Step.Stash, context: ExecutionContext): StepResult {
        context.logger.info("Stashing files: ${step.includes}")
        val startTime = Instant.now()
        
        return try {
            val stashResult = stashStorage.stash(
                stashName = step.name,
                workspaceRoot = context.workspace.rootDir,
                includes = step.includes,
                excludes = step.excludes
            )
            
            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime)
            
            StepResult(
                stepName = getStepName(step),
                status = StepStatus.SUCCESS,
                duration = duration,
                output = "Stashed files: ${step.includes}",
                metadata = mapOf(
                    "stashName" to stashResult.stashName,
                    "includes" to step.includes,
                    "excludes" to step.excludes,
                    "stashedFiles" to stashResult.stashedFiles,
                    "stashLocation" to stashResult.stashLocation,
                    "timestamp" to stashResult.timestamp,
                    "fileCount" to stashResult.fileCount,
                    "totalSize" to stashResult.totalSize,
                    "checksums" to stashResult.checksums
                )
            )
        } catch (e: Exception) {
            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime)
            
            StepResult(
                stepName = getStepName(step),
                status = StepStatus.FAILURE,
                duration = duration,
                output = "Failed to stash files: ${e.message}",
                error = e
            )
        }
    }
    
    override fun getStepName(step: Step.Stash): String = "stash"
}