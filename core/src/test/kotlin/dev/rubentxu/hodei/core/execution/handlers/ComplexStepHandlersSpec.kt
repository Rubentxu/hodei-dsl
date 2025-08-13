package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.ExecutionContext
import dev.rubentxu.hodei.core.execution.StepResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Specification for complex step handlers (FASE 3)
 * 
 * Tests the SOLID implementation of complex step handlers including
 * directory operations, environment manipulation, parallel execution,
 * retry logic, and timeout handling.
 */
class ComplexStepHandlersSpec : BehaviorSpec({
    
    given("a DirStepHandler") {
        val handler = DirStepHandler()
        val context = ExecutionContext.default()
        
        `when`("executing a dir step with valid path") {
            val dirStep = Step.Dir(
                path = "test-directory",
                steps = listOf(
                    Step.Echo("Hello from directory")
                )
            )
            
            then("should create directory and execute nested steps") {
                val result = handler.executeWithLifecycle(dirStep, context)
                
                result.isSuccessful shouldBe true
                result.output shouldContain "Executed 1 steps in directory"
                result.metadata shouldContainKey "directory"
                result.metadata shouldContainKey "stepsExecuted"
            }
        }
        
        `when`("executing a dir step with empty path") {
            val dirStep = Step.Dir(
                path = "",
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(dirStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
        
        `when`("executing a dir step with no nested steps") {
            val dirStep = Step.Dir(
                path = "test-dir",
                steps = emptyList()
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(dirStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
    }
    
    given("a WithEnvStepHandler") {
        val handler = WithEnvStepHandler()
        val context = ExecutionContext.default()
        
        `when`("executing a withEnv step with valid environment variables") {
            val envStep = Step.WithEnv(
                environment = listOf("TEST_VAR=test_value", "ANOTHER_VAR=another_value"),
                steps = listOf(
                    Step.Echo("Using environment variables")
                )
            )
            
            then("should set environment and execute nested steps") {
                val result = handler.executeWithLifecycle(envStep, context)
                
                result.isSuccessful shouldBe true
                result.output shouldContain "Executed 1 steps with modified environment"
                result.metadata shouldContainKey "environmentVariables"
                result.metadata shouldContainKey "stepsExecuted"
            }
        }
        
        `when`("executing a withEnv step with invalid environment format") {
            val envStep = Step.WithEnv(
                environment = listOf("INVALID_FORMAT"),
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(envStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
        
        `when`("executing a withEnv step with empty environment") {
            val envStep = Step.WithEnv(
                environment = emptyList(),
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(envStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
    }
    
    given("a ParallelStepHandler") {
        val handler = ParallelStepHandler()
        val context = ExecutionContext.default()
        
        `when`("executing a parallel step with multiple branches") {
            val parallelStep = Step.Parallel(
                branches = mapOf(
                    "branch1" to listOf(Step.Echo("Branch 1 message")),
                    "branch2" to listOf(Step.Echo("Branch 2 message")),
                    "branch3" to listOf(Step.Echo("Branch 3 message"))
                )
            )
            
            then("should execute all branches concurrently") {
                val result = handler.executeWithLifecycle(parallelStep, context)
                
                result.isSuccessful shouldBe true
                result.output shouldContain "Executed 3 branches in parallel"
                result.metadata shouldContainKey "branchCount"
                result.metadata shouldContainKey "totalStepsExecuted"
                result.metadata shouldContainKey "successfulBranches"
            }
        }
        
        `when`("executing a parallel step with empty branches") {
            val parallelStep = Step.Parallel(branches = emptyMap())
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(parallelStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
        
        `when`("executing a parallel step with empty branch steps") {
            val parallelStep = Step.Parallel(
                branches = mapOf("empty-branch" to emptyList())
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(parallelStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
    }
    
    given("a RetryStepHandler") {
        val handler = RetryStepHandler()
        val context = ExecutionContext.default()
        
        `when`("executing a retry step that succeeds on first attempt") {
            val retryStep = Step.Retry(
                times = 3,
                steps = listOf(Step.Echo("Success on first try"))
            )
            
            then("should succeed without retries") {
                val result = handler.executeWithLifecycle(retryStep, context)
                
                result.isSuccessful shouldBe true
                result.output shouldContain "Retry succeeded on attempt 1"
                result.metadata shouldContainKey "successfulAttempt"
                result.metadata shouldContainKey "maxAttempts"
            }
        }
        
        `when`("executing a retry step with invalid retry count") {
            val retryStep = Step.Retry(
                times = 0,
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(retryStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
        
        `when`("executing a retry step with too many retries") {
            val retryStep = Step.Retry(
                times = 15,
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(retryStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
    }
    
    given("a TimeoutStepHandler") {
        val handler = TimeoutStepHandler()
        val context = ExecutionContext.default()
        
        `when`("executing a timeout step that completes within timeout") {
            val timeoutStep = Step.Timeout(
                duration = 5.seconds,
                steps = listOf(Step.Echo("Quick execution"))
            )
            
            then("should complete successfully") {
                val result = handler.executeWithLifecycle(timeoutStep, context)
                
                result.isSuccessful shouldBe true
                result.output shouldContain "Executed 1 steps within timeout"
                result.metadata shouldContainKey "timeoutDuration"
                result.metadata shouldContainKey "timedOut"
                result.metadata?.get("timedOut") shouldBe false
            }
        }
        
        `when`("executing a timeout step with invalid duration") {
            val timeoutStep = Step.Timeout(
                duration = (-1).seconds,
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(timeoutStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
        
        `when`("executing a timeout step with zero duration") {
            val timeoutStep = Step.Timeout(
                duration = 0.milliseconds,
                steps = listOf(Step.Echo("test"))
            )
            
            then("should fail validation") {
                val result = handler.executeWithLifecycle(timeoutStep, context)
                
                result.isSuccessful shouldBe false
                result.error shouldNotBe null
            }
        }
    }
    
    given("handler registration") {
        `when`("registering all complex handlers") {
            StepHandlerRegistry.clear()
            DefaultHandlerRegistration.registerDefaultHandlers()
            
            then("should register all complex step handlers") {
                StepHandlerRegistry.hasHandler(Step.Dir::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.WithEnv::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.Parallel::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.Retry::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.Timeout::class) shouldBe true
            }
            
            then("should be able to get handlers for complex steps") {
                val dirHandler = StepHandlerRegistry.getHandler(Step.Dir::class)
                val envHandler = StepHandlerRegistry.getHandler(Step.WithEnv::class)
                val parallelHandler = StepHandlerRegistry.getHandler(Step.Parallel::class)
                val retryHandler = StepHandlerRegistry.getHandler(Step.Retry::class)
                val timeoutHandler = StepHandlerRegistry.getHandler(Step.Timeout::class)
                
                dirHandler shouldNotBe null
                envHandler shouldNotBe null
                parallelHandler shouldNotBe null
                retryHandler shouldNotBe null
                timeoutHandler shouldNotBe null
            }
        }
    }
})