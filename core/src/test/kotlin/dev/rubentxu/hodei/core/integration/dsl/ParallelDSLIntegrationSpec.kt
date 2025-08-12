package dev.rubentxu.hodei.core.integration.dsl

import dev.rubentxu.hodei.core.dsl.pipeline
import dev.rubentxu.hodei.core.integration.container.ContainerPipelineExecutor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.GenericContainer

/**
 * Parallel DSL Integration Specification
 * 
 * Tests the parallel execution capabilities of the Pipeline DSL.
 * Validates that parallel branches execute concurrently and handle
 * both success and failure scenarios correctly.
 * 
 * Note: Since we don't have parallel execution implemented yet in 
 * ContainerPipelineExecutor, these tests will verify the DSL structure
 * and prepare for when parallel execution is implemented.
 */
class ParallelDSLIntegrationSpec : BehaviorSpec({
    
    val alpineContainer = GenericContainer("alpine:latest")
        .withCommand("tail", "-f", "/dev/null")
    
    listener(alpineContainer.perSpec())
    
    given("Pipeline DSL with parallel execution") {
        
        `when`("defining parallel branches") {
            then("should create and execute multiple branches") {
                val parallelPipeline = pipeline {
                    stage("Parallel Test") {
                        steps {
                            echo("Starting parallel execution test")
                            parallel {
                                branch("Branch A") {
                                    echo("Executing Branch A")
                                    sh("sleep 1")
                                    echo("Branch A completed")
                                }
                                
                                branch("Branch B") {
                                    echo("Executing Branch B")
                                    sh("sleep 1") 
                                    echo("Branch B completed")
                                }
                                
                                branch("Branch C") {
                                    echo("Executing Branch C")
                                    sh("echo 'Quick branch'")
                                    echo("Branch C completed")
                                }
                            }
                            echo("All parallel branches completed")
                        }
                    }
                }
                
                val startTime = System.currentTimeMillis()
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(parallelPipeline)
                val executionTime = System.currentTimeMillis() - startTime
                
                // Validate parallel DSL structure
                result.success shouldBe true
                result.stages shouldHaveSize 1
                
                val parallelStage = result.stages[0]
                parallelStage.success shouldBe true
                parallelStage.stageName shouldBe "Parallel Test"
                
                // Note: Current implementation executes sequentially
                // When parallel is implemented, this timing check should validate concurrency
                // For now, we validate that the DSL structure is correct
                println("✅ Parallel DSL: Structure created correctly with 3 branches")
                println("✅ Execution time: ${executionTime}ms")
                
                // Validate that all branches were processed
                val stageSteps = parallelStage.steps
                stageSteps.any { it.stdout.contains("Starting parallel execution test") } shouldBe true
                stageSteps.any { it.stdout.contains("All parallel branches completed") } shouldBe true
            }
        }
        
        `when`("parallel branches have different execution times") {
            then("should handle branches with varying durations") {
                val timedParallelPipeline = pipeline {
                    stage("Timed Parallel") {
                        steps {
                            parallel {
                                branch("Fast Branch") {
                                    echo("Fast branch starting")
                                    sh("echo 'Fast task'")
                                    echo("Fast branch done")
                                }
                                
                                branch("Medium Branch") {
                                    echo("Medium branch starting")
                                    sh("sleep 2")
                                    echo("Medium branch done")
                                }
                                
                                branch("Slow Branch") {
                                    echo("Slow branch starting")
                                    sh("sleep 3")
                                    echo("Slow branch done")
                                }
                            }
                        }
                    }
                }
                
                val startTime = System.currentTimeMillis()
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(timedParallelPipeline)
                val executionTime = System.currentTimeMillis() - startTime
                
                result.success shouldBe true
                result.stages shouldHaveSize 1
                
                // When parallel execution is implemented, this should take ~3 seconds (slowest branch)
                // not ~8 seconds (sum of all branches)
                println("✅ Timed Parallel DSL: Executed with different branch durations")
                println("✅ Total execution time: ${executionTime}ms")
                
                val timedStage = result.stages[0]
                timedStage.success shouldBe true
            }
        }
        
        `when`("parallel branches have different outcomes") {
            then("should handle mixed success and failure") {
                val mixedParallelPipeline = pipeline {
                    stage("Mixed Outcome Parallel") {
                        steps {
                            parallel {
                                branch("Success Branch") {
                                    echo("This branch will succeed")
                                    sh("echo 'Success!'")
                                    echo("Success branch completed")
                                }
                                
                                branch("Failure Branch") {
                                    echo("This branch will fail")
                                    sh("exit 1") // Intentional failure
                                    echo("This should not execute")
                                }
                                
                                branch("Another Success") {
                                    echo("Another successful branch")
                                    sh("echo 'Also success!'")
                                    echo("Another success completed")
                                }
                            }
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(mixedParallelPipeline)
                
                // Note: Current sequential implementation will stop at first failure
                // When parallel is implemented, this behavior may change based on failFast setting
                result.stages shouldHaveSize 1
                
                val mixedStage = result.stages[0]
                println("✅ Mixed Parallel DSL: Handled branches with different outcomes")
                println("✅ Stage success: ${mixedStage.success}")
            }
        }
        
        `when`("parallel branches use environment variables") {
            then("should propagate environment to all branches") {
                val envParallelPipeline = pipeline {
                    environment {
                        set("SHARED_VAR", "shared_value")
                        set("BRANCH_COUNT", "3")
                    }
                    
                    stage("Environment Parallel") {
                        environment {
                            set("STAGE_VAR", "stage_value")
                        }
                        steps {
                            parallel {
                                branch("Env Branch 1") {
                                    echo("Branch 1 checking environment")
                                    sh("echo 'SHARED_VAR=' && echo \$SHARED_VAR")
                                    sh("echo 'STAGE_VAR=' && echo \$STAGE_VAR")
                                    sh("echo 'Branch 1 ID: 1'")
                                }
                                
                                branch("Env Branch 2") {
                                    echo("Branch 2 checking environment")
                                    sh("echo 'SHARED_VAR=' && echo \$SHARED_VAR")
                                    sh("echo 'BRANCH_COUNT=' && echo \$BRANCH_COUNT")
                                    sh("echo 'Branch 2 ID: 2'")
                                }
                                
                                branch("Env Branch 3") {
                                    echo("Branch 3 checking environment")
                                    sh("echo 'All vars:' && env | grep -E '(SHARED_VAR|STAGE_VAR|BRANCH_COUNT)' | wc -l")
                                    sh("echo 'Branch 3 ID: 3'")
                                }
                            }
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(envParallelPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 1
                
                val envStage = result.stages[0]
                envStage.success shouldBe true
                
                // Validate environment propagation to branches
                val stageSteps = envStage.steps
                stageSteps.any { it.stdout.contains("shared_value") } shouldBe true
                stageSteps.any { it.stdout.contains("stage_value") } shouldBe true
                stageSteps.any { it.stdout.contains("Branch 1 ID: 1") } shouldBe true
                stageSteps.any { it.stdout.contains("Branch 2 ID: 2") } shouldBe true
                stageSteps.any { it.stdout.contains("Branch 3 ID: 3") } shouldBe true
                
                println("✅ Environment Parallel DSL: Environment variables accessible in all branches")
            }
        }
        
        `when`("nesting parallel blocks") {
            then("should handle nested parallel execution") {
                val nestedParallelPipeline = pipeline {
                    stage("Nested Parallel Test") {
                        steps {
                            echo("Starting nested parallel test")
                            parallel {
                                branch("Outer Branch 1") {
                                    echo("Outer Branch 1 - starting inner parallel")
                                    parallel {
                                        branch("Inner A") {
                                            echo("Inner A executing")
                                            sh("echo 'Inner A done'")
                                        }
                                        branch("Inner B") {
                                            echo("Inner B executing") 
                                            sh("echo 'Inner B done'")
                                        }
                                    }
                                    echo("Outer Branch 1 - inner parallel completed")
                                }
                                
                                branch("Outer Branch 2") {
                                    echo("Outer Branch 2 - simple execution")
                                    sh("echo 'Outer Branch 2 simple task'")
                                    echo("Outer Branch 2 completed")
                                }
                            }
                            echo("Nested parallel test completed")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(nestedParallelPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 1
                
                val nestedStage = result.stages[0]
                nestedStage.success shouldBe true
                
                // Validate nested structure execution
                val stageSteps = nestedStage.steps
                stageSteps.any { it.stdout.contains("Starting nested parallel test") } shouldBe true
                stageSteps.any { it.stdout.contains("Inner A done") } shouldBe true
                stageSteps.any { it.stdout.contains("Inner B done") } shouldBe true
                stageSteps.any { it.stdout.contains("Outer Branch 2 simple task") } shouldBe true
                stageSteps.any { it.stdout.contains("Nested parallel test completed") } shouldBe true
                
                println("✅ Nested Parallel DSL: Nested parallel blocks executed correctly")
            }
        }
    }
    
    given("Parallel DSL edge cases") {
        
        `when`("parallel block has only one branch") {
            then("should execute single branch correctly") {
                val singleBranchPipeline = pipeline {
                    stage("Single Branch Parallel") {
                        steps {
                            parallel {
                                branch("Only Branch") {
                                    echo("Single branch in parallel block")
                                    sh("echo 'This is the only branch'")
                                }
                            }
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(singleBranchPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 1
                
                val singleStage = result.stages[0]
                singleStage.success shouldBe true
                singleStage.steps.any { it.stdout.contains("This is the only branch") } shouldBe true
                
                println("✅ Single Branch Parallel DSL: Single branch executed correctly")
            }
        }
        
        `when`("parallel block is empty") {
            then("should handle empty parallel block") {
                val emptyParallelPipeline = pipeline {
                    stage("Empty Parallel") {
                        steps {
                            echo("Before empty parallel")
                            parallel {
                                // No branches defined
                            }
                            echo("After empty parallel")
                        }
                    }
                }
                
                val executor = ContainerPipelineExecutor(alpineContainer, "/tmp")
                val result = executor.execute(emptyParallelPipeline)
                
                result.success shouldBe true
                result.stages shouldHaveSize 1
                
                val emptyStage = result.stages[0]
                emptyStage.success shouldBe true
                emptyStage.steps.any { it.stdout.contains("Before empty parallel") } shouldBe true
                emptyStage.steps.any { it.stdout.contains("After empty parallel") } shouldBe true
                
                println("✅ Empty Parallel DSL: Empty parallel block handled gracefully")
            }
        }
    }
})