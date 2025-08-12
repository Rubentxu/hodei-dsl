package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import dev.rubentxu.hodei.core.execution.StepExecutionException
import java.time.Duration

/**
 * Handler for Shell steps
 * 
 * Implements the Strategy Pattern for shell step execution,
 * following Single Responsibility Principle.
 */
public class ShellStepHandler : AbstractStepHandler<Step.Shell>() {
    
    override fun validate(step: Step.Shell, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.script.isBlank()) {
            errors.add(ValidationError.required("script"))
        }
        
        return errors
    }
    
    override suspend fun execute(step: Step.Shell, context: ExecutionContext): StepResult {
        val result = context.launcher.execute(
            command = step.script,
            workingDir = context.workDir.toString(),
            environment = context.environment
        )
        
        return StepResult(
            stepName = getStepName(step),
            status = if (result.success) StepStatus.SUCCESS else StepStatus.FAILURE,
            duration = Duration.ofMillis(result.durationMs),
            output = result.stdout,
            errorOutput = result.stderr,
            exitCode = result.exitCode,
            error = if (!result.success) {
                StepExecutionException(getStepName(step), "Command failed with exit code ${result.exitCode}")
            } else null
        )
    }
    
    override fun getStepName(step: Step.Shell): String = "sh"
}