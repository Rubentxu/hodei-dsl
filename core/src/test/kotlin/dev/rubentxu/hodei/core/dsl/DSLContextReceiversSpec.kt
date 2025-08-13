package dev.rubentxu.hodei.core.dsl

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.dsl.builders.*
import dev.rubentxu.hodei.core.dsl.context.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import kotlin.time.Duration.Companion.seconds

/**
 * Specification for DSL Context Receivers modernization
 * 
 * Tests the enhanced DSL API using Kotlin context receivers for
 * improved fluency and reduced boilerplate in pipeline construction.
 */
class DSLContextReceiversSpec : BehaviorSpec({
    
    given("modernized pipeline DSL with context receivers") {
        
        `when`("using simplified stage syntax with direct step calls") {
            val pipeline = pipelineWithContextReceivers {
                stage("build") {
                    sh("./gradlew build")
                    echo("Build completed")
                }
                
                stage("test") {
                    sh("./gradlew test")
                }
            }
            
            then("should create pipeline with proper structure") {
                pipeline shouldNotBe null
                pipeline.stages shouldHaveSize 2
                
                val buildStage = pipeline.stages.find { it.name == "build" }
                buildStage shouldNotBe null
                buildStage!!.steps shouldHaveSize 2
                
                val testStage = pipeline.stages.find { it.name == "test" }
                testStage shouldNotBe null
                testStage!!.steps shouldHaveSize 1
            }
        }
        
        `when`("mixing traditional and context receiver syntax") {
            val traditionalPipeline = pipeline {
                stage("traditional") {
                    steps {
                        sh("echo traditional")
                    }
                }
            }
            
            val modernPipeline = pipelineWithContextReceivers {
                stage("modern") {
                    sh("echo modern")
                }
            }
            
            then("both approaches should work") {
                traditionalPipeline.stages shouldHaveSize 1
                modernPipeline.stages shouldHaveSize 1
                
                traditionalPipeline.stages.first().name shouldBe "traditional"
                modernPipeline.stages.first().name shouldBe "modern"
            }
        }
    }
    
    given("enhanced step builder with context receivers") {
        
        `when`("creating steps with direct method calls") {
            val steps = buildStepsWithContextReceivers {
                sh("echo 'Starting deployment'")
                echo("Deployment completed")
            }
            
            then("should create proper step list") {
                steps shouldHaveSize 2
                
                val shellStep = steps[0] as Step.Shell
                shellStep.script shouldBe "echo 'Starting deployment'"
                
                val echoStep = steps[1] as Step.Echo
                echoStep.message shouldBe "Deployment completed"
            }
        }
    }
})