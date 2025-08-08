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
    
    /**
     * Parallel execution step
     */
    public data class Parallel(
        val branches: Map<String, List<Step>>,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.PARALLEL
    }
    
    /**
     * Retry wrapper step
     */
    public data class Retry(
        val times: Int,
        val steps: List<Step>,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.RETRY
    }
    
    /**
     * Timeout wrapper step
     */
    public data class Timeout(
        val duration: Duration,
        val steps: List<Step>,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.TIMEOUT
    }
    
    /**
     * Archive artifacts step
     */
    public data class ArchiveArtifacts(
        val artifacts: String,
        val allowEmptyArchive: Boolean = false,
        val fingerprint: Boolean = false,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.ARCHIVE_ARTIFACTS
    }
    
    /**
     * Publish test results step
     */
    public data class PublishTestResults(
        val testResultsPattern: String,
        val allowEmptyResults: Boolean = false,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.JUNIT
    }
    
    /**
     * Stash step
     */
    public data class Stash(
        val name: String,
        val includes: String,
        val excludes: String = "",
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.CUSTOM
    }
    
    /**
     * Unstash step
     */
    public data class Unstash(
        val name: String,
        override val timeout: Duration? = null
    ) : Step() {
        override val type: StepType = StepType.CUSTOM
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
        is Parallel -> copy(timeout = timeout)
        is Retry -> copy(timeout = timeout)
        is Timeout -> copy(timeout = timeout)
        is ArchiveArtifacts -> copy(timeout = timeout)
        is PublishTestResults -> copy(timeout = timeout)
        is Stash -> copy(timeout = timeout)
        is Unstash -> copy(timeout = timeout)
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