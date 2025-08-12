package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.dsl.extensions.StepExtensionRegistry
import dev.rubentxu.hodei.core.execution.PipelineExecutionException
import kotlin.time.Duration

/**
 * DSL Builder for Steps construction
 * 
 * Provides comprehensive step creation API compatible with Jenkins Pipeline steps.
 * Supports all major step types including shell commands, control flow,
 * archiving, and Jenkins-specific operations.
 */
@PipelineDSL
public class StepsBuilder {
    private val steps: MutableList<Step> by lazy { mutableListOf() }
    
    // === BASIC STEPS ===
    
    /**
     * Execute shell command
     * @param script Command to execute
     * @param returnStdout Whether to capture stdout as return value
     * @param returnStatus Whether to capture exit code as return value
     * @param encoding Text encoding for output (default: UTF-8)
     */
    public fun sh(
        script: String,
        returnStdout: Boolean = false,
        returnStatus: Boolean = false,
        encoding: String = "UTF-8"
    ) {
        steps.add(Step.Shell(script, returnStdout, returnStatus, encoding))
    }
    
    /**
     * Print message to console
     * @param message Message to display
     */
    public fun echo(message: String) {
        steps.add(Step.echo(message))
    }
    
    /**
     * Execute steps in a specific directory
     * @param path Directory path (relative or absolute)
     * @param block Steps to execute in directory
     */
    public fun dir(path: String, block: StepsBuilder.() -> Unit) {
        val nestedBuilder = StepsBuilder()
        nestedBuilder.apply(block)
        steps.add(Step.Dir(path, nestedBuilder.build()))
    }
    
    /**
     * Execute steps with additional environment variables
     * @param env Environment variables in "KEY=value" format
     * @param block Steps to execute with environment
     */
    public fun withEnv(env: List<String>, block: StepsBuilder.() -> Unit) {
        val nestedBuilder = StepsBuilder()
        nestedBuilder.apply(block)
        steps.add(Step.WithEnv(env, nestedBuilder.build()))
    }
    
    // === CONTROL FLOW ===
    
    /**
     * Execute branches in parallel
     * @param block Parallel branches configuration
     */
    public fun parallel(block: ParallelBuilder.() -> Unit) {
        val builder = ParallelBuilder()
        builder.apply(block)
        steps.add(builder.build())
    }
    
    /**
     * Retry steps on failure
     * @param times Number of retry attempts
     * @param block Steps to retry
     */
    public fun retry(times: Int, block: StepsBuilder.() -> Unit) {
        val nestedBuilder = StepsBuilder()
        nestedBuilder.apply(block)
        steps.add(Step.Retry(times, nestedBuilder.build()))
    }
    
    /**
     * Execute steps with timeout
     * @param duration Maximum execution time
     * @param block Steps to execute with timeout
     */
    public fun timeout(duration: Duration, block: StepsBuilder.() -> Unit) {
        val nestedBuilder = StepsBuilder()
        nestedBuilder.apply(block)
        steps.add(Step.Timeout(duration, nestedBuilder.build()))
    }
    
    // === ARTIFACT MANAGEMENT ===
    
    /**
     * Archive artifacts for later use
     * @param artifacts File pattern for artifacts to archive
     * @param allowEmptyArchive Allow archiving when no files match
     * @param fingerprint Generate fingerprint for archived files
     */
    public fun archiveArtifacts(
        artifacts: String,
        allowEmptyArchive: Boolean = false,
        fingerprint: Boolean = false
    ) {
        steps.add(Step.ArchiveArtifacts(artifacts, allowEmptyArchive, fingerprint))
    }
    
    /**
     * Archive artifacts with detailed configuration
     * @param block Artifacts configuration block
     */
    public fun archiveArtifacts(block: ArchiveArtifactsBuilder.() -> Unit) {
        val builder = ArchiveArtifactsBuilder()
        builder.apply(block)
        steps.add(builder.build())
    }
    
    /**
     * Publish test results
     * @param testResults Test results file pattern
     * @param allowEmptyResults Allow when no test files found
     */
    public fun publishTestResults(
        testResults: String,
        allowEmptyResults: Boolean = false
    ) {
        steps.add(Step.PublishTestResults(testResults, allowEmptyResults))
    }
    
    /**
     * Publish test results with detailed configuration
     * @param block Test results configuration block
     */
    public fun publishTestResults(block: PublishTestResultsBuilder.() -> Unit) {
        val builder = PublishTestResultsBuilder()
        builder.apply(block)
        steps.add(builder.build())
    }
    
    /**
     * Stash files for later use across stages
     * @param name Stash identifier
     * @param includes File patterns to include
     * @param excludes File patterns to exclude
     */
    public fun stash(
        name: String,
        includes: String,
        excludes: String = ""
    ) {
        steps.add(Step.Stash(name, includes, excludes))
    }
    
    /**
     * Stash files with detailed configuration
     * @param block Stash configuration block
     */
    public fun stash(block: StashBuilder.() -> Unit) {
        val builder = StashBuilder()
        builder.apply(block)
        steps.add(builder.build())
    }
    
    /**
     * Unstash previously stashed files
     * @param name Stash identifier to restore
     */
    public fun unstash(name: String) {
        steps.add(Step.Unstash(name))
    }
    
    // === COMMUNICATION ===
    
    /**
     * Send email notification
     * @param to Recipients email addresses
     * @param subject Email subject
     * @param body Email content
     */
    public fun emailext(
        to: String,
        subject: String,
        body: String
    ) {
        // For now, create as echo step - will be enhanced later
        steps.add(Step.echo("Email to $to: $subject"))
    }
    
    /**
     * Builds the list of configured steps
     * @return Immutable list of steps
     */
    public fun build(): List<Step> = steps.toList()

    /**
     * Internal helper used by generated DSL functions to invoke a registered extension by [name].
     * If the extension is not available, a clear error is thrown to guide the user.
     */
    public fun invokeExtension(name: String, scope: Any) {
        val extension = StepExtensionRegistry.get(name)
            ?: throw PipelineExecutionException("$name extension not available. Did you forget to add the plugin dependency?")
        extension.execute(scope, this)
    }
}

/**
 * Builder for parallel execution branches
 */
@PipelineDSL
public class ParallelBuilder {
    private val branches: MutableMap<String, List<Step>> by lazy { mutableMapOf() }
    
    /**
     * Defines a parallel execution branch
     * @param name Branch name/identifier
     * @param block Steps to execute in this branch
     */
    public fun branch(name: String, block: StepsBuilder.() -> Unit) {
        val stepsBuilder = StepsBuilder()
        stepsBuilder.apply(block)
        branches[name] = stepsBuilder.build()
    }
    
    public fun build(): Step.Parallel = Step.Parallel(branches.toMap())
}

/**
 * Builder for archive artifacts configuration
 */
@PipelineDSL
public class ArchiveArtifactsBuilder {
    public var artifacts: String = ""
    public var allowEmptyArchive: Boolean = false
    public var fingerprint: Boolean = false
    
    public fun build(): Step.ArchiveArtifacts {
        require(artifacts.isNotEmpty()) { "Artifacts pattern cannot be empty" }
        return Step.ArchiveArtifacts(artifacts, allowEmptyArchive, fingerprint)
    }
}

/**
 * Builder for test results publishing configuration
 */
@PipelineDSL
public class PublishTestResultsBuilder {
    public var testResultsPattern: String = ""
    public var allowEmptyResults: Boolean = false
    
    public fun build(): Step.PublishTestResults {
        require(testResultsPattern.isNotEmpty()) { "Test results pattern cannot be empty" }
        return Step.PublishTestResults(testResultsPattern, allowEmptyResults)
    }
}

/**
 * Builder for stash configuration
 */
@PipelineDSL
public class StashBuilder {
    public var name: String = ""
    public var includes: String = ""
    public var excludes: String = ""
    
    public fun build(): Step.Stash {
        require(name.isNotEmpty()) { "Stash name cannot be empty" }
        require(includes.isNotEmpty()) { "Stash includes pattern cannot be empty" }
        return Step.Stash(name, includes, excludes)
    }
}