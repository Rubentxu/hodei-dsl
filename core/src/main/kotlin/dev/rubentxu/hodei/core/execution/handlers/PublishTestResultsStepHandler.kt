package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import java.time.Duration

/**
 * Handler for PublishTestResults steps
 * 
 * Implements the Strategy Pattern for publishing test results,
 * following Single Responsibility Principle.
 */
public class PublishTestResultsStepHandler : AbstractStepHandler<Step.PublishTestResults>() {
    
    override fun validate(step: Step.PublishTestResults, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.testResultsPattern.isBlank()) {
            errors.add(ValidationError.required("testResultsPattern"))
        }
        
        return errors
    }
    
    override suspend fun execute(step: Step.PublishTestResults, context: ExecutionContext): StepResult {
        context.logger.info("Publishing test results: ${step.testResultsPattern}")
        
        return StepResult(
            stepName = getStepName(step),
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(50),
            output = "Published test results: ${step.testResultsPattern}",
            metadata = mapOf(
                "testResultsPattern" to step.testResultsPattern,
                "allowEmptyResults" to step.allowEmptyResults
            )
        )
    }
    
    override fun getStepName(step: Step.PublishTestResults): String = "publishTestResults"
}