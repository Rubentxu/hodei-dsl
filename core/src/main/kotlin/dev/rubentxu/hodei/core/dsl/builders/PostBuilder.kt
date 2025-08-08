package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.domain.model.*

/**
 * DSL Builder for Post actions configuration
 * 
 * Provides fluent API for configuring post-execution actions that run
 * after stage or pipeline completion based on execution result.
 */
@PipelineDSL
public class PostBuilder {
    private val postActions: MutableList<PostAction> = mutableListOf()
    
    /**
     * Actions that always execute regardless of result
     * @param block Steps configuration block
     */
    public fun always(block: StepsBuilder.() -> Unit) {
        val stepsBuilder = StepsBuilder()
        stepsBuilder.apply(block)
        postActions.add(PostAction.always(stepsBuilder.build()))
    }
    
    /**
     * Actions that execute only on successful completion
     * @param block Steps configuration block
     */
    public fun success(block: StepsBuilder.() -> Unit) {
        val stepsBuilder = StepsBuilder()
        stepsBuilder.apply(block)
        postActions.add(PostAction.success(stepsBuilder.build()))
    }
    
    /**
     * Actions that execute only on failure
     * @param block Steps configuration block
     */
    public fun failure(block: StepsBuilder.() -> Unit) {
        val stepsBuilder = StepsBuilder()
        stepsBuilder.apply(block)
        postActions.add(PostAction.failure(stepsBuilder.build()))
    }
    
    /**
     * Actions that execute only when build is unstable
     * @param block Steps configuration block
     */
    public fun unstable(block: StepsBuilder.() -> Unit) {
        val stepsBuilder = StepsBuilder()
        stepsBuilder.apply(block)
        postActions.add(PostAction.unstable(stepsBuilder.build()))
    }
    
    /**
     * Actions that execute when build result changes from previous
     * @param block Steps configuration block
     */
    public fun changed(block: StepsBuilder.() -> Unit) {
        val stepsBuilder = StepsBuilder()
        stepsBuilder.apply(block)
        postActions.add(PostAction.changed(stepsBuilder.build()))
    }
    
    internal fun build(): List<PostAction> = postActions.toList()
}