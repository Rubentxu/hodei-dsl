package dev.rubentxu.hodei.core.integration.container

import dev.rubentxu.hodei.core.domain.model.Pipeline
import dev.rubentxu.hodei.core.domain.model.PostAction
import dev.rubentxu.hodei.core.domain.model.PostCondition
import dev.rubentxu.hodei.core.domain.model.Stage
import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pipeline executor that runs pipelines inside Testcontainers
 * 
 * This executor provides a complete integration testing environment where
 * pipelines are executed in isolated containers, allowing for safe testing
 * of real commands without affecting the host system.
 */
public class ContainerPipelineExecutor(
    private val container: GenericContainer<*>,
    private val workspaceDir: String = "/workspace"
) {
    
    private val commandLauncher = ContainerCommandLauncher(container)
    
    /**
     * Executes a complete pipeline in the container
     */
    public fun execute(pipeline: Pipeline): ContainerExecutionResult {
        val startTime = Instant.now()
        val stageResults = mutableListOf<ContainerStageResult>()
        val artifacts = mutableListOf<String>()
        
        try {
            // Ensure container is running
            if (!container.isRunning) {
                throw IllegalStateException("Container is not running")
            }
            
            // Setup workspace in container
            setupWorkspace()
            
            // Execute stages sequentially
            for (stage in pipeline.stages) {
                val stageResult = executeStage(stage, pipeline.globalEnvironment)
                stageResults.add(stageResult)
                
                // Collect artifacts from this stage
                artifacts.addAll(stageResult.artifacts)
                
                // Stop execution if stage failed and no error handling configured
                if (!stageResult.success) {
                    // TODO: Implement proper error handling based on pipeline configuration
                    break
                }
            }
            
            val endTime = Instant.now()
            val success = stageResults.all { it.success }
            
            return ContainerExecutionResult(
                pipelineId = pipeline.id,
                success = success,
                stages = stageResults,
                artifacts = artifacts,
                startTime = startTime,
                endTime = endTime,
                duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).milliseconds
            )
            
        } catch (e: Exception) {
            val endTime = Instant.now()
            
            return ContainerExecutionResult(
                pipelineId = pipeline.id,
                success = false,
                stages = stageResults,
                artifacts = artifacts,
                startTime = startTime,
                endTime = endTime,
                duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).milliseconds,
                error = e.message
            )
        }
    }
    
    /**
     * Executes a single stage in the container
     */
    private fun executeStage(
        stage: Stage,
        globalEnvironment: Map<String, String>
    ): ContainerStageResult {
        val startTime = Instant.now()
        val stepResults = mutableListOf<ContainerStepResult>()
        val artifacts = mutableListOf<String>()
        
        // Merge global and stage-specific environment
        val stageEnvironment = globalEnvironment + stage.environment
        
        try {
            // Execute all steps in the stage
            for (step in stage.steps) {
                val stepResult = executeStep(step, stageEnvironment)
                stepResults.add(stepResult)
                
                // Handle step failures
                if (!stepResult.success) {
                    // TODO: Implement proper step failure handling
                    break
                }
                
                // Collect artifacts if this is an archive step
                if (step is Step.ArchiveArtifacts) {
                    artifacts.addAll(findArtifacts(step.artifacts))
                }
            }
            
            // Execute post actions
            val stageSuccess = stepResults.all { it.success }
            for (postAction in stage.post) {
                val shouldExecute = when (postAction.condition) {
                    PostCondition.ALWAYS -> true
                    PostCondition.SUCCESS -> stageSuccess
                    PostCondition.FAILURE -> !stageSuccess
                    PostCondition.UNSTABLE -> false // TODO: implement unstable detection
                    PostCondition.CHANGED -> false // TODO: implement change detection
                    else -> false
                }
                
                if (shouldExecute) {
                    for (postStep in postAction.steps) {
                        val postStepResult = executeStep(postStep, stageEnvironment)
                        stepResults.add(postStepResult)
                    }
                }
            }
            
            val endTime = Instant.now()
            val success = stepResults.all { it.success }
            
            return ContainerStageResult(
                stageName = stage.name,
                success = success,
                steps = stepResults,
                artifacts = artifacts,
                startTime = startTime,
                endTime = endTime,
                duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).milliseconds
            )
            
        } catch (e: Exception) {
            val endTime = Instant.now()
            
            return ContainerStageResult(
                stageName = stage.name,
                success = false,
                steps = stepResults,
                artifacts = artifacts,
                startTime = startTime,
                endTime = endTime,
                duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).milliseconds,
                error = e.message
            )
        }
    }
    
    /**
     * Executes a single step in the container
     */
    private fun executeStep(
        step: Step,
        environment: Map<String, String>
    ): ContainerStepResult {
        val startTime = Instant.now()
        
        try {
            val result = runBlocking {
                when (step) {
                    is Step.Shell -> {
                        commandLauncher.execute(step.script, workspaceDir, environment)
                    }
                    is Step.Echo -> {
                        commandLauncher.execute("echo '${step.message}'", workspaceDir, environment)
                    }
                    is Step.ArchiveArtifacts -> {
                        // For archive artifacts, we just verify the files exist
                        commandLauncher.execute("find . -path '${step.artifacts}' -type f", workspaceDir, environment)
                    }
                    else -> {
                        // For unsupported steps, just log and continue
                        CommandResult(0, "Step ${step::class.simpleName} not implemented", "", 0, true)
                    }
                }
            }
            
            val endTime = Instant.now()
            
            return ContainerStepResult(
                stepType = step::class.simpleName ?: "Unknown",
                success = result.success,
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
                startTime = startTime,
                endTime = endTime,
                duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).milliseconds
            )
            
        } catch (e: Exception) {
            val endTime = Instant.now()
            
            return ContainerStepResult(
                stepType = step::class.simpleName ?: "Unknown",
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Unknown error",
                startTime = startTime,
                endTime = endTime,
                duration = (endTime.toEpochMilli() - startTime.toEpochMilli()).milliseconds
            )
        }
    }
    
    /**
     * Sets up the workspace directory in the container
     */
    private fun setupWorkspace() {
        runBlocking {
            commandLauncher.execute("mkdir -p $workspaceDir", null, emptyMap())
            commandLauncher.execute("cd $workspaceDir", null, emptyMap())
            
            // Create a minimal gradle project structure for testing
            createMinimalGradleProject()
        }
    }
    
    /**
     * Creates a minimal gradle project in the container workspace
     */
    private fun createMinimalGradleProject() {
        runBlocking {
            // Create settings.gradle.kts
            commandLauncher.execute("""
                cat > $workspaceDir/settings.gradle.kts << 'EOF'
rootProject.name = "hodei-test-project"
include(":core")
EOF
            """.trimIndent(), null, emptyMap())
            
            // Create root build.gradle.kts
            commandLauncher.execute("""
                cat > $workspaceDir/build.gradle.kts << 'EOF'
plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "kotlin")
    
    repositories {
        mavenCentral()
    }
    
    dependencies {
        implementation(kotlin("stdlib"))
    }
}
EOF
            """.trimIndent(), null, emptyMap())
            
            // Create core module directory structure
            commandLauncher.execute("mkdir -p $workspaceDir/core/src/main/kotlin", null, emptyMap())
            commandLauncher.execute("mkdir -p $workspaceDir/core/src/test/kotlin", null, emptyMap())
            
            // Create core/build.gradle.kts
            commandLauncher.execute("""
                cat > $workspaceDir/core/build.gradle.kts << 'EOF'
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
EOF
            """.trimIndent(), null, emptyMap())
            
            // Create a simple Kotlin file
            commandLauncher.execute("""
                cat > $workspaceDir/core/src/main/kotlin/TestClass.kt << 'EOF'
package hodei.test

class TestClass {
    fun hello(): String = "Hello from Gradle build!"
}
EOF
            """.trimIndent(), null, emptyMap())
        }
    }
    
    /**
     * Finds artifacts matching the given pattern
     */
    private fun findArtifacts(pattern: String): List<String> {
        return runBlocking {
            val result = commandLauncher.execute("find . -path '$pattern' -type f", workspaceDir, emptyMap())
            if (result.success) {
                result.stdout.split("\n").filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        }
    }
}

/**
 * Result of container pipeline execution
 */
public data class ContainerExecutionResult(
    val pipelineId: String,
    val success: Boolean,
    val stages: List<ContainerStageResult>,
    val artifacts: List<String>,
    val startTime: Instant,
    val endTime: Instant,
    val duration: kotlin.time.Duration,
    val error: String? = null
)

/**
 * Result of container stage execution
 */
public data class ContainerStageResult(
    val stageName: String,
    val success: Boolean,
    val steps: List<ContainerStepResult>,
    val artifacts: List<String>,
    val startTime: Instant,
    val endTime: Instant,
    val duration: kotlin.time.Duration,
    val error: String? = null
)

/**
 * Result of container step execution
 */
public data class ContainerStepResult(
    val stepType: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: kotlin.time.Duration
)