package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL

/**
 * DSL Builder for Environment configuration
 * 
 * Provides fluent API for setting environment variables in pipelines and stages.
 * Supports standard environment variables with validation.
 */
@PipelineDSL
public class EnvironmentBuilder {
    private val environment: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Set an environment variable
     * @param name Variable name (must be valid identifier)
     * @param value Variable value
     */
    public fun set(name: String, value: String) {
        require(name.isNotBlank()) { "Environment variable name cannot be blank" }
        require(name.matches(VALID_ENV_VAR_REGEX)) { 
            "Invalid environment variable name: '$name'. Must start with letter/underscore and contain only alphanumeric characters and underscores." 
        }
        environment[name] = value
    }
    
    /**
     * Set multiple environment variables from a map
     * @param vars Map of variable name to value
     */
    public fun setAll(vars: Map<String, String>) {
        vars.forEach { (name, value) -> set(name, value) }
    }
    
    internal fun build(): Map<String, String> = environment.toMap()
    
    private companion object {
        private val VALID_ENV_VAR_REGEX = "^[a-zA-Z_][a-zA-Z0-9_]*$".toRegex()
    }
}