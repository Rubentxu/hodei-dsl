package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import java.time.Duration

/**
 * Handler for Echo steps
 * 
 * Implements the Strategy Pattern for echo step execution,
 * following Single Responsibility Principle.
 */
public class EchoStepHandler : AbstractStepHandler<Step.Echo>() {
    
    override fun validate(step: Step.Echo, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.message.isBlank()) {
            errors.add(ValidationError.required("message"))
        }
        
        return errors
    }
    
    override suspend fun execute(step: Step.Echo, context: ExecutionContext): StepResult {
        // Log the message to the context logger
        context.logger.info(step.message)
        
        return StepResult(
            stepName = getStepName(step),
            status = StepStatus.SUCCESS,
            duration = Duration.ofMillis(1), // Minimal duration for echo
            output = step.message,
            exitCode = 0
        )
    }
    
    override fun getStepName(step: Step.Echo): String = "echo"
}