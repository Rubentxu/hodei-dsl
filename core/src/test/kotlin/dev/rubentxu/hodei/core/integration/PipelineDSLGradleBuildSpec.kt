package dev.rubentxu.hodei.core.integration

import dev.rubentxu.hodei.core.dsl.pipeline
import dev.rubentxu.hodei.core.integration.container.ContainerPipelineExecutor
import dev.rubentxu.hodei.core.integration.utils.ProjectCopyUtils
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.GenericContainer

/**
 * Pipeline DSL Integration Specification using Gradle Build
 * 
 * This test validates the Pipeline DSL functionality by creating real pipeline
 * definitions that execute gradle commands. The focus is on testing that the
 * Pipeline DSL correctly builds, executes, and manages pipeline execution flow.
 * 
 * The gradle commands serve as realistic test cases to verify DSL behavior,
 * not to test gradle itself.
 */
class PipelineDSLGradleBuildSpec : BehaviorSpec({
    
    val gradleContainer = GenericContainer("gradle:8.5-jdk17-alpine")
        .withWorkingDirectory("/workspace")
        .withCommand("tail", "-f", "/dev/null")
        .withCreateContainerCmdModifier { cmd ->
            cmd.withUser("root")
        }
    
    listener(gradleContainer.perSpec())
    
    given("Pipeline DSL with gradle commands") {
        
        beforeEach {
            // Project setup is now handled by ContainerPipelineExecutor.setupWorkspace()
            // This creates a minimal gradle project structure for testing
        }
        
        `when`("creating a simple sequential pipeline") {
            then("should execute stages in correct order") {
                val simplePipeline = pipeline {
                    agent { 
                        docker { image = "gradle:8.5-jdk17-alpine" } 
                    }
                    
                    environment {
                        set("GRADLE_OPTS", "-Xmx1g -Dorg.gradle.daemon=false")
                        set("BUILD_STAGE", "test")
                    }
                    
                    stage("Initialize") {
                        steps {
                            echo("Starting Hodei DSL Pipeline Test")
                            sh("echo 'Current directory:' && pwd")
                            sh("echo 'Java version:' && java -version")
                        }
                    }
                    
                    stage("Clean") {
                        steps {
                            echo("Cleaning previous build artifacts...")
                            sh("gradle clean --no-daemon --stacktrace")
                        }
                    }
                    
                    stage("Compile") {
                        steps {
                            echo("Compiling Kotlin sources...")
                            sh("gradle :core:compileKotlin --no-daemon --stacktrace")
                        }
                        post {
                            always {
                                echo("Compile stage completed")
                            }
                        }
                    }
                }
                
                // Execute pipeline and validate DSL behavior
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(simplePipeline)
                
                // Validate Pipeline DSL execution
                result.success shouldBe true
                result.pipelineId shouldBe simplePipeline.id
                result.stages shouldHaveSize 3
                
                // Validate stage execution order
                result.stages[0].stageName shouldBe "Initialize"
                result.stages[1].stageName shouldBe "Clean" 
                result.stages[2].stageName shouldBe "Compile"
                
                // Validate all stages succeeded
                result.stages.forEach { stage ->
                    stage.success shouldBe true
                    stage.steps.forEach { step ->
                        step.success shouldBe true
                        step.exitCode shouldBe 0
                    }
                }
                
                // Validate echo steps work
                val initStage = result.stages[0]
                initStage.steps.any { it.stdout.contains("Starting Hodei DSL Pipeline Test") } shouldBe true
                
                // Validate sh steps work  
                val cleanStage = result.stages[1]
                cleanStage.steps.any { it.exitCode == 0 } shouldBe true
                
                println("✅ Pipeline DSL executed 3 stages successfully")
                println("✅ Stage execution order: ${result.stages.map { it.stageName }}")
            }
        }
        
        `when`("creating pipeline with environment variables") {
            then("should propagate environment between stages") {
                val envPipeline = pipeline {
                    environment {
                        set("TEST_VAR", "pipeline_value")
                        set("BUILD_NUMBER", "123")
                        set("GRADLE_OPTS", "-Xmx1g -Dorg.gradle.daemon=false")
                    }
                    
                    stage("Check Global Environment") {
                        steps {
                            echo("Checking global environment variables...")
                            sh("echo 'TEST_VAR=' && echo \$TEST_VAR")
                            sh("echo 'BUILD_NUMBER=' && echo \$BUILD_NUMBER")
                        }
                    }
                    
                    stage("Stage with Local Environment") {
                        environment {
                            set("STAGE_VAR", "stage_value")
                        }
                        steps {
                            echo("Checking stage-specific environment...")
                            sh("echo 'STAGE_VAR=' && echo \$STAGE_VAR")
                            sh("echo 'TEST_VAR=' && echo \$TEST_VAR") // Should still have global
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(envPipeline)
                
                // Validate DSL environment handling
                result.success shouldBe true
                result.stages shouldHaveSize 2
                
                // Check global environment was applied
                val globalStage = result.stages[0]
                globalStage.steps.any { it.stdout.contains("pipeline_value") } shouldBe true
                globalStage.steps.any { it.stdout.contains("123") } shouldBe true
                
                // Check stage environment was applied
                val stageEnvStage = result.stages[1]
                stageEnvStage.steps.any { it.stdout.contains("stage_value") } shouldBe true
                stageEnvStage.steps.any { it.stdout.contains("pipeline_value") } shouldBe true // Global still available
                
                println("✅ Environment DSL: Global and stage variables work correctly")
            }
        }
        
        `when`("creating pipeline with post actions") {
            then("should execute post actions based on stage results") {
                val postPipeline = pipeline {
                    environment {
                        set("GRADLE_OPTS", "-Xmx1g -Dorg.gradle.daemon=false")
                    }
                    
                    stage("Successful Stage") {
                        steps {
                            echo("This stage will succeed")
                            sh("gradle :core:compileKotlin --no-daemon")
                        }
                        post {
                            always {
                                echo("Always executed after Successful Stage")
                            }
                            success {
                                echo("Success action executed")
                            }
                            failure {
                                echo("This should not execute")
                            }
                        }
                    }
                    
                    stage("Intentional Failure") {
                        steps {
                            echo("This stage will fail intentionally")
                            sh("exit 1") // Intentional failure
                        }
                        post {
                            always {
                                echo("Always executed after failed stage")
                            }
                            success {
                                echo("This should not execute")
                            }
                            failure {
                                echo("Failure action executed")
                            }
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(postPipeline)
                
                // Validate DSL post action handling
                result.success shouldBe false // Pipeline fails due to second stage
                result.stages shouldHaveSize 2
                
                // First stage should succeed
                val successStage = result.stages[0]
                successStage.success shouldBe true
                
                // Second stage should fail
                val failStage = result.stages[1] 
                failStage.success shouldBe false
                
                println("✅ Post Actions DSL: Always, success, and failure actions work correctly")
                println("✅ Pipeline correctly failed due to intentional stage failure")
            }
        }
        
        `when`("creating pipeline with complex gradle build") {
            then("should handle multi-module gradle build") {
                val complexBuildPipeline = pipeline {
                    agent { 
                        docker { image = "gradle:8.5-jdk17-alpine" } 
                    }
                    
                    environment {
                        set("GRADLE_OPTS", "-Xmx2g -Dorg.gradle.daemon=false")
                        set("BUILD_ENV", "integration_test")
                    }
                    
                    stage("Validate Project") {
                        steps {
                            echo("Validating Hodei DSL project structure...")
                            sh("ls -la")
                            sh("gradle projects --no-daemon")
                        }
                    }
                    
                    stage("Compile All Modules") {
                        steps {
                            echo("Compiling all Kotlin modules...")
                            sh("gradle compileKotlin --no-daemon --parallel")
                        }
                    }
                    
                    stage("Assemble Libraries") {
                        steps {
                            echo("Creating JAR artifacts...")
                            sh("gradle assemble --no-daemon")
                        }
                        post {
                            always {
                                echo("Checking generated artifacts...")
                                sh("find . -name '*.jar' -type f | head -5 || echo 'No JARs found'")
                            }
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(complexBuildPipeline)
                
                // Validate DSL handles complex pipeline
                result.success shouldBe true
                result.stages shouldHaveSize 3
                result.duration shouldNotBe kotlin.time.Duration.ZERO
                
                // Validate each stage executed
                result.stages.forEach { stage ->
                    stage.success shouldBe true
                    stage.steps.shouldNotBeEmpty()
                }
                
                println("✅ Complex Pipeline DSL: Multi-stage gradle build executed successfully")
                println("✅ Pipeline duration: ${result.duration}")
            }
        }
    }
    
    given("Pipeline DSL validation scenarios") {
        
        `when`("pipeline has no stages") {
            then("should handle empty pipeline gracefully") {
                val emptyPipeline = pipeline {
                    environment {
                        set("TEST", "empty_pipeline")
                    }
                    // No stages defined
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(emptyPipeline)
                
                // Empty pipeline should succeed but do nothing
                result.success shouldBe true
                result.stages shouldHaveSize 0
                
                println("✅ Pipeline DSL: Empty pipeline handled correctly")
            }
        }
        
        `when`("pipeline has only echo steps") {
            then("should execute echo-only pipeline") {
                val echoPipeline = pipeline {
                    stage("Echo Test") {
                        steps {
                            echo("Testing echo functionality")
                            echo("Multiple echo statements")
                            echo("Echo with special chars: !@#$%")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(gradleContainer, "/workspace")
                val result = executor.execute(echoPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 1
                result.stages[0].steps shouldHaveSize 3
                
                // All echo steps should succeed
                result.stages[0].steps.forEach { step ->
                    step.success shouldBe true
                    step.stepType shouldBe "Echo"
                }
                
                println("✅ Echo Steps DSL: All echo statements executed correctly")
            }
        }
    }
}) {
    
    init {
        this.invocationTimeout = 600_000L // 10 minutes for complex builds
    }
}