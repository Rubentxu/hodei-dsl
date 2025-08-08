package dev.rubentxu.hodei.execution

import dev.rubentxu.hodei.core.domain.model.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Advanced BDD Specification for PipelineExecutor
 * 
 * Tests parallel execution, retry logic, and timeout handling
 * using structured concurrency and advanced Pipeline DSL features.
 */
public class AdvancedPipelineExecutorSpec : BehaviorSpec({
    
    given("a PipelineExecutor with parallel execution capabilities") {
        val executor = PipelineExecutor()
        val context = ExecutionContext.default()
        
        `when`("executing a pipeline with parallel branches") {
            then("should execute branches concurrently") {
                val pipeline = Pipeline.builder()
                    .stage("Build") {
                        steps {
                            sh("echo 'Building...'")
                        }
                    }
                    .stage("Test") {
                        steps {
                            parallel {
                                branch("Unit Tests") {
                                    sh("sleep 1")
                                    sh("echo 'Unit tests completed'")
                                }
                                branch("Integration Tests") {
                                    sh("sleep 1") 
                                    sh("echo 'Integration tests completed'")
                                }
                                branch("E2E Tests") {
                                    sh("sleep 1")
                                    sh("echo 'E2E tests completed'")
                                }
                            }
                        }
                    }
                    .build()
                
                val startTime = System.currentTimeMillis()
                val result = runBlocking { executor.execute(pipeline, context) }
                val totalTime = System.currentTimeMillis() - startTime
                
                result.status shouldBe PipelineStatus.SUCCESS
                result.stages shouldHaveSize 2
                
                val testStage = result.stages[1]
                testStage.stageName shouldBe "Test"
                testStage.status shouldBe StageStatus.SUCCESS
                
                // Parallel execution should be faster than sequential (3 * 1s = 3s)
                // With parallelism, should complete in ~1s + overhead
                totalTime shouldBeGreaterThan 1000L    // At least 1 second
                totalTime shouldBeLessThan 2500L       // But less than 2.5 seconds (parallel benefit)
            }
        }
        
        `when`("executing steps with retry logic") {
            then("should retry failed steps according to configuration") {
                val pipeline = Pipeline.builder()
                    .stage("Flaky Stage") {
                        steps {
                            retry(3) {
                                sh("exit 1") // This will fail
                            }
                        }
                    }
                    .build()
                
                val result = runBlocking { executor.execute(pipeline, context) }
                
                // Should eventually fail after 3 retries
                result.status shouldBe PipelineStatus.FAILURE
                result.stages shouldHaveSize 1
                
                val flakyStage = result.stages[0]
                flakyStage.stageName shouldBe "Flaky Stage"
                flakyStage.status shouldBe StageStatus.FAILURE
            }
        }
        
        `when`("executing steps with timeout") {
            then("should timeout and cancel long-running steps") {
                val pipeline = Pipeline.builder()
                    .stage("Slow Stage") {
                        steps {
                            timeout(2.seconds) {
                                sh("sleep 5") // This will timeout
                            }
                        }
                    }
                    .build()
                
                val startTime = System.currentTimeMillis()
                val result = runBlocking { executor.execute(pipeline, context) }
                val totalTime = System.currentTimeMillis() - startTime
                
                result.status shouldBe PipelineStatus.FAILURE
                result.stages shouldHaveSize 1
                
                val slowStage = result.stages[0]
                slowStage.stageName shouldBe "Slow Stage"
                slowStage.status shouldBe StageStatus.FAILURE
                
                // Should complete in ~2 seconds due to timeout, not 5
                totalTime shouldBeGreaterThan 1900L    // At least ~2 seconds
                totalTime shouldBeLessThan 3000L       // But less than 3 seconds
            }
        }
        
        `when`("combining parallel execution with retry and timeout") {
            then("should handle complex execution patterns") {
                val pipeline = Pipeline.builder()
                    .stage("Complex Stage") {
                        steps {
                            parallel {
                                branch("Stable Branch") {
                                    sh("echo 'This will succeed'")
                                }
                                branch("Retry Branch") {
                                    retry(2) {
                                        sh("echo 'Retry attempt'")
                                    }
                                }
                                branch("Timeout Branch") {
                                    timeout(3.seconds) {
                                        sh("sleep 1")
                                        sh("echo 'Within timeout'")
                                    }
                                }
                            }
                        }
                    }
                    .build()
                
                val result = runBlocking { executor.execute(pipeline, context) }
                
                result.status shouldBe PipelineStatus.SUCCESS
                result.stages shouldHaveSize 1
                result.executionId shouldNotBe null
                
                val complexStage = result.stages[0]
                complexStage.stageName shouldBe "Complex Stage"
                complexStage.status shouldBe StageStatus.SUCCESS
            }
        }
    }
})