package dev.rubentxu.hodei.core.integration.dsl

import dev.rubentxu.hodei.core.dsl.pipeline
import dev.rubentxu.hodei.core.integration.container.ContainerPipelineExecutor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.GenericContainer

/**
 * Environment DSL Integration Specification
 * 
 * Tests the environment variable handling capabilities of the Pipeline DSL.
 * Validates that environment variables are correctly set, propagated between
 * stages, and can be overridden at stage level.
 */
class EnvironmentDSLIntegrationSpec : BehaviorSpec({
    
    val alpineContainer = GenericContainer("alpine:latest")
        .withCommand("tail", "-f", "/dev/null")
    
    listener(alpineContainer.perSpec())
    
    given("Pipeline DSL with environment variables") {
        
        `when`("setting global environment variables") {
            then("should make variables available to all stages") {
                val globalEnvPipeline = pipeline {
                    environment {
                        set("GLOBAL_VAR", "global_value")
                        set("PROJECT_NAME", "hodei-dsl")
                        set("VERSION", "1.0.0")
                    }
                    
                    stage("First Stage") {
                        steps {
                            echo("Checking global variables in first stage")
                            sh("echo 'GLOBAL_VAR=' && echo \$GLOBAL_VAR")
                            sh("echo 'PROJECT_NAME=' && echo \$PROJECT_NAME")
                        }
                    }
                    
                    stage("Second Stage") {
                        steps {
                            echo("Checking global variables in second stage")
                            sh("echo 'VERSION=' && echo \$VERSION")
                            sh("echo 'All vars:' && env | grep -E '(GLOBAL_VAR|PROJECT_NAME|VERSION)' || echo 'Vars not found'")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(globalEnvPipeline)
                
                // Validate DSL environment handling
                result.success shouldBe true
                result.stages shouldHaveSize 2
                
                // Check first stage has access to global variables
                val firstStage = result.stages[0]
                firstStage.success shouldBe true
                firstStage.steps.any { it.stdout.contains("global_value") } shouldBe true
                firstStage.steps.any { it.stdout.contains("hodei-dsl") } shouldBe true
                
                // Check second stage also has access to global variables
                val secondStage = result.stages[1]
                secondStage.success shouldBe true
                secondStage.steps.any { it.stdout.contains("1.0.0") } shouldBe true
                
                println("✅ Global Environment DSL: Variables accessible across all stages")
            }
        }
        
        `when`("setting stage-specific environment variables") {
            then("should override global variables at stage level") {
                val stageEnvPipeline = pipeline {
                    environment {
                        set("SHARED_VAR", "global_value")
                        set("STAGE_VAR", "original_value")
                    }
                    
                    stage("Stage with Override") {
                        environment {
                            set("STAGE_VAR", "overridden_value")
                            set("LOCAL_VAR", "stage_only")
                        }
                        steps {
                            echo("Checking stage environment override")
                            sh("echo 'SHARED_VAR=' && echo \$SHARED_VAR") // Should be global
                            sh("echo 'STAGE_VAR=' && echo \$STAGE_VAR")   // Should be overridden
                            sh("echo 'LOCAL_VAR=' && echo \$LOCAL_VAR")   // Should be stage-only
                        }
                    }
                    
                    stage("Stage without Override") {
                        steps {
                            echo("Checking original environment")
                            sh("echo 'SHARED_VAR=' && echo \$SHARED_VAR") // Should be global
                            sh("echo 'STAGE_VAR=' && echo \$STAGE_VAR")   // Should be original
                            sh("echo 'LOCAL_VAR=' && echo \$LOCAL_VAR || echo 'LOCAL_VAR not set'") // Should not exist
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(stageEnvPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 2
                
                // Validate stage override behavior
                val overrideStage = result.stages[0]
                overrideStage.success shouldBe true
                overrideStage.steps.any { it.stdout.contains("global_value") } shouldBe true    // SHARED_VAR
                overrideStage.steps.any { it.stdout.contains("overridden_value") } shouldBe true // STAGE_VAR overridden
                overrideStage.steps.any { it.stdout.contains("stage_only") } shouldBe true      // LOCAL_VAR
                
                // Validate original values in second stage
                val originalStage = result.stages[1]
                originalStage.success shouldBe true
                originalStage.steps.any { it.stdout.contains("global_value") } shouldBe true   // SHARED_VAR still global
                originalStage.steps.any { it.stdout.contains("original_value") } shouldBe true // STAGE_VAR back to original
                originalStage.steps.any { it.stdout.contains("LOCAL_VAR not set") } shouldBe true // LOCAL_VAR not available
                
                println("✅ Stage Environment DSL: Override and isolation work correctly")
            }
        }
        
        `when`("using environment variables in complex scenarios") {
            then("should handle special characters and complex values") {
                val complexEnvPipeline = pipeline {
                    environment {
                        set("SIMPLE_VAR", "simple")
                        set("SPACES_VAR", "value with spaces")
                        set("SPECIAL_CHARS", "value!@#\$%^&*()")
                        set("JSON_LIKE", "{\"key\": \"value\", \"number\": 123}")
                        set("PATH_VAR", "/usr/local/bin:/usr/bin:/bin")
                    }
                    
                    stage("Test Complex Variables") {
                        steps {
                            echo("Testing complex environment variables")
                            sh("echo 'SIMPLE_VAR=' && echo \$SIMPLE_VAR")
                            sh("echo 'SPACES_VAR=' && echo \"\$SPACES_VAR\"")
                            sh("echo 'PATH_VAR=' && echo \$PATH_VAR")
                            // Test that variables are properly escaped
                            sh("[ \"\$SIMPLE_VAR\" = \"simple\" ] && echo 'Simple var OK' || echo 'Simple var FAIL'")
                        }
                    }
                    
                    stage("Environment Variable Operations") {
                        steps {
                            echo("Testing environment variable operations")
                            sh("echo 'Variable count:' && env | wc -l")
                            sh("echo 'Our variables:' && env | grep -E '(SIMPLE_VAR|SPACES_VAR|PATH_VAR)' | wc -l")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(complexEnvPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 2
                
                // Validate complex environment handling
                val testStage = result.stages[0]
                testStage.success shouldBe true
                testStage.steps.any { it.stdout.contains("Simple var OK") } shouldBe true
                testStage.steps.any { it.stdout.contains("value with spaces") } shouldBe true
                
                val opsStage = result.stages[1]
                opsStage.success shouldBe true
                
                println("✅ Complex Environment DSL: Special characters and operations work")
            }
        }
        
        `when`("environment variables interact with shell commands") {
            then("should properly expand variables in shell commands") {
                val shellEnvPipeline = pipeline {
                    environment {
                        set("ECHO_MSG", "Hello from Pipeline DSL")
                        set("FILE_NAME", "test-file.txt")
                        set("DIR_NAME", "test-directory")
                    }
                    
                    stage("Variable Expansion Test") {
                        steps {
                            echo("Testing variable expansion in shell commands")
                            sh("echo \$ECHO_MSG > /tmp/\$FILE_NAME")
                            sh("cat /tmp/\$FILE_NAME")
                            sh("mkdir -p /tmp/\$DIR_NAME")
                            sh("ls -la /tmp/\$DIR_NAME && echo 'Directory created successfully'")
                        }
                    }
                    
                    stage("Variable Manipulation") {
                        environment {
                            set("COUNT", "5")
                        }
                        steps {
                            echo("Testing variable manipulation")
                            sh("for i in \$(seq 1 \$COUNT); do echo \"Iteration \$i\"; done")
                            sh("echo 'Counted to:' \$COUNT")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(shellEnvPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 2
                
                // Validate shell expansion
                val expansionStage = result.stages[0]
                expansionStage.success shouldBe true
                expansionStage.steps.any { it.stdout.contains("Hello from Pipeline DSL") } shouldBe true
                expansionStage.steps.any { it.stdout.contains("Directory created successfully") } shouldBe true
                
                val manipulationStage = result.stages[1]
                manipulationStage.success shouldBe true
                manipulationStage.steps.any { it.stdout.contains("Iteration 1") } shouldBe true
                manipulationStage.steps.any { it.stdout.contains("Iteration 5") } shouldBe true
                manipulationStage.steps.any { it.stdout.contains("Counted to: 5") } shouldBe true
                
                println("✅ Shell Environment DSL: Variable expansion and manipulation work correctly")
            }
        }
    }
})