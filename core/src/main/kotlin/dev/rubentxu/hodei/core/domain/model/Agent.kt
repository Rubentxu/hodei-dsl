package dev.rubentxu.hodei.core.domain.model

/**
 * Agent configuration sealed class hierarchy
 * 
 * Defines where and how pipeline stages are executed,
 * supporting various execution environments like local, Docker, Kubernetes.
 */
public sealed class Agent {
    public abstract val type: AgentType
    
    /**
     * Any available agent
     */
    public object Any : Agent() {
        override val type: AgentType = AgentType.ANY
    }
    
    /**
     * No specific agent (stages must define their own)
     */
    public object None : Agent() {
        override val type: AgentType = AgentType.NONE
    }
    
    /**
     * Agent by label
     */
    public data class Label(
        val label: String
    ) : Agent() {
        override val type: AgentType = AgentType.LABEL
    }
    
    /**
     * Docker container agent
     */
    public data class Docker(
        val image: String,
        val args: String = "",
        val volumes: Map<String, String> = emptyMap(),
        val environment: Map<String, String> = emptyMap()
    ) : Agent() {
        override val type: AgentType = AgentType.DOCKER
    }
    
    /**
     * Kubernetes pod agent
     */
    public data class Kubernetes(
        val yaml: String,
        val namespace: String = "default"
    ) : Agent() {
        override val type: AgentType = AgentType.KUBERNETES
    }
    
    public companion object {
        /**
         * Creates any agent
         */
        public fun any(): Any = Any
        
        /**
         * Creates none agent
         */
        public fun none(): None = None
        
        /**
         * Creates label agent
         */
        public fun label(label: String): Label = Label(label)
        
        /**
         * Creates docker agent
         */
        public fun docker(
            image: String,
            args: String = "",
            volumes: Map<String, String> = emptyMap(),
            environment: Map<String, String> = emptyMap()
        ): Docker = Docker(image, args, volumes, environment)
        
        /**
         * Creates kubernetes agent
         */
        public fun kubernetes(yaml: String, namespace: String = "default"): Kubernetes = 
            Kubernetes(yaml, namespace)
    }
}

/**
 * Agent type enumeration
 */
public enum class AgentType {
    ANY,
    NONE,
    LABEL,
    DOCKER,
    KUBERNETES
}