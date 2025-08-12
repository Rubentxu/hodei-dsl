package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Test that all simple step handlers are properly registered
 */
class HandlerRegistrationSpec : BehaviorSpec({
    
    given("DefaultHandlerRegistration") {
        `when`("registering all default handlers") {
            DefaultHandlerRegistration.registerDefaultHandlers()
            
            then("should have all simple handlers registered") {
                StepHandlerRegistry.hasHandler(Step.Echo::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.Shell::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.ArchiveArtifacts::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.PublishTestResults::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.Stash::class) shouldBe true
                StepHandlerRegistry.hasHandler(Step.Unstash::class) shouldBe true
            }
            
            then("should report handlers as registered") {
                DefaultHandlerRegistration.areDefaultHandlersRegistered() shouldBe true
            }
        }
    }
})