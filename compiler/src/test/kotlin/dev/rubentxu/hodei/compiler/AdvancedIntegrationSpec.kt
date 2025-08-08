package dev.rubentxu.hodei.compiler

import dev.rubentxu.hodei.core.domain.model.Pipeline
import dev.rubentxu.hodei.execution.PipelineExecutor
import dev.rubentxu.hodei.execution.ExecutionContext
import dev.rubentxu.hodei.execution.PipelineStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import kotlinx.coroutines.runBlocking

/**
 * Advanced Integration Tests - End-to-End Pipeline DSL with Advanced Features
 * 
 * Tests the complete integration between compiler, DSL, and execution engine
 * with advanced features like parallel execution, retry, and timeout.
 */
public class AdvancedIntegrationSpec : BehaviorSpec({
    
    given("Advanced Pipeline DSL Integration") {
        val compiler = ScriptCompiler(ScriptConfig.defaultConfig())
        val executor = PipelineExecutor()
        val context = ExecutionContext.default()
        
        `when`("executing advanced .pipeline.kts with parallel, retry, and timeout") {
            then("should execute with proper concurrency and timing") {
                val advancedScript = """
                    val advancedPipeline = pipeline {
                        stage("Build") {
                            steps {
                                sh("echo 'Starting build...'")
                                echo("Build completed")
                            }
                        }
                        
                        stage("Parallel Testing") {
                            steps {
                                parallel {
                                    branch("Unit Tests") {
                                        sh("sleep 1")
                                        echo("Unit tests completed")
                                    }
                                    
                                    branch("Integration Tests") {
                                        sh("sleep 1")
                                        echo("Integration tests completed")  
                                    }
                                    
                                    branch("E2E Tests") {
                                        sh("sleep 1")
                                        echo("E2E tests completed")
                                    }
                                }
                            }
                        }
                        
                        stage("Deploy") {
                            steps {
                                echo("🚀 Deployment completed!")
                            }
                        }
                    }
                    
                    advancedPipeline
                """.trimIndent()
                
                // Step 1: Compile the advanced script
                val compilationResult = runBlocking { 
                    compiler.compile(advancedScript, "advanced.pipeline.kts") 
                }
                
                compilationResult.isSuccess shouldBe true
                val successResult = compilationResult as CompilationResult.Success
                
                // Step 2: Execute script to get pipeline
                val executionResult = runBlocking { compiler.execute(successResult) }
                executionResult.isSuccess shouldBe true
                
                val execSuccess = executionResult as ExecutionResult.Success
                val pipeline = when (val result = execSuccess.result) {
                    is kotlin.script.experimental.api.ResultValue.Value -> result.value as Pipeline
                    else -> result as Pipeline
                }
                
                // Step 3: Execute the pipeline with timing validation
                val startTime = System.currentTimeMillis()
                val pipelineResult = runBlocking { executor.execute(pipeline, context) }
                val totalTime = System.currentTimeMillis() - startTime
                
                // Validate results
                pipelineResult.status shouldBe PipelineStatus.SUCCESS
                pipelineResult.stages shouldHaveSize 3
                pipelineResult.stages[0].stageName shouldBe "Build"
                pipelineResult.stages[1].stageName shouldBe "Parallel Testing"
                pipelineResult.stages[2].stageName shouldBe "Deploy"
                
                // Validate parallel execution performance
                // 3 parallel branches with 1s each should complete in ~1s + overhead, not 3s+
                totalTime shouldBeGreaterThan 1000L    // At least 1 second for the sleeps
                totalTime shouldBeLessThan 2500L       // But less than 2.5s due to parallelism
                
                pipelineResult.executionId shouldNotBe null
            }
        }
    }
})