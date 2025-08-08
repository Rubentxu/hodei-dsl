package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * BDD Specification for StageExecutor
 * 
 * Tests stage execution patterns including sequential stages, parallel branches,
 * conditional execution, timeout handling, and resource management.
 * Ensures proper integration with ExecutionContext and step execution.
 */
class StageExecutorSpec : BehaviorSpec({
    
    given("a StageExecutor") {
        val executor = StageExecutor()
        val context = ExecutionContext.default()
        
        `when`("executing a sequential stage") {
            then("should execute all steps in order") {
                val stage = Stage.sequential("Sequential Stage") {
                    steps {
                        sh("echo 'Step 1'")
                        sh("echo 'Step 2'") 
                        sh("echo 'Step 3'")
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.SUCCESS
                result.stageName shouldBe "Sequential Stage"
                result.steps shouldHaveSize 3
                result.steps.all { it.status == StepStatus.SUCCESS } shouldBe true
                result.duration.toMillis() shouldBeGreaterThan 0L
                result.agent shouldBe stage.agent
            }
        }
        
        `when`("executing a sequential stage with failure") {
            then("should stop execution on first failure when fail-fast enabled") {
                val stage = Stage.sequential("Failing Stage") {
                    failFast(true)
                    steps {
                        sh("echo 'Step 1 - Success'")
                        sh("exit 1") // This will fail
                        sh("echo 'Step 3 - Should not execute'")
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.FAILURE
                result.steps shouldHaveSize 2 // Only first 2 steps executed
                result.steps[0].status shouldBe StepStatus.SUCCESS
                result.steps[1].status shouldBe StepStatus.FAILURE
                result.error shouldNotBe null
            }
        }
        
        `when`("executing a sequential stage with failure and no fail-fast") {
            then("should continue execution after failures") {
                val stage = Stage.sequential("Resilient Stage") {
                    failFast(false)
                    steps {
                        sh("echo 'Step 1 - Success'")
                        sh("exit 1") // This will fail but continue
                        sh("echo 'Step 3 - Should execute'")
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.PARTIAL_SUCCESS
                result.steps shouldHaveSize 3 // All steps executed
                result.steps[0].status shouldBe StepStatus.SUCCESS
                result.steps[1].status shouldBe StepStatus.FAILURE
                result.steps[2].status shouldBe StepStatus.SUCCESS
            }
        }
        
        `when`("executing a parallel stage") {
            then("should execute branches concurrently") {
                val stage = Stage.parallel("Parallel Stage") {
                    branch("Branch A") {
                        sh("sleep 0.5")
                        sh("echo 'Branch A completed'")
                    }
                    branch("Branch B") {
                        sh("sleep 0.3")
                        sh("echo 'Branch B completed'")
                    }
                    branch("Branch C") {
                        sh("sleep 0.4")
                        sh("echo 'Branch C completed'")
                    }
                }
                
                val startTime = System.currentTimeMillis()
                val result = executor.execute(stage, context)
                val executionTime = System.currentTimeMillis() - startTime
                
                result.status shouldBe StageStatus.SUCCESS
                result.stageName shouldBe "Parallel Stage"
                result.branches shouldHaveSize 3
                result.branches?.all { it.status == StageStatus.SUCCESS } shouldBe true
                
                // Should complete in roughly the time of the longest branch (0.5s)
                executionTime shouldBeGreaterThan 400L
                executionTime shouldBeLessThan 800L // Allow some overhead
                
                // Verify all branches completed
                result.branches?.forEach { branch ->
                    branch.steps.all { it.status == StepStatus.SUCCESS } shouldBe true
                }
            }
        }
        
        `when`("executing a parallel stage with mixed results") {
            then("should handle partial failures correctly") {
                val stage = Stage.parallel("Mixed Results Stage") {
                    failFast(false)
                    branch("Success Branch") {
                        sh("echo 'Success branch completed'")
                    }
                    branch("Failure Branch") {
                        sh("exit 1") // This will fail
                    }
                    branch("Slow Success Branch") {
                        sh("sleep 0.2")
                        sh("echo 'Slow success branch completed'")
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.PARTIAL_SUCCESS
                result.branches shouldHaveSize 3
                
                val successBranches = result.branches?.count { it.status == StageStatus.SUCCESS }
                val failureBranches = result.branches?.count { it.status == StageStatus.FAILURE }
                
                successBranches shouldBe 2
                failureBranches shouldBe 1
            }
        }
        
        `when`("executing a parallel stage with fail-fast") {
            then("should cancel other branches on first failure") {
                val stage = Stage.parallel("Fail Fast Stage") {
                    failFast(true)
                    branch("Quick Failure") {
                        sh("sleep 0.1")
                        sh("exit 1") // Fail quickly
                    }
                    branch("Long Running") {
                        sh("sleep 2") // This should be cancelled
                        sh("echo 'Should not complete'")
                    }
                    branch("Another Long Running") {
                        sh("sleep 2") // This should also be cancelled
                        sh("echo 'Should not complete either'")
                    }
                }
                
                val startTime = System.currentTimeMillis()
                val result = executor.execute(stage, context)
                val executionTime = System.currentTimeMillis() - startTime
                
                result.status shouldBe StageStatus.FAILURE
                // Should fail quickly, not wait for long-running branches
                executionTime shouldBeLessThan 1000L
                
                result.branches?.any { it.status == StageStatus.FAILURE } shouldBe true
                result.branches?.any { it.status == StageStatus.CANCELLED } shouldBe true
            }
        }
        
        `when`("executing a conditional stage") {
            then("should evaluate when condition correctly") {
                val trueConditionStage = Stage.conditional("True Condition Stage") {
                    `when` {
                        branch("main") // Assume we're on main branch
                    }
                    steps {
                        sh("echo 'Condition was true'")
                    }
                }
                
                val falseConditionStage = Stage.conditional("False Condition Stage") {
                    `when` {
                        branch("develop") // Assume we're NOT on develop branch
                    }
                    steps {
                        sh("echo 'This should not execute'")
                    }
                }
                
                val contextWithMainBranch = context.copy(
                    environment = context.environment + ("GIT_BRANCH" to "main")
                )
                
                val trueResult = executor.execute(trueConditionStage, contextWithMainBranch)
                val falseResult = executor.execute(falseConditionStage, contextWithMainBranch)
                
                trueResult.status shouldBe StageStatus.SUCCESS
                trueResult.steps shouldHaveSize 1
                
                falseResult.status shouldBe StageStatus.SKIPPED
                falseResult.steps shouldHaveSize 0
            }
        }
        
        `when`("executing a stage with complex when conditions") {
            then("should evaluate AND/OR/NOT conditions correctly") {
                val complexConditionStage = Stage.conditional("Complex Condition Stage") {
                    `when` {
                        anyOf(
                            branch("main"),
                            allOf(
                                branch("develop"),
                                environment("DEPLOY", "true")
                            )
                        )
                    }
                    steps {
                        sh("echo 'Complex condition matched'")
                    }
                }
                
                // Test with main branch (should match)
                val mainContext = context.copy(
                    environment = context.environment + ("GIT_BRANCH" to "main")
                )
                val mainResult = executor.execute(complexConditionStage, mainContext)
                
                // Test with develop + DEPLOY=true (should match)
                val deployContext = context.copy(
                    environment = context.environment + mapOf(
                        "GIT_BRANCH" to "develop",
                        "DEPLOY" to "true"
                    )
                )
                val deployResult = executor.execute(complexConditionStage, deployContext)
                
                // Test with develop only (should not match)
                val developContext = context.copy(
                    environment = context.environment + ("GIT_BRANCH" to "develop")
                )
                val developResult = executor.execute(complexConditionStage, developContext)
                
                mainResult.status shouldBe StageStatus.SUCCESS
                deployResult.status shouldBe StageStatus.SUCCESS
                developResult.status shouldBe StageStatus.SKIPPED
            }
        }
        
        `when`("executing a stage with timeout") {
            then("should timeout and cancel execution") {
                val stage = Stage.sequential("Timeout Stage") {
                    timeout(300.milliseconds)
                    steps {
                        sh("echo 'Starting long task'")
                        sh("sleep 1") // This will exceed timeout
                        sh("echo 'This should not complete'")
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.TIMEOUT
                result.error.shouldBeInstanceOf<StageTimeoutException>()
                result.duration.toMillis() shouldBeLessThan 600L // Should timeout around 300ms
            }
        }
        
        `when`("executing a stage with different agents") {
            then("should configure execution environment appropriately") {
                val dockerStage = Stage.sequential("Docker Stage") {
                    agent {
                        docker {
                            image("ubuntu:20.04")
                            args("-v", "/tmp:/tmp")
                        }
                    }
                    steps {
                        sh("echo 'Running in Docker'")
                        sh("cat /etc/os-release")
                    }
                }
                
                val kubernetesStage = Stage.sequential("Kubernetes Stage") {
                    agent {
                        kubernetes {
                            yaml("""
                                spec:
                                  containers:
                                  - name: worker
                                    image: ubuntu:20.04
                            """.trimIndent())
                        }
                    }
                    steps {
                        sh("echo 'Running in Kubernetes'")
                    }
                }
                
                val dockerResult = executor.execute(dockerStage, context)
                val k8sResult = executor.execute(kubernetesStage, context)
                
                dockerResult.status shouldBe StageStatus.SUCCESS
                dockerResult.agent.shouldBeInstanceOf<DockerAgent>()
                
                k8sResult.status shouldBe StageStatus.SUCCESS
                k8sResult.agent.shouldBeInstanceOf<KubernetesAgent>()
            }
        }
        
        `when`("executing stage with environment variables") {
            then("should pass environment context to steps") {
                val stage = Stage.sequential("Environment Stage") {
                    environment {
                        set("STAGE_VAR", "stage_value")
                        set("OVERRIDE_VAR", "overridden")
                    }
                    steps {
                        sh("echo \"STAGE_VAR=\$STAGE_VAR\"")
                        sh("echo \"OVERRIDE_VAR=\$OVERRIDE_VAR\"")
                    }
                }
                
                val contextWithEnv = context.copy(
                    environment = context.environment + ("OVERRIDE_VAR" to "original")
                )
                
                val result = executor.execute(stage, contextWithEnv)
                
                result.status shouldBe StageStatus.SUCCESS
                // Verify that stage environment was merged with context
                result.metadata["stageEnvironment"] shouldNotBe null
            }
        }
        
        `when`("stage execution is cancelled") {
            then("should perform proper cleanup") {
                val stage = Stage.sequential("Cancellable Stage") {
                    steps {
                        sh("echo 'Step 1'")
                        sh("sleep 5") // Long running step
                        sh("echo 'Step 3 - should not execute'")
                    }
                }
                
                runBlocking {
                    val job = kotlinx.coroutines.async {
                        executor.execute(stage, context)
                    }
                    
                    delay(100) // Let execution start
                    job.cancel()
                    
                    shouldThrow<kotlinx.coroutines.CancellationException> {
                        job.await()
                    }
                }
            }
        }
    }
    
    given("a StageExecutor with resource management") {
        `when`("stage uses temporary resources") {
            then("should clean up resources after execution") {
                val stage = Stage.sequential("Resource Stage") {
                    steps {
                        sh("mkdir -p /tmp/test-stage-$$")
                        sh("echo 'data' > /tmp/test-stage-$$/file.txt")
                        sh("cat /tmp/test-stage-$$/file.txt")
                    }
                    post {
                        always {
                            sh("rm -rf /tmp/test-stage-$$")
                        }
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.SUCCESS
                // Post actions should have been executed
                result.metadata["postActionsExecuted"] shouldBe true
            }
        }
        
        `when`("stage execution fails") {
            then("should still execute cleanup in post section") {
                val stage = Stage.sequential("Cleanup Stage") {
                    steps {
                        sh("echo 'Creating resources'")
                        sh("mkdir -p /tmp/test-cleanup")
                        sh("exit 1") // Fail intentionally
                    }
                    post {
                        always {
                            sh("rm -rf /tmp/test-cleanup")
                            sh("echo 'Cleanup completed'")
                        }
                        failure {
                            sh("echo 'Stage failed, additional cleanup'")
                        }
                    }
                }
                
                val result = executor.execute(stage, context)
                
                result.status shouldBe StageStatus.FAILURE
                result.metadata["postActionsExecuted"] shouldBe true
                result.metadata["failureActionsExecuted"] shouldBe true
            }
        }
    }
})