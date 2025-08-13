package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Verification that all complex handlers are properly registered
 */
class ComplexHandlerRegistrationVerificationSpec : StringSpec({
    
    "should have all complex handlers registered after default registration" {
        // Clear registry and re-register
        StepHandlerRegistry.clear()
        DefaultHandlerRegistration.registerDefaultHandlers()
        
        // Verify all complex handlers are registered
        StepHandlerRegistry.hasHandler(Step.Dir::class) shouldBe true
        StepHandlerRegistry.hasHandler(Step.WithEnv::class) shouldBe true
        StepHandlerRegistry.hasHandler(Step.Parallel::class) shouldBe true
        StepHandlerRegistry.hasHandler(Step.Retry::class) shouldBe true
        StepHandlerRegistry.hasHandler(Step.Timeout::class) shouldBe true
        
        // Also verify simple handlers still work
        StepHandlerRegistry.hasHandler(Step.Echo::class) shouldBe true
        StepHandlerRegistry.hasHandler(Step.Shell::class) shouldBe true
    }
    
    "should be able to get all complex handlers" {
        StepHandlerRegistry.clear()
        DefaultHandlerRegistration.registerDefaultHandlers()
        
        val dirHandler = StepHandlerRegistry.getHandler(Step.Dir::class)
        val envHandler = StepHandlerRegistry.getHandler(Step.WithEnv::class)
        val parallelHandler = StepHandlerRegistry.getHandler(Step.Parallel::class)
        val retryHandler = StepHandlerRegistry.getHandler(Step.Retry::class)
        val timeoutHandler = StepHandlerRegistry.getHandler(Step.Timeout::class)
        
        dirHandler::class.simpleName shouldBe "DirStepHandler"
        envHandler::class.simpleName shouldBe "WithEnvStepHandler"
        parallelHandler::class.simpleName shouldBe "ParallelStepHandler"
        retryHandler::class.simpleName shouldBe "RetryStepHandler"
        timeoutHandler::class.simpleName shouldBe "TimeoutStepHandler"
    }
})