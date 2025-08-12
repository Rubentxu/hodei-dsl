package dev.rubentxu.hodei.core.integration.container

import dev.rubentxu.hodei.core.execution.CommandLauncher
import dev.rubentxu.hodei.core.execution.CommandResult
import dev.rubentxu.hodei.core.execution.LauncherType
import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * CommandLauncher implementation that executes commands inside a Testcontainer
 * 
 * This launcher allows the Pipeline DSL to execute commands within a containerized
 * environment, providing isolation and reproducibility for integration tests.
 */
public class ContainerCommandLauncher(
    private val container: GenericContainer<*>
) : CommandLauncher {
    
    override val type: LauncherType = LauncherType.DOCKER
    
    override suspend fun execute(
        command: String,
        workingDir: String?,
        environment: Map<String, String>
    ): CommandResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Prepare command with working directory if specified
            val fullCommand = if (workingDir != null) {
                arrayOf("sh", "-c", "cd $workingDir && $command")
            } else {
                arrayOf("sh", "-c", command)
            }
            
            // Prepare environment variables
            val envArray = environment.map { "${it.key}=${it.value}" }.toTypedArray()
            
            // Execute command in container
            val execResult = if (envArray.isNotEmpty()) {
                container.execInContainer(*fullCommand, *envArray)
            } else {
                container.execInContainer(*fullCommand)
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            return CommandResult(
                exitCode = execResult.exitCode,
                stdout = execResult.stdout ?: "",
                stderr = execResult.stderr ?: "",
                durationMs = duration,
                success = execResult.exitCode == 0
            )
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            
            return CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Container execution failed: ${e.message}",
                durationMs = duration,
                success = false
            )
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        return try {
            container.isRunning
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Extension functions for easier container command execution
 */
public suspend fun GenericContainer<*>.executeCommand(
    command: String,
    workingDir: String? = null,
    environment: Map<String, String> = emptyMap()
): CommandResult {
    val launcher = ContainerCommandLauncher(this)
    return launcher.execute(command, workingDir, environment)
}

/**
 * Helper function to execute multiple commands in sequence
 */
public suspend fun GenericContainer<*>.executeCommands(
    commands: List<String>,
    workingDir: String? = null,
    environment: Map<String, String> = emptyMap()
): List<CommandResult> {
    val launcher = ContainerCommandLauncher(this)
    return commands.map { command ->
        launcher.execute(command, workingDir, environment)
    }
}