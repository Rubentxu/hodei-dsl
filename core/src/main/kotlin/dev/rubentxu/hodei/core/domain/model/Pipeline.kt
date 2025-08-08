package dev.rubentxu.hodei.core.domain.model

import java.util.*

/**
 * Core Pipeline domain model
 * 
 * Represents a complete CI/CD pipeline with stages, configuration,
 * and execution context following Jenkins Declarative Pipeline structure.
 * 
 * This is an immutable value object that represents the pipeline configuration
 * and state at a specific point in time.
 */
public data class Pipeline internal constructor(
    val id: String,
    val stages: List<Stage>,
    val globalEnvironment: Map<String, String>,
    val status: PipelineStatus,
    val agent: Agent?,
    val metadata: Map<String, Any>
) {
    
    init {
        // Validation rules
        require(id.isNotBlank()) { "Pipeline ID cannot be blank" }
        require(stages.distinctBy { it.name }.size == stages.size) { 
            "Stage names must be unique within a pipeline" 
        }
    }
    
    /**
     * Creates a new pipeline instance with updated status
     * Following immutable pattern for state changes
     */
    public fun withStatus(newStatus: PipelineStatus): Pipeline = copy(status = newStatus)
    
    /**
     * Creates a new pipeline instance with additional metadata
     */
    public fun withMetadata(key: String, value: Any): Pipeline = 
        copy(metadata = metadata + (key to value))
    
    public companion object {
        /**
         * Creates a new pipeline builder for fluent configuration
         */
        public fun builder(): PipelineBuilder = PipelineBuilder()
        
        /**
         * Creates a simple pipeline with basic configuration
         * Useful for testing and simple use cases
         */
        public fun simple(
            id: String = UUID.randomUUID().toString(),
            stages: List<Stage> = emptyList()
        ): Pipeline = Pipeline(
            id = id,
            stages = stages,
            globalEnvironment = emptyMap(),
            status = PipelineStatus.PENDING,
            agent = null,
            metadata = emptyMap()
        )
    }
}

/**
 * Pipeline status enumeration
 */
public enum class PipelineStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILURE,
    CANCELLED,
    PAUSED
}

/**
 * Builder for Pipeline instances following builder pattern
 * 
 * Provides fluent API for constructing Pipeline instances with validation
 * and sensible defaults.
 */
public class PipelineBuilder {
    private var id: String = UUID.randomUUID().toString()
    private val stages: MutableList<Stage> = mutableListOf()
    private var globalEnvironment: Map<String, String> = emptyMap()
    private var status: PipelineStatus = PipelineStatus.PENDING
    private var agent: Agent? = null
    private val metadata: MutableMap<String, Any> = mutableMapOf()
    
    /**
     * Sets the pipeline ID
     * @param id Pipeline identifier, must not be blank
     */
    public fun id(id: String): PipelineBuilder = apply {
        require(id.isNotBlank()) { "Pipeline ID cannot be blank" }
        this.id = id
    }
    
    /**
     * Adds a stage to the pipeline
     * @param stage Stage to add, name must be unique
     */
    public fun addStage(stage: Stage): PipelineBuilder = apply {
        require(stages.none { it.name == stage.name }) {
            "Stage with name '${stage.name}' already exists"
        }
        stages.add(stage)
    }
    
    /**
     * Adds multiple stages to the pipeline
     * @param stages Stages to add, names must be unique
     */
    public fun addStages(vararg stages: Stage): PipelineBuilder = apply {
        stages.forEach { addStage(it) }
    }
    
    /**
     * Sets global environment variables
     * @param environment Environment variables map
     */
    public fun globalEnvironment(environment: Map<String, String>): PipelineBuilder = apply {
        this.globalEnvironment = environment.toMap() // Defensive copy
    }
    
    /**
     * Adds a global environment variable
     * @param key Variable name
     * @param value Variable value
     */
    public fun addEnvironment(key: String, value: String): PipelineBuilder = apply {
        require(key.isNotBlank()) { "Environment variable name cannot be blank" }
        this.globalEnvironment = this.globalEnvironment + (key to value)
    }
    
    /**
     * Sets the pipeline status
     * @param status Pipeline status
     */
    public fun status(status: PipelineStatus): PipelineBuilder = apply {
        this.status = status
    }
    
    /**
     * Sets the global agent for the pipeline
     * @param agent Agent configuration
     */
    public fun agent(agent: Agent?): PipelineBuilder = apply {
        this.agent = agent
    }
    
    /**
     * Adds metadata to the pipeline
     * @param key Metadata key
     * @param value Metadata value
     */
    public fun metadata(key: String, value: Any): PipelineBuilder = apply {
        require(key.isNotBlank()) { "Metadata key cannot be blank" }
        metadata[key] = value
    }
    
    /**
     * Builds the pipeline instance
     * @return Immutable Pipeline instance
     * @throws IllegalArgumentException if validation fails
     */
    public fun build(): Pipeline = Pipeline(
        id = id,
        stages = stages.toList(), // Defensive copy
        globalEnvironment = globalEnvironment,
        status = status,
        agent = agent,
        metadata = metadata.toMap() // Defensive copy
    )
}