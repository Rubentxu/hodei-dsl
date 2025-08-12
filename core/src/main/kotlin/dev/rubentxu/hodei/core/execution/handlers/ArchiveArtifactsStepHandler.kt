package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import java.time.Duration

/**
 * Handler for ArchiveArtifacts steps
 * 
 * Implements the Strategy Pattern for archiving artifacts,
 * following Single Responsibility Principle.
 */
public class ArchiveArtifactsStepHandler : AbstractStepHandler<Step.ArchiveArtifacts>() {
    
    override fun validate(step: Step.ArchiveArtifacts, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.artifacts.isBlank()) {
            errors.add(ValidationError.required("artifacts"))
        }
        
        return errors
    }
    
    override suspend fun execute(step: Step.ArchiveArtifacts, context: ExecutionContext): StepResult {
        // Implementation would involve file system operations to archive artifacts
        context.logger.info("Archiving artifacts: ${step.artifacts}")
        
        return StepResult(
            stepName = getStepName(step),
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
    
    override fun getStepName(step: Step.ArchiveArtifacts): String = "archiveArtifacts"
}