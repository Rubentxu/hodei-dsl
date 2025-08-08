package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.domain.model.*
import java.util.*

/**
 * DSL Builder for Pipeline construction
 * 
 * Provides a type-safe fluent API for building Pipeline instances with
 * Jenkins Declarative Pipeline compatibility. Uses @PipelineDSL marker
 * for scope safety.
 */
@PipelineDSL
public class PipelineBuilder {
    private var id: String = UUID.randomUUID().toString()
    private val stages: MutableList<Stage> = mutableListOf()
    private var globalEnvironment: Map<String, String> = emptyMap()
    private var agent: Agent? = null
    private val postActions: MutableList<PostAction> = mutableListOf()
    
    /**
     * Configures global agent for the entire pipeline
     * @param block Agent configuration block
     */
    public fun agent(block: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder()
        builder.apply(block)
        this.agent = builder.build()
    }
    
    /**
     * Configures global environment variables
     * @param block Environment configuration block
     */
    public fun environment(block: EnvironmentBuilder.() -> Unit) {
        val builder = EnvironmentBuilder()
        builder.apply(block)
        this.globalEnvironment = builder.build()
    }
    
    /**
     * Adds a stage to the pipeline
     * @param name Stage name (must be unique)
     * @param block Stage configuration block
     */
    public fun stage(name: String, block: StageBuilder.() -> Unit) {
        require(stages.none { it.name == name }) {
            "Stage with name '$name' already exists"
        }
        
        val stageBuilder = StageBuilder(name)
        stageBuilder.apply(block)
        stages.add(stageBuilder.build())
    }
    
    /**
     * Configures post-execution actions
     * @param block Post actions configuration block
     */
    public fun post(block: PostBuilder.() -> Unit) {
        val builder = PostBuilder()
        builder.apply(block)
        this.postActions.addAll(builder.build())
    }
    
    /**
     * Builds the final Pipeline instance
     * @return Immutable Pipeline instance
     */
    internal fun build(): Pipeline {
        require(stages.isNotEmpty()) { "Pipeline must contain at least one stage" }
        
        return Pipeline.builder()
            .id(id)
            .globalEnvironment(globalEnvironment)
            .agent(agent)
            .apply { stages.forEach { addStage(it) } }
            .build()
    }
}