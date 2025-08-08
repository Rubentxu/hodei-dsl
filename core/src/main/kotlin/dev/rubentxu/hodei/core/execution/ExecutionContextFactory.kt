package dev.rubentxu.hodei.core.execution

import java.nio.file.Path

/**
 * Factory interface for creating ExecutionContext instances
 * 
 * Provides dependency injection port for ExecutionContext creation,
 * reducing connascence of construction and enabling testability.
 * Following hexagonal architecture principles for external dependencies.
 */
public interface ExecutionContextFactory {
    
    /**
     * Creates a default ExecutionContext for development/testing
     * @return Configured ExecutionContext with sensible defaults
     */
    public fun createDefault(): ExecutionContext
    
    /**
     * Creates an ExecutionContext for a specific environment
     * @param environment Target environment (development, staging, production)
     * @return Environment-specific ExecutionContext
     */
    public fun createForEnvironment(environment: String): ExecutionContext
    
    /**
     * Creates an ExecutionContext with custom configuration
     * @param config ExecutionContext configuration
     * @return Configured ExecutionContext
     */
    public fun create(config: ExecutionContextConfig): ExecutionContext
}

/**
 * Configuration data class for ExecutionContext creation
 * 
 * Encapsulates all configuration parameters to reduce connascence
 * of position and improve type safety.
 */
public data class ExecutionContextConfig(
    val workDir: Path? = null,
    val environment: Map<String, String> = emptyMap(),
    val mergeSystemEnvironment: Boolean = false,
    val executionId: String? = null,
    val buildId: String? = null,
    val workspace: WorkspaceInfo? = null,
    val jobInfo: JobInfo? = null,
    val logger: PipelineLogger? = null,
    val launcher: CommandLauncher? = null,
    val metrics: PipelineMetrics? = null,
    val artifactDir: Path? = null
)

/**
 * Default implementation of ExecutionContextFactory
 * 
 * Provides standard factory implementation with dependency injection
 * capabilities and environment-specific configuration.
 */
public class DefaultExecutionContextFactory(
    private val loggerFactory: PipelineLoggerFactory = DefaultPipelineLoggerFactory(),
    private val launcherFactory: CommandLauncherFactory = DefaultCommandLauncherFactory(),
    private val workspaceFactory: WorkspaceInfoFactory = DefaultWorkspaceInfoFactory(),
    private val jobInfoFactory: JobInfoFactory = DefaultJobInfoFactory()
) : ExecutionContextFactory {
    
    override fun createDefault(): ExecutionContext {
        return ExecutionContextBuilder()
            .logger(loggerFactory.createDefault())
            .launcher(launcherFactory.createLocal())
            .workspace(workspaceFactory.createDefault())
            .jobInfo(jobInfoFactory.createDefault())
            .build()
    }
    
    override fun createForEnvironment(environment: String): ExecutionContext {
        return ExecutionContextBuilder()
            .environment(mapOf("ENVIRONMENT" to environment))
            .executionId("${environment.take(3).lowercase()}-${java.util.UUID.randomUUID()}")
            .logger(loggerFactory.createForEnvironment(environment))
            .launcher(launcherFactory.createForEnvironment(environment))
            .workspace(workspaceFactory.createForEnvironment(environment))
            .jobInfo(jobInfoFactory.createForEnvironment(environment))
            .build()
    }
    
    override fun create(config: ExecutionContextConfig): ExecutionContext {
        val builder = ExecutionContextBuilder()
        
        config.workDir?.let { builder.workDir(it) }
        if (config.environment.isNotEmpty()) {
            builder.environment(config.environment)
        }
        builder.mergeSystemEnvironment(config.mergeSystemEnvironment)
        config.executionId?.let { builder.executionId(it) }
        config.buildId?.let { builder.buildId(it) }
        
        val logger = config.logger ?: loggerFactory.createDefault()
        val launcher = config.launcher ?: launcherFactory.createLocal()
        val workspace = config.workspace ?: workspaceFactory.createDefault()
        val jobInfo = config.jobInfo ?: jobInfoFactory.createDefault()
        val metrics = config.metrics ?: PipelineMetrics.start()
        
        builder.logger(logger)
            .launcher(launcher)
            .workspace(workspace)
            .jobInfo(jobInfo)
            .metrics(metrics)
        
        config.artifactDir?.let { builder.artifactDir(it) }
        
        return builder.build()
    }
}