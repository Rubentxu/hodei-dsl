package dev.rubentxu.hodei.core.domain.model

import kotlin.time.Duration

/**
 * Pipeline Step sealed class hierarchy
 * 
 * Represents different types of steps that can be executed within stages,
 * following Jenkins step API compatibility.
 */
public sealed class Step {
    public abstract val type: StepType
    public abstract val timeout: Duration?
    
    /**
     * Shell execution step
     */
    public data class Shell(
        val script: String,
        val returnStdout: Boolean = false,
        val returnStatus: Boolean = false,
        val encoding: String = "UTF-8",
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.SHELL
    }
    
    /**
     * Echo/print step
     */
    public data class Echo(
        val message: String,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.ECHO
    }
    
    /**
     * Directory change step
     */
    public data class Dir(
        val path: String,
        val steps: List<Step>,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.DIR
    }
    
    /**
     * Environment wrapper step
     */
    public data class WithEnv(
        val environment: List<String>,
        val steps: List<Step>,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.WITH_ENV
    }
    
    public companion object {
        /**
         * Creates a shell execution step
         */
        public fun shell(
            script: String,
            returnStdout: Boolean = false,
            returnStatus: Boolean = false,
            encoding: String = "UTF-8"
        ): Shell = Shell(
            script = script,
            returnStdout = returnStdout,
            returnStatus = returnStatus,
            encoding = encoding
        )
        
        /**
         * Creates an echo step
         */
        public fun echo(message: String): Echo = Echo(message)
        
        /**
         * Creates a directory step
         */
        public fun dir(path: String, steps: List<Step>): Dir = Dir(path, steps)
        
        /**
         * Creates an environment wrapper step
         */
        public fun withEnv(environment: List<String>, steps: List<Step>): WithEnv = 
            WithEnv(environment, steps)
    }
    
    /**
     * Adds timeout to any step
     */
    public fun withTimeout(timeout: Duration): Step = when (this) {
        is Shell -> copy(timeout = timeout)
        is Echo -> copy(timeout = timeout)
        is Dir -> copy(timeout = timeout)
        is WithEnv -> copy(timeout = timeout)
    }
}

/**
 * Step type enumeration
 */
public enum class StepType {
    SHELL,
    ECHO,
    DIR,
    WITH_ENV,
    PARALLEL,
    RETRY,
    TIMEOUT,
    CHECKOUT,
    ARCHIVE_ARTIFACTS,
    JUNIT,
    BUILD,
    PUBLISH_ARTIFACTS,
    EMAIL,
    INPUT,
    CUSTOM
}