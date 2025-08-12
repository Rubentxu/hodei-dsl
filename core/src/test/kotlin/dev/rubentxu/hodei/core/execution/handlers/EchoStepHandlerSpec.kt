package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

class EchoStepHandlerSpec : BehaviorSpec({
    
    given("an EchoStepHandler") {
        val handler = EchoStepHandler()
        val context = ExecutionContext.default()
        
        `when`("validating a valid echo step") {
            val step = Step.Echo("Hello world")
            val errors = handler.validate(step, context)
            
            then("should have no validation errors") {
                errors shouldBe emptyList()
            }
        }
        
        `when`("validating an echo step with blank message") {
            val step = Step.Echo("")
            val errors = handler.validate(step, context)
            
            then("should have validation error for required message") {
                errors.size shouldBe 1
                errors.first().field shouldBe "message"
                errors.first().message shouldBe "message is required"
            }
        }
        
        `when`("executing a valid echo step") {
            then("should return successful result") {
                runBlocking {
                    val step = Step.Echo("Test message")
                    val result = handler.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "echo"
                    result.output shouldBe "Test message"
                    result.exitCode shouldBe 0
                    result.error shouldBe null
                }
            }
        }
        
        `when`("executing through StepExecutor with handler") {
            then("should use the new handler system") {
                runBlocking {
                    val stepExecutor = StepExecutor()
                    val step = Step.Echo("Handler test")
                    val result = stepExecutor.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "echo"
                    result.output shouldBe "Handler test"
                    result.exitCode shouldBe 0
                    result.error shouldBe null
                    
                    // Should have enhanced metadata from handler system
                    result.metadata shouldNotBe null
                    result.metadata["dispatcher"] shouldNotBe null
                    result.metadata["thread"] shouldNotBe null
                    result.metadata["launcher"] shouldNotBe null
                }
            }
        }
    }
})