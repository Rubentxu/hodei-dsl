package dev.rubentxu.hodei.core.dsl.config

import dev.rubentxu.hodei.core.domain.model.Agent
import dev.rubentxu.hodei.core.domain.model.PostAction
import dev.rubentxu.hodei.core.domain.model.Stage
import java.util.*

/**
 * Immutable configuration objects for Pipeline DSL
 * 
 * These replace mutable var properties in builders with immutable
 * configuration objects that can be safely passed around and composed.
 */

/**
 * Immutable pipeline configuration
 */
public data class PipelineConfig(
    val id: String = UUID.randomUUID().toString(),
    val stages: List<Stage> = emptyList(),
    val globalEnvironment: Map<String, String> = emptyMap(),
    val agent: Agent? = null,
    val postActions: List<PostAction> = emptyList()
) {
    /**
     * Add a stage to the configuration
     */
    public fun withStage(stage: Stage): PipelineConfig = copy(
        stages = stages + stage
    )
    
    /**
     * Add multiple stages to the configuration
     */
    public fun withStages(vararg newStages: Stage): PipelineConfig = copy(
        stages = stages + newStages
    )
    
    /**
     * Set global environment variables
     */
    public fun withEnvironment(environment: Map<String, String>): PipelineConfig = copy(
        globalEnvironment = environment
    )
    
    /**
     * Add a single environment variable
     */
    public fun withEnvironmentVar(name: String, value: String): PipelineConfig = copy(
        globalEnvironment = globalEnvironment + (name to value)
    )
    
    /**
     * Set the global agent
     */
    public fun withAgent(agent: Agent): PipelineConfig = copy(
        agent = agent
    )
    
    /**
     * Add a post action
     */
    public fun withPostAction(postAction: PostAction): PipelineConfig = copy(
        postActions = postActions + postAction
    )
}

/**
 * Immutable stage configuration
 */
public data class StageConfig(
    val name: String,
    val steps: List<dev.rubentxu.hodei.core.domain.model.Step> = emptyList(),
    val agent: Agent? = null,
    val environment: Map<String, String> = emptyMap(),
    val whenCondition: dev.rubentxu.hodei.core.domain.model.WhenCondition? = null,
    val postActions: List<PostAction> = emptyList()
) {
    /**
     * Add a step to the configuration
     */
    public fun withStep(step: dev.rubentxu.hodei.core.domain.model.Step): StageConfig = copy(
        steps = steps + step
    )
    
    /**
     * Add multiple steps to the configuration
     */
    public fun withSteps(vararg newSteps: dev.rubentxu.hodei.core.domain.model.Step): StageConfig = copy(
        steps = steps + newSteps
    )
    
    /**
     * Set environment variables for this stage
     */
    public fun withEnvironment(environment: Map<String, String>): StageConfig = copy(
        environment = environment
    )
    
    /**
     * Add a single environment variable
     */
    public fun withEnvironmentVar(name: String, value: String): StageConfig = copy(
        environment = environment + (name to value)
    )
    
    /**
     * Set the agent for this stage
     */
    public fun withAgent(agent: Agent): StageConfig = copy(
        agent = agent
    )
    
    /**
     * Set the when condition
     */
    public fun withWhenCondition(condition: dev.rubentxu.hodei.core.domain.model.WhenCondition): StageConfig = copy(
        whenCondition = condition
    )
    
    /**
     * Add a post action
     */
    public fun withPostAction(postAction: PostAction): StageConfig = copy(
        postActions = postActions + postAction
    )
}

/**
 * Immutable environment configuration
 */
public data class EnvironmentConfig(
    val variables: Map<String, String> = emptyMap()
) {
    /**
     * Add a variable to the configuration
     */
    public fun withVariable(name: String, value: String): EnvironmentConfig = copy(
        variables = variables + (name to value)
    )
    
    /**
     * Add multiple variables to the configuration
     */
    public fun withVariables(newVariables: Map<String, String>): EnvironmentConfig = copy(
        variables = variables + newVariables
    )
    
    /**
     * Remove a variable from the configuration
     */
    public fun withoutVariable(name: String): EnvironmentConfig = copy(
        variables = variables - name
    )
}