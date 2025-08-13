package dev.rubentxu.hodei.core.dsl

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.dsl.builders.*
import dev.rubentxu.hodei.core.dsl.context.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain

/**
 * Specification for improved builder API with modern Kotlin syntax
 * 
 * Tests advanced features of the enhanced DSL including better type inference,
 * improved readability, and more concise syntax patterns.
 */
class ImprovedBuilderAPISpec : BehaviorSpec({
    
    given("enhanced DSL with improved syntax") {
        
        `when`("creating complex pipeline with modern syntax") {
            val pipeline = pipelineWithContextReceivers {
                stage("prepare") {
                    sh("echo 'Preparing environment'")
                    echo("Environment ready")
                }
                
                stage("build") {
                    sh("./gradlew build")
                    echo("Build successful")
                }
                
                stage("test") {
                    sh("./gradlew test")
                    echo("Tests passed")
                }
                
                stage("deploy") {
                    sh("echo 'Deploying application'")
                    echo("Deployment complete")
                }
            }
            
            then("should create well-structured pipeline") {
                pipeline shouldNotBe null
                pipeline.stages shouldHaveSize 4
                
                val stageNames = pipeline.stages.map { it.name }
                stageNames shouldBe listOf("prepare", "build", "test", "deploy")
                
                // Verify each stage has proper steps
                pipeline.stages.forEach { stage ->
                    stage.steps.shouldNotBe(null)
                    // Each stage has 2 steps (sh + echo)
                    stage.steps.size shouldBe 2
                }
                
                // Check specific stage content
                val buildStage = pipeline.stages.find { it.name == "build" }
                buildStage shouldNotBe null
                
                val buildSteps = buildStage!!.steps
                buildSteps shouldHaveSize 2
                
                val firstShellStep = buildSteps[0] as Step.Shell
                firstShellStep.script shouldBe "./gradlew build"
                
                val secondEchoStep = buildSteps[1] as Step.Echo
                secondEchoStep.message shouldBe "Build successful"
            }
        }
        
        `when`("comparing traditional vs modern syntax performance") {
            val traditionalPipeline = pipeline {
                stage("test-traditional") {
                    steps {
                        sh("echo 'Traditional syntax'")
                        echo("Traditional echo")
                    }
                }
            }
            
            val modernPipeline = pipelineWithContextReceivers {
                stage("test-modern") {
                    sh("echo 'Modern syntax'")
                    echo("Modern echo")
                }
            }
            
            then("both should produce equivalent results") {
                traditionalPipeline.stages shouldHaveSize 1
                modernPipeline.stages shouldHaveSize 1
                
                val traditionalStage = traditionalPipeline.stages.first()
                val modernStage = modernPipeline.stages.first()
                
                traditionalStage.steps shouldHaveSize 2
                modernStage.steps shouldHaveSize 2
                
                // Both should have equivalent step types
                traditionalStage.steps[0]::class shouldBe modernStage.steps[0]::class
                traditionalStage.steps[1]::class shouldBe modernStage.steps[1]::class
            }
        }
        
        `when`("building complex steps collection with modern syntax") {
            val steps = buildStepsWithContextReceivers {
                echo("Starting complex workflow")
                sh("docker --version")
                sh("kubectl version")
                echo("Tools verified")
                sh("helm version")
                echo("Workflow complete")
            }
            
            then("should create ordered step collection") {
                steps shouldHaveSize 6
                
                // Verify step sequence
                steps[0] shouldBe Step.echo("Starting complex workflow")
                
                val dockerStep = steps[1] as Step.Shell
                dockerStep.script shouldBe "docker --version"
                
                val kubectlStep = steps[2] as Step.Shell
                kubectlStep.script shouldBe "kubectl version"
                
                steps[3] shouldBe Step.echo("Tools verified")
                
                val helmStep = steps[4] as Step.Shell
                helmStep.script shouldBe "helm version"
                
                steps[5] shouldBe Step.echo("Workflow complete")
            }
        }
        
        `when`("testing readability improvements") {
            val readablePipeline = pipelineWithContextReceivers {
                stage("setup") {
                    echo("Setting up environment")
                    sh("export NODE_ENV=production")
                    sh("npm ci")
                }
                
                stage("quality-checks") {
                    echo("Running quality checks")
                    sh("npm run lint")
                    sh("npm run test:unit")
                    sh("npm run test:integration")
                }
                
                stage("security-scan") {
                    echo("Running security scan")
                    sh("npm audit")
                    sh("docker scan .")
                }
            }
            
            then("should maintain clarity and structure") {
                readablePipeline.stages shouldHaveSize 3
                
                val setupStage = readablePipeline.stages[0]
                setupStage.name shouldBe "setup"
                setupStage.steps shouldHaveSize 3
                
                val qualityStage = readablePipeline.stages[1]
                qualityStage.name shouldBe "quality-checks"
                qualityStage.steps shouldHaveSize 4
                
                val securityStage = readablePipeline.stages[2]
                securityStage.name shouldBe "security-scan"
                securityStage.steps shouldHaveSize 3
                
                // Verify first step of each stage is an echo
                setupStage.steps[0] shouldBe Step.echo("Setting up environment")
                qualityStage.steps[0] shouldBe Step.echo("Running quality checks")
                securityStage.steps[0] shouldBe Step.echo("Running security scan")
            }
        }
    }
    
    given("DSL backward compatibility") {
        
        `when`("mixing old and new syntax in same project") {
            val oldStylePipeline = pipeline {
                stage("old-style") {
                    steps {
                        echo("Old style still works")
                        sh("echo 'Backward compatible'")
                    }
                }
            }
            
            val newStylePipeline = pipelineWithContextReceivers {
                stage("new-style") {
                    echo("New style is available")
                    sh("echo 'Forward compatible'")
                }
            }
            
            then("both syntaxes should coexist") {
                oldStylePipeline shouldNotBe null
                newStylePipeline shouldNotBe null
                
                oldStylePipeline.stages shouldHaveSize 1
                newStylePipeline.stages shouldHaveSize 1
                
                // Both produce valid pipeline structures
                oldStylePipeline.stages.first().steps shouldHaveSize 2
                newStylePipeline.stages.first().steps shouldHaveSize 2
            }
        }
    }
})