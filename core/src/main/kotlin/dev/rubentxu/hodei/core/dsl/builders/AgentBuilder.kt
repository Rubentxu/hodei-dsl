package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.domain.model.Agent

/**
 * DSL Builder for Agent configuration
 * 
 * Provides fluent API for configuring where and how pipeline steps execute.
 * Supports Jenkins agent types including any, none, label, Docker, and Kubernetes.
 */
@PipelineDSL
public class AgentBuilder {
    private var agent: Agent? = null
    
    /**
     * Use any available agent
     */
    public fun any() {
        this.agent = Agent.any()
    }
    
    /**
     * Use no agent (stages must define their own agents)
     */
    public fun none() {
        this.agent = Agent.none()
    }
    
    /**
     * Use agent with specific label
     * @param label Agent label or expression
     */
    public fun label(label: String) {
        this.agent = Agent.label(label)
    }
    
    /**
     * Use Docker container agent
     * @param block Docker configuration block
     */
    public fun docker(block: DockerAgentBuilder.() -> Unit) {
        val builder = DockerAgentBuilder()
        builder.apply(block)
        this.agent = builder.build()
    }
    
    /**
     * Use Kubernetes pod agent
     * @param block Kubernetes configuration block
     */
    public fun kubernetes(block: KubernetesAgentBuilder.() -> Unit) {
        val builder = KubernetesAgentBuilder()
        builder.apply(block)
        this.agent = builder.build()
    }
    
    public fun build(): Agent = agent ?: Agent.any()
}

/**
 * Builder for Docker agent configuration
 */
@PipelineDSL
public class DockerAgentBuilder {
    public var image: String = ""
    public var args: String = ""
    
    public fun build(): Agent.Docker {
        require(image.isNotEmpty()) { "Docker image cannot be empty" }
        return Agent.docker(image, args)
    }
}

/**
 * Builder for Kubernetes agent configuration
 */
@PipelineDSL
public class KubernetesAgentBuilder {
    public var yaml: String = ""
    
    public fun build(): Agent.Kubernetes {
        require(yaml.isNotEmpty()) { "Kubernetes YAML configuration cannot be empty" }
        return Agent.kubernetes(yaml)
    }
}