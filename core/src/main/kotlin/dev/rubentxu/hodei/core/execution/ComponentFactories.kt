package dev.rubentxu.hodei.core.execution

/**
 * Factory interface for creating PipelineLogger instances
 * 
 * Dependency injection port for logger creation, enabling different
 * logging implementations based on environment or configuration.
 */
public interface PipelineLoggerFactory {
    /**
     * Creates default logger implementation
     */
    public fun createDefault(): PipelineLogger
    
    /**
     * Creates environment-specific logger
     * @param environment Target environment
     */
    public fun createForEnvironment(environment: String): PipelineLogger
}

/**
 * Factory interface for creating CommandLauncher instances
 * 
 * Enables different command execution strategies (local, Docker, remote)
 * while maintaining loose coupling through dependency injection.
 */
public interface CommandLauncherFactory {
    /**
     * Creates local command launcher
     */
    public fun createLocal(): CommandLauncher
    
    /**
     * Creates Docker command launcher
     * @param image Docker image to use
     */
    public fun createDocker(image: String): CommandLauncher
    
    /**
     * Creates environment-appropriate launcher
     * @param environment Target environment
     */
    public fun createForEnvironment(environment: String): CommandLauncher
}

/**
 * Factory interface for creating WorkspaceInfo instances
 * 
 * Handles workspace configuration and directory management
 * with environment-specific settings.
 */
public interface WorkspaceInfoFactory {
    /**
     * Creates default workspace configuration
     */
    public fun createDefault(): WorkspaceInfo
    
    /**
     * Creates environment-specific workspace
     * @param environment Target environment
     */
    public fun createForEnvironment(environment: String): WorkspaceInfo
}

/**
 * Factory interface for creating JobInfo instances
 * 
 * Provides job metadata configuration with environment detection
 * and CI/CD system integration.
 */
public interface JobInfoFactory {
    /**
     * Creates default job information
     */
    public fun createDefault(): JobInfo
    
    /**
     * Creates environment-specific job info
     * @param environment Target environment
     */
    public fun createForEnvironment(environment: String): JobInfo
    
    /**
     * Creates job info from environment variables (CI/CD integration)
     */
    public fun createFromEnvironment(): JobInfo
}

/**
 * Default implementation of PipelineLoggerFactory
 */
public class DefaultPipelineLoggerFactory : PipelineLoggerFactory {
    override fun createDefault(): PipelineLogger = DefaultPipelineLogger()
    
    override fun createForEnvironment(environment: String): PipelineLogger = 
        ConfigurablePipelineLoggerFactory().createForEnvironment(environment)
}

/**
 * Default implementation of CommandLauncherFactory
 */
public class DefaultCommandLauncherFactory : CommandLauncherFactory {
    override fun createLocal(): CommandLauncher = LocalCommandLauncher()
    
    override fun createDocker(image: String): CommandLauncher = DockerCommandLauncher(image)
    
    override fun createForEnvironment(environment: String): CommandLauncher =
        when (environment.lowercase()) {
            "development" -> createLocal()
            "production" -> createDocker("ubuntu:20.04")
            else -> createLocal()
        }
}

/**
 * Default implementation of WorkspaceInfoFactory
 */
public class DefaultWorkspaceInfoFactory : WorkspaceInfoFactory {
    override fun createDefault(): WorkspaceInfo = WorkspaceInfo.default()
    
    override fun createForEnvironment(environment: String): WorkspaceInfo =
        WorkspaceInfo.default().copy(
            isCleanWorkspace = environment != "development"
        )
}

/**
 * Default implementation of JobInfoFactory
 */
public class DefaultJobInfoFactory : JobInfoFactory {
    override fun createDefault(): JobInfo = JobInfo.default()
    
    override fun createForEnvironment(environment: String): JobInfo =
        JobInfo.default().copy(
            parameters = mapOf("ENVIRONMENT" to environment)
        )
    
    override fun createFromEnvironment(): JobInfo = JobInfo(
        jobName = System.getenv("JOB_NAME") ?: "unknown-job",
        buildNumber = System.getenv("BUILD_NUMBER") ?: "0",
        buildUrl = System.getenv("BUILD_URL") ?: "",
        gitCommit = System.getenv("GIT_COMMIT") ?: "",
        gitBranch = System.getenv("GIT_BRANCH") ?: "main",
        gitUrl = System.getenv("GIT_URL") ?: "",
        parameters = System.getenv().filterKeys { it.startsWith("PARAM_") }
    )
}