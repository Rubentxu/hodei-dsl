package dev.rubentxu.hodei.compiler

import dev.rubentxu.hodei.core.domain.model.Pipeline
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * End-to-End Integration Tests
 * 
 * Tests complete integration between compiler and Pipeline DSL
 * using real .pipeline.kts scripts.
 */
public class EndToEndIntegrationSpec : BehaviorSpec({
    
    given("Pipeline DSL script compilation") {
        val compiler = ScriptCompiler(ScriptConfig.defaultConfig())
        
        `when`("compiling a .pipeline.kts script with Pipeline DSL") {
            then("should compile and execute DSL successfully") {
                val scriptContent = """
                    val myPipeline = pipeline {
                        stage("Build") {
                            steps {
                                sh("echo 'Building project...'")
                                echo("Build stage completed")
                            }
                        }
                        
                        stage("Test") {
                            steps {
                                sh("echo 'Running tests...'")
                                echo("Test stage completed")
                            }
                        }
                    }
                    
                    // Return the pipeline for verification
                    myPipeline
                """.trimIndent()
                
                // Step 1: Compile the script
                val compilationResult = runBlocking { 
                    compiler.compile(scriptContent, "integration-test.pipeline.kts") 
                }
                
                compilationResult.isSuccess shouldBe true
                val successResult = compilationResult as CompilationResult.Success
                successResult.scriptName shouldBe "integration-test.pipeline.kts"
                successResult.activeImports.contains("dev.rubentxu.hodei.core.dsl.pipeline") shouldBe true
                
                // Step 2: Execute the script to get the pipeline
                val executionResult = runBlocking { compiler.execute(successResult) }
                executionResult.isSuccess shouldBe true
                
                val execSuccess = executionResult as ExecutionResult.Success
                execSuccess.result shouldNotBe null
                
                // Step 3: Extract the pipeline from the script result
                // The result is wrapped in kotlin.script.experimental.api.ResultValue.Value
                val pipeline = when (val result = execSuccess.result) {
                    is kotlin.script.experimental.api.ResultValue.Value -> result.value as Pipeline
                    else -> result as Pipeline
                }
                pipeline.stages.size shouldBe 2
                pipeline.stages[0].name shouldBe "Build"
                pipeline.stages[1].name shouldBe "Test"
                pipeline.id shouldNotBe null
            }
        }
    }
})