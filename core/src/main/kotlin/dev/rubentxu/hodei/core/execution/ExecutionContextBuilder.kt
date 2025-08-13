package dev.rubentxu.hodei.core.execution

import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Builder for ExecutionContext instances
 * 
 * Provides fluent API for building ExecutionContext with validation
 * and sensible defaults. Supports partial configuration and environment merging.
 */
public class ExecutionContextBuilder {
    private var workDir: Path = Paths.get(System.getProperty("user.dir"))
    private var environment: MutableMap<String, String> = mutableMapOf()
    private var mergeSystemEnv: Boolean = false
    private var executionId: String = "execution-${UUID.randomUUID()}"
    private var buildId: String = "build-${System.currentTimeMillis()}"
    private var workspace: WorkspaceInfo? = null
    private var jobInfo: JobInfo? = null
    private var logger: PipelineLogger? = null
    private var launcher: CommandLauncher? = null
    private var metrics: PipelineMetrics? = null
    private var artifactDir: Path? = null
    private var stepExecutor: StepExecutor? = null
    
    /**
     * Sets the working directory
     * @param workDir Working directory path
     */
    public fun workDir(workDir: Path): ExecutionContextBuilder = apply {
        this.workDir = workDir
    }
    
    /**
     * Sets the environment variables
     * @param environment Environment variables map
     */
    public fun environment(environment: Map<String, String>): ExecutionContextBuilder = apply {
        this.environment.clear()
        this.environment.putAll(environment)
    }
    
    /**
     * Adds an environment variable
     * @param key Variable name
     * @param value Variable value
     */
    public fun addEnvironment(key: String, value: String): ExecutionContextBuilder = apply {
        this.environment[key] = value
    }
    
    /**
     * Configures whether to merge system environment variables
     * @param merge true to merge system environment
     */
    public fun mergeSystemEnvironment(merge: Boolean): ExecutionContextBuilder = apply {
        this.mergeSystemEnv = merge
    }
    
    /**
     * Sets the execution ID
     * @param executionId Unique execution identifier
     */
    public fun executionId(executionId: String): ExecutionContextBuilder = apply {
        require(executionId.isNotBlank()) { "Execution ID cannot be blank" }
        this.executionId = executionId
    }
    
    /**
     * Sets the build ID
     * @param buildId Build identifier
     */
    public fun buildId(buildId: String): ExecutionContextBuilder = apply {
        require(buildId.isNotBlank()) { "Build ID cannot be blank" }
        this.buildId = buildId
    }
    
    /**
     * Sets the workspace information
     * @param workspace Workspace configuration
     */
    public fun workspace(workspace: WorkspaceInfo): ExecutionContextBuilder = apply {
        this.workspace = workspace
    }
    
    /**
     * Sets the job information
     * @param jobInfo Job metadata
     */
    public fun jobInfo(jobInfo: JobInfo): ExecutionContextBuilder = apply {
        this.jobInfo = jobInfo
    }
    
    /**
     * Sets the pipeline logger
     * @param logger Logger implementation
     */
    public fun logger(logger: PipelineLogger): ExecutionContextBuilder = apply {
        this.logger = logger
    }
    
    /**
     * Sets the command launcher
     * @param launcher Command launcher implementation
     */
    public fun launcher(launcher: CommandLauncher): ExecutionContextBuilder = apply {
        this.launcher = launcher
    }
    
    /**
     * Sets the pipeline metrics
     * @param metrics Metrics instance
     */
    public fun metrics(metrics: PipelineMetrics): ExecutionContextBuilder = apply {
        this.metrics = metrics
    }
    
    /**
     * Sets the artifact directory
     * @param artifactDir Directory for storing artifacts
     */
    public fun artifactDir(artifactDir: Path): ExecutionContextBuilder = apply {
        this.artifactDir = artifactDir
    }
    
    /**
     * Sets the step executor
     * @param stepExecutor Step executor for nested step execution
     */
    public fun stepExecutor(stepExecutor: StepExecutor): ExecutionContextBuilder = apply {
        this.stepExecutor = stepExecutor
    }
    
    /**
     * Builds the ExecutionContext instance
     * @return Configured ExecutionContext
     */
    public fun build(): ExecutionContext {
        // Prepare final environment
        val finalEnvironment = if (mergeSystemEnv) {
            System.getenv().toMutableMap().apply {
                putAll(environment)
            }
        } else {
            environment.toMap()
        }
        
        // Use defaults for unset components
        val finalWorkspace = workspace ?: WorkspaceInfo.default()
        val finalJobInfo = jobInfo ?: JobInfo.default()
        val finalLogger = logger ?: DefaultPipelineLogger()
        val finalLauncher = launcher ?: LocalCommandLauncher()
        val finalMetrics = metrics ?: PipelineMetrics.start()
        val finalArtifactDir = artifactDir ?: workDir.resolve("artifacts")
        val finalStepExecutor = stepExecutor ?: StepExecutor()
        
        return DefaultExecutionContext(
            workDir = workDir,
            _environment = finalEnvironment,
            logger = finalLogger,
            executionId = executionId,
            buildId = buildId,
            workspace = finalWorkspace,
            jobInfo = finalJobInfo,
            artifactDir = finalArtifactDir,
            launcher = finalLauncher,
            metrics = finalMetrics,
            stepExecutor = finalStepExecutor
        )
    }
}

/**
 * Default implementation of ExecutionContext
 * 
 * Provides immutable execution context with thread-safe operations
 * and comprehensive validation.
 */
public data class DefaultExecutionContext(
    override val workDir: Path,
    private val _environment: Map<String, String>,
    override val logger: PipelineLogger,
    override val executionId: String,
    override val buildId: String,
    override val workspace: WorkspaceInfo,
    override val jobInfo: JobInfo,
    override val artifactDir: Path,
    override val launcher: CommandLauncher,
    override val metrics: PipelineMetrics,
    override val stepExecutor: StepExecutor
) : ExecutionContext {
    
    // Immutable view of environment
    override val environment: Map<String, String> = Collections.unmodifiableMap(_environment)
    
    override fun copy(
        workDir: Path,
        environment: Map<String, String>,
        launcher: CommandLauncher
    ): ExecutionContext = copy(
        workDir = workDir,
        _environment = environment,
        launcher = launcher
    )
    
    override fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // Validate execution ID
        if (executionId.isBlank()) {
            errors.add(ValidationError.required("executionId"))
        }
        
        // Validate build ID
        if (buildId.isBlank()) {
            errors.add(ValidationError.required("buildId"))
        }
        
        // Validate working directory
        if (!workDir.isAbsolute) {
            errors.add(ValidationError.invalidValue("workDir", workDir, listOf("absolute path")))
        }
        
        // Validate working directory exists (warning only)
        if (!workDir.toFile().exists()) {
            errors.add(ValidationError.fileSystemError("workDir", workDir.toString(), "directory does not exist"))
        }
        
        // Validate artifact directory is absolute
        if (!artifactDir.isAbsolute) {
            errors.add(ValidationError.invalidValue("artifactDir", artifactDir, listOf("absolute path")))
        }
        
        return errors
    }
}