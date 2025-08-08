package dev.rubentxu.hodei.core.domain.model

/**
 * Pipeline Stage domain model
 * 
 * Represents a single stage within a pipeline, containing steps to execute,
 * environment configuration, agent specification, and conditional logic.
 * 
 * This is an immutable value object that ensures stage integrity and
 * follows Jenkins Declarative Pipeline semantics.
 */
public data class Stage(
    val name: String,
    val steps: List<Step>,
    val agent: Agent? = null,
    val environment: Map<String, String> = emptyMap(),
    val whenCondition: WhenCondition? = null,
    val post: List<PostAction> = emptyList()
) {
    
    init {
        // Validation rules
        require(name.isNotBlank()) { "Stage name cannot be blank" }
        require(steps.isNotEmpty()) { "Stage must contain at least one step" }
        require(name.matches(VALID_NAME_REGEX)) { 
            "Stage name '$name' contains invalid characters. Only alphanumeric, spaces, hyphens, and underscores are allowed." 
        }
    }
    
    /**
     * Returns true if this stage should execute based on its when condition
     * @param context Execution context for condition evaluation
     */
    public fun shouldExecute(context: Map<String, Any>): Boolean = 
        whenCondition?.evaluate(context) ?: true
    
    /**
     * Creates a new stage with additional environment variables
     * @param additionalEnv Environment variables to add/override
     */
    public fun withEnvironment(additionalEnv: Map<String, String>): Stage =
        copy(environment = environment + additionalEnv)
    
    /**
     * Creates a new stage with a different agent
     * @param newAgent Agent to use for this stage
     */
    public fun withAgent(newAgent: Agent?): Stage = copy(agent = newAgent)
    
    public companion object {
        private val VALID_NAME_REGEX = "^[a-zA-Z0-9\\s\\-_]+$".toRegex()
        
        /**
         * Creates a simple stage with name and steps
         * @param name Stage name (must be valid identifier)
         * @param steps List of steps to execute (must not be empty)
         */
        public fun create(name: String, steps: List<Step>): Stage = Stage(
            name = name,
            steps = steps
        )
        
        /**
         * Creates a simple stage with name and single step
         * @param name Stage name
         * @param step Single step to execute
         */
        public fun create(name: String, step: Step): Stage = Stage(
            name = name,
            steps = listOf(step)
        )
        
        /**
         * Creates a builder for complex stage configuration
         * @param name Stage name
         */
        public fun builder(name: String): StageBuilder = StageBuilder(name)
    }
}

/**
 * Builder for Stage instances following builder pattern
 */
public class StageBuilder(private val name: String) {
    private val steps: MutableList<Step> = mutableListOf()
    private var agent: Agent? = null
    private var environment: Map<String, String> = emptyMap()
    private var whenCondition: WhenCondition? = null
    private val post: MutableList<PostAction> = mutableListOf()
    
    /**
     * Adds a step to this stage
     * @param step Step to add
     */
    public fun addStep(step: Step): StageBuilder = apply {
        steps.add(step)
    }
    
    /**
     * Adds multiple steps to this stage
     * @param steps Steps to add
     */
    public fun addSteps(vararg steps: Step): StageBuilder = apply {
        this.steps.addAll(steps)
    }
    
    /**
     * Sets the agent for this stage
     * @param agent Agent configuration
     */
    public fun agent(agent: Agent?): StageBuilder = apply {
        this.agent = agent
    }
    
    /**
     * Sets environment variables for this stage
     * @param environment Environment variables
     */
    public fun environment(environment: Map<String, String>): StageBuilder = apply {
        this.environment = environment.toMap() // Defensive copy
    }
    
    /**
     * Sets the when condition for this stage
     * @param condition Condition for stage execution
     */
    public fun `when`(condition: WhenCondition?): StageBuilder = apply {
        this.whenCondition = condition
    }
    
    /**
     * Adds a post action to this stage
     * @param action Post action to add
     */
    public fun addPostAction(action: PostAction): StageBuilder = apply {
        post.add(action)
    }
    
    /**
     * Builds the stage instance
     * @return Immutable Stage instance
     */
    public fun build(): Stage = Stage(
        name = name,
        steps = steps.toList(),
        agent = agent,
        environment = environment,
        whenCondition = whenCondition,
        post = post.toList()
    )
}