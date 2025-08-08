package dev.rubentxu.hodei.core.execution

/**
 * Command launcher interface for executing commands in different environments
 * 
 * Provides abstraction for command execution allowing different implementations
 * for local execution, Docker containers, remote execution, etc.
 */
public interface CommandLauncher {
    
    /**
     * Executes a command synchronously
     * @param command Command to execute
     * @param workingDir Working directory for command execution
     * @param environment Environment variables for the command
     * @return Command execution result
     */
    public suspend fun execute(
        command: String,
        workingDir: String? = null,
        environment: Map<String, String> = emptyMap()
    ): CommandResult
    
    /**
     * Checks if this launcher is available for use
     * @return true if the launcher can execute commands
     */
    public suspend fun isAvailable(): Boolean
    
    /**
     * Gets the launcher type identifier
     */
    public val type: LauncherType
}

/**
 * Result of command execution
 */
public data class CommandResult(
    /**
     * Exit code of the command
     */
    val exitCode: Int,
    
    /**
     * Standard output of the command
     */
    val stdout: String,
    
    /**
     * Standard error output of the command  
     */
    val stderr: String,
    
    /**
     * Execution duration in milliseconds
     */
    val durationMs: Long,
    
    /**
     * Whether the command was successful (exit code 0)
     */
    val success: Boolean = exitCode == 0
)

/**
 * Types of command launchers
 */
public enum class LauncherType {
    LOCAL, DOCKER, KUBERNETES, SSH, AGENT
}

/**
 * Local command launcher implementation
 * 
 * Executes commands on the local system using ProcessBuilder.
 */
public class LocalCommandLauncher : CommandLauncher {
    
    override val type: LauncherType = LauncherType.LOCAL
    
    override suspend fun execute(
        command: String,
        workingDir: String?,
        environment: Map<String, String>
    ): CommandResult {
        val startTime = System.currentTimeMillis()
        
        try {
            val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
            
            // Set working directory if provided
            workingDir?.let { 
                processBuilder.directory(java.io.File(it))
            }
            
            // Add environment variables
            if (environment.isNotEmpty()) {
                processBuilder.environment().putAll(environment)
            }
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            
            val duration = System.currentTimeMillis() - startTime
            
            return CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Command execution failed: ${e.message}",
                durationMs = duration
            )
        }
    }
    
    override suspend fun isAvailable(): Boolean = true
}

/**
 * Docker command launcher implementation
 * 
 * Executes commands inside Docker containers.
 */
public class DockerCommandLauncher(
    private val image: String,
    private val containerArgs: List<String> = emptyList()
) : CommandLauncher {
    
    override val type: LauncherType = LauncherType.DOCKER
    
    override suspend fun execute(
        command: String,
        workingDir: String?,
        environment: Map<String, String>
    ): CommandResult {
        val dockerCommand = buildDockerCommand(command, workingDir, environment)
        
        // Delegate to local launcher for executing docker command
        val localLauncher = LocalCommandLauncher()
        return localLauncher.execute(dockerCommand)
    }
    
    override suspend fun isAvailable(): Boolean {
        val localLauncher = LocalCommandLauncher()
        val result = localLauncher.execute("docker --version")
        return result.success
    }
    
    private fun buildDockerCommand(
        command: String,
        workingDir: String?,
        environment: Map<String, String>
    ): String {
        val dockerCmd = mutableListOf("docker", "run", "--rm")
        
        // Add container arguments
        dockerCmd.addAll(containerArgs)
        
        // Add working directory
        workingDir?.let {
            dockerCmd.addAll(listOf("-w", it))
        }
        
        // Add environment variables
        environment.forEach { (key, value) ->
            dockerCmd.addAll(listOf("-e", "$key=$value"))
        }
        
        // Add image
        dockerCmd.add(image)
        
        // Add command to execute
        dockerCmd.addAll(listOf("sh", "-c", command))
        
        return dockerCmd.joinToString(" ")
    }
}