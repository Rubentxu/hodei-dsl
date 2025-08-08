package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.domain.model.*

/**
 * DSL Builder for Stage construction
 * 
 * Provides fluent API for building Stage instances with Jenkins
 * compatibility including agent overrides, environment variables,
 * when conditions, and post actions.
 */
@PipelineDSL
public class StageBuilder(private val name: String) {
    private val steps: MutableList<Step> = mutableListOf()
    private var agent: Agent? = null
    private var environment: Map<String, String> = emptyMap()
    private var whenCondition: WhenCondition? = null
    private val postActions: MutableList<PostAction> = mutableListOf()
    
    /**
     * Configures agent for this stage (overrides global agent)
     * @param block Agent configuration block
     */
    public fun agent(block: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder()
        builder.apply(block)
        this.agent = builder.build()
    }
    
    /**
     * Configures environment variables for this stage
     * @param block Environment configuration block
     */
    public fun environment(block: EnvironmentBuilder.() -> Unit) {
        val builder = EnvironmentBuilder()
        builder.apply(block)
        this.environment = builder.build()
    }
    
    /**
     * Configures when conditions for conditional stage execution
     * @param block When conditions configuration block
     */
    public fun `when`(block: WhenBuilder.() -> Unit) {
        val builder = WhenBuilder()
        builder.apply(block)
        this.whenCondition = builder.build()
    }
    
    /**
     * Configures steps to execute in this stage
     * @param block Steps configuration block
     */
    public fun steps(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.apply(block)
        this.steps.addAll(builder.build())
    }
    
    /**
     * Configures post-execution actions for this stage
     * @param block Post actions configuration block
     */
    public fun post(block: PostBuilder.() -> Unit) {
        val builder = PostBuilder()
        builder.apply(block)
        this.postActions.addAll(builder.build())
    }
    
    /**
     * Shorthand method to add a single shell step directly
     * Convenience method for simple stages with one command
     * @param script Shell script to execute
     */
    public fun sh(script: String) {
        steps.add(Step.shell(script))
    }
    
    /**
     * Builds the final Stage instance
     * @return Immutable Stage instance
     */
    internal fun build(): Stage {
        require(steps.isNotEmpty()) { "Stage must contain at least one step" }
        
        return Stage(
            name = name,
            steps = steps.toList(),
            agent = agent,
            environment = environment,
            whenCondition = whenCondition,
            post = postActions.toList()
        )
    }
}