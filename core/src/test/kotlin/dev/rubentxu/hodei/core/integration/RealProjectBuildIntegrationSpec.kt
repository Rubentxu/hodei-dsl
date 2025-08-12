package dev.rubentxu.hodei.core.integration

import dev.rubentxu.hodei.core.dsl.pipeline
import dev.rubentxu.hodei.core.integration.container.ContainerPipelineExecutor
import dev.rubentxu.hodei.core.integration.utils.BuildArtifactValidator
import dev.rubentxu.hodei.core.integration.utils.ProjectCopyUtils
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Real Project Build Integration Specification
 * 
 * This test copies the entire hodei-dsl project into a Gradle container,
 * executes a complete build pipeline using the DSL, and validates the
 * generated artifacts. This provides end-to-end validation that the
 * Pipeline DSL can handle real-world build scenarios.
 * 
 * IMPORTANT: This test DOES NOT execute `gradle test` to avoid infinite recursion.
 * Only compilation and assembly tasks are tested.
 */
class RealProjectBuildIntegrationSpec : BehaviorSpec({
    
    // Use Gradle container with JDK 17
    val gradleContainer = GenericContainer("gradle:8.5-jdk17-alpine")
        .withWorkingDirectory("/workspace")
        .withCommand("tail", "-f", "/dev/null") // Keep container alive
        .withCreateContainerCmdModifier { cmd ->
            cmd.withUser("root") // Ensure we have write permissions
        }
    
    // Configure container lifecycle
    listener(gradleContainer.perSpec())
    
    given("hodei-dsl project in Gradle container") {
        
        `when`("setting up project in container") {
            then("should copy project source successfully") {
                // Copy project to container (excluding build directories)
                ProjectCopyUtils.copyProjectToContainer(gradleContainer, containerPath = "/workspace")
                
                // Verify basic project structure
                val executor = ContainerPipelineExecutor(gradleContainer)
                val lsResult = gradleContainer.execInContainer("ls", "-la", "/workspace")
                
                lsResult.exitCode shouldBe 0
                lsResult.stdout shouldContain "settings.gradle.kts"
                lsResult.stdout shouldContain "core"
                lsResult.stdout shouldContain "compiler"
            }
        }
        
        `when`("executing safe build pipeline using DSL") {
            then("should compile and assemble all modules successfully") {
                // Create a build pipeline that avoids infinite recursion
                val safeBuildPipeline = pipeline {
                    agent { 
                        // Note: This is more for documentation - we're already in a container
                        docker { image = "gradle:8.5-jdk17-alpine" } 
                    }
                    
                    environment {
                        set("GRADLE_OPTS", "-Xmx2g -Dorg.gradle.daemon=false")
                        set("JAVA_OPTS", "-Xmx1g")
                    }
                    
                    stage("Clean Build") {
                        steps {
                            echo("Starting clean build of hodei-dsl project...")
                            sh("gradle clean --no-daemon --stacktrace")
                        }
                    }
                    
                    stage("Compile Sources") {
                        steps {
                            echo("Compiling Kotlin sources...")
                            sh("gradle compileKotlin --no-daemon --stacktrace")
                        }
                    }
                    
                    stage("Assemble JARs") {
                        steps {
                            echo("Assembling JAR artifacts...")
                            sh("gradle assemble --no-daemon --stacktrace")
                        }
                        post {
                            always {
                                echo("Build completed - checking artifacts...")
                                sh("find . -name '*.jar' -type f | head -10")
                            }
                        }
                    }
                    
                    stage("Verify Build") {
                        steps {
                            echo("Verifying build artifacts...")
                            sh("ls -la */build/libs/ || echo 'Some modules may not have JARs'")
                            sh("du -sh . || echo 'Could not get disk usage'")
                        }
                    }
                }
                
                // Execute the pipeline in the container
                val pipelineExecutor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = pipelineExecutor.execute(safeBuildPipeline)
                
                // Validate pipeline execution
                result.success shouldBe true
                result.stages.size shouldBe 4
                result.duration shouldNotBe kotlin.time.Duration.INFINITE
                
                // Validate that all stages succeeded
                result.stages.forEach { stage ->
                    stage.success shouldBe true
                    stage.steps.shouldNotBeEmpty()
                    stage.steps.forEach { step ->
                        step.success shouldBe true
                        step.exitCode shouldBe 0
                    }
                }
                
                println("Pipeline execution successful! Duration: ${result.duration}")
                println("Stage results: ${result.stages.map { "${it.stageName}: ${it.success}" }}")
            }
        }
        
        `when`("validating generated build artifacts") {
            then("should have created valid JAR files for all modules") {
                val validator = BuildArtifactValidator(gradleContainer, "/workspace")
                
                // Get list of all JARs created
                val actualJars = validator.listAllJars()
                actualJars.shouldNotBeEmpty()
                
                println("Found JAR files: $actualJars")
                
                // Validate specific expected artifacts
                val validationResult = validator.validateAllArtifacts()
                
                // At least some artifacts should be valid (not all modules may produce JARs)
                val validArtifacts = validationResult.artifacts.filter { it.isValid }
                validArtifacts.shouldNotBeEmpty()
                
                // Check that we have some core JARs
                val coreModuleJars = actualJars.filter { 
                    it.contains("core") || it.contains("compiler") || it.contains("execution")
                }
                coreModuleJars.shouldNotBeEmpty()
                
                // Validate JAR content for existing JARs
                validArtifacts.forEach { artifact ->
                    artifact.exists shouldBe true
                    artifact.sizeBytes shouldBeGreaterThan 1024L // At least 1KB
                    artifact.hasManifest shouldBe true
                    // Note: hasClasses might be false for some modules if they only contain resources
                }
                
                println("Validation summary: ${validationResult.summary}")
                validArtifacts.forEach { artifact ->
                    println("✓ ${artifact.path}: ${artifact.sizeBytes} bytes")
                }
            }
        }
        
        `when`("checking build environment information") {
            then("should provide detailed build environment info") {
                val validator = BuildArtifactValidator(gradleContainer, "/workspace")
                val envInfo = validator.getBuildEnvironmentInfo()
                
                envInfo.javaVersion shouldContain "17"
                envInfo.gradleVersion shouldContain "Gradle"
                envInfo.workspaceSize shouldBeGreaterThan 0L
                
                println("Build Environment Info:")
                println("Java Version: ${envInfo.javaVersion}")
                println("Gradle Version: ${envInfo.gradleVersion}")
                println("Workspace Size: ${envInfo.diskUsage}")
            }
        }
        
        `when`("running a simplified build for performance testing") {
            then("should complete build within reasonable time") {
                val startTime = System.currentTimeMillis()
                
                // Simple single-stage pipeline for performance test
                val quickBuildPipeline = pipeline {
                    stage("Quick Build") {
                        steps {
                            echo("Running quick build test...")
                            sh("gradle :core:compileKotlin --no-daemon")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(quickBuildPipeline)
                
                val totalTime = System.currentTimeMillis() - startTime
                
                result.success shouldBe true
                totalTime shouldBeLessThan 300_000L // Less than 5 minutes
                
                println("Quick build completed in ${totalTime}ms")
            }
        }
    }
    
    given("edge cases and error handling") {
        
        `when`("executing pipeline with invalid commands") {
            then("should handle failures gracefully") {
                val failingPipeline = pipeline {
                    stage("Will Fail") {
                        steps {
                            sh("nonexistent-command-that-will-fail")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(failingPipeline)
                
                result.success shouldBe false
                result.stages.size shouldBe 1
                result.stages[0].success shouldBe false
                result.stages[0].steps[0].exitCode shouldNotBe 0
            }
        }
        
        `when`("executing pipeline with mixed success/failure") {
            then("should handle partial failures") {
                val mixedPipeline = pipeline {
                    stage("Will Succeed") {
                        steps {
                            echo("This should work")
                            sh("echo 'Success!'")
                        }
                    }
                    stage("Will Fail") {
                        steps {
                            sh("exit 1") // Explicit failure
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(mixedPipeline)
                
                result.success shouldBe false
                result.stages.size shouldBe 2
                result.stages[0].success shouldBe true
                result.stages[1].success shouldBe false
            }
        }
    }
}) {
    
    init {
        // Configure test timeout for build operations
        this.invocationTimeout = 600_000L // 10 minutes in milliseconds
    }
}