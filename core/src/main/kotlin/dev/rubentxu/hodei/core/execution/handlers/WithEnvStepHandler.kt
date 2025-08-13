package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import dev.rubentxu.hodei.core.execution.StepStatus
import dev.rubentxu.hodei.core.execution.ValidationError
import java.time.Duration

/**
 * Handler for environment variable operations
 * 
 * Executes steps within a modified environment context, adding or overriding
 * environment variables for the execution scope.
 */
class WithEnvStepHandler : AbstractStepHandler<Step.WithEnv>() {
    
    override fun validate(step: Step.WithEnv, context: ExecutionContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        if (step.environment.isEmpty()) {
            errors.add(ValidationError(
                field = "environment",
                message = "WithEnv step must specify at least one environment variable",
                code = "EMPTY_ENVIRONMENT"
            ))
        }
        
        if (step.steps.isEmpty()) {
            errors.add(ValidationError(
                field = "steps",
                message = "WithEnv step must contain at least one nested step",
                code = "EMPTY_STEPS"
            ))
        }
        
        // Validate environment variable format
        step.environment.forEach { envVar ->
            if (!isValidEnvironmentVariable(envVar)) {
                errors.add(ValidationError(
                    field = "environment",
                    message = "Invalid environment variable format: '$envVar'. Expected 'KEY=value'",
                    code = "INVALID_ENV_FORMAT"
                ))
            }
        }
        
        return errors
    }
    
    override suspend fun prepare(step: Step.WithEnv, context: ExecutionContext) {
        // Log the environment variables that will be set
        context.logger.info("Setting environment variables:")
        step.environment.forEach { envVar ->
            val (key, _) = parseEnvironmentVariable(envVar)
            context.logger.info("  $key=${getMaskedValue(key, envVar)}")
        }
    }
    
    override suspend fun execute(step: Step.WithEnv, context: ExecutionContext): StepResult {
        context.logger.info("Executing steps with modified environment")
        
        try {
            // Parse environment variables
            val envMap = step.environment.associate { envVar ->
                val (key, value) = parseEnvironmentVariable(envVar)
                key to value
            }
            
            // Create new context with merged environment
            val mergedEnvironment = context.environment.toMutableMap().apply {
                putAll(envMap)
            }
            
            val envContext = context.copy(environment = mergedEnvironment)
            
            // Execute all nested steps with the modified environment
            val stepResults = mutableListOf<StepResult>()
            
            for (nestedStep in step.steps) {
                val stepResult = context.stepExecutor.execute(nestedStep, envContext)
                stepResults.add(stepResult)
                
                // Stop execution if a step fails
                if (!stepResult.isSuccessful) {
                    context.logger.error("Step failed with modified environment: ${stepResult.error}")
                    return StepResult(
                        stepName = getStepName(step),
                        status = StepStatus.FAILURE,
                        duration = Duration.ofMillis(System.currentTimeMillis()),
                        output = "",
                        error = stepResult.error ?: RuntimeException("WithEnv step failed: nested step '${nestedStep.type}' failed")
                    )
                }
            }
            
            context.logger.info("Successfully executed ${stepResults.size} steps with modified environment")
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.SUCCESS,
                duration = Duration.ofMillis(50),
                output = "Executed ${stepResults.size} steps with modified environment",
                metadata = mapOf(
                    "environmentVariables" to envMap.keys,
                    "stepsExecuted" to stepResults.size
                )
            )
            
        } catch (e: Exception) {
            context.logger.error("Error executing steps with modified environment: ${e.message}")
            return StepResult(
                stepName = getStepName(step),
                status = StepStatus.FAILURE,
                duration = Duration.ofMillis(System.currentTimeMillis()),
                output = "",
                error = e
            )
        }
    }
    
    override suspend fun cleanup(step: Step.WithEnv, context: ExecutionContext, result: StepResult) {
        // Environment context is automatically restored since we used a copied context
        context.logger.debug("WithEnv step cleanup completed")
    }
    
    override fun getStepName(step: Step.WithEnv): String = "withEnv"
    
    /**
     * Validates that an environment variable string has the correct format
     */
    private fun isValidEnvironmentVariable(envVar: String): Boolean {
        return envVar.contains("=") && envVar.indexOf("=") > 0
    }
    
    /**
     * Parses an environment variable string into key and value
     */
    private fun parseEnvironmentVariable(envVar: String): Pair<String, String> {
        val equalIndex = envVar.indexOf("=")
        if (equalIndex <= 0) {
            throw IllegalArgumentException("Invalid environment variable format: '$envVar'")
        }
        
        val key = envVar.substring(0, equalIndex)
        val value = envVar.substring(equalIndex + 1)
        
        return key to value
    }
    
    /**
     * Masks sensitive environment variable values for logging
     */
    private fun getMaskedValue(key: String, envVar: String): String {
        val sensitiveKeys = setOf(
            "PASSWORD", "PASS", "SECRET", "TOKEN", "KEY", "CREDENTIAL",
            "API_KEY", "ACCESS_TOKEN", "PRIVATE_KEY", "AUTH_TOKEN"
        )
        
        return if (sensitiveKeys.any { key.uppercase().contains(it) }) {
            "***"
        } else {
            parseEnvironmentVariable(envVar).second
        }
    }
}