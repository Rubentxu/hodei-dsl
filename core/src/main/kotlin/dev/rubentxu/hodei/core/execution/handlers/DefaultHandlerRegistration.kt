package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step

/**
 * Default handler registration utility
 * 
 * Registers all built-in step handlers with the registry.
 * This follows the Dependency Inversion Principle by allowing
 * handlers to be registered without the main executor knowing
 * about specific implementations.
 */
public object DefaultHandlerRegistration {
    
    /**
     * Registers all default step handlers
     * 
     * This method should be called during application initialization
     * to ensure all built-in step types have handlers available.
     */
    public fun registerDefaultHandlers() {
        // Register simple handlers (FASE 2)
        StepHandlerRegistry.register<Step.Echo>(EchoStepHandler())
        StepHandlerRegistry.register<Step.Shell>(ShellStepHandler())
        StepHandlerRegistry.register<Step.ArchiveArtifacts>(ArchiveArtifactsStepHandler())
        StepHandlerRegistry.register<Step.PublishTestResults>(PublishTestResultsStepHandler())
        StepHandlerRegistry.register<Step.Stash>(StashStepHandler())
        StepHandlerRegistry.register<Step.Unstash>(UnstashStepHandler())
        
        // Register complex handlers (FASE 3)
        StepHandlerRegistry.register<Step.Dir>(DirStepHandler())
        StepHandlerRegistry.register<Step.WithEnv>(WithEnvStepHandler())
        StepHandlerRegistry.register<Step.Parallel>(ParallelStepHandler())
        StepHandlerRegistry.register<Step.Retry>(RetryStepHandler())
        StepHandlerRegistry.register<Step.Timeout>(TimeoutStepHandler())
    }
    
    /**
     * Checks if all default handlers are registered
     * 
     * @return true if all expected handlers are registered
     */
    public fun areDefaultHandlersRegistered(): Boolean {
        val expectedHandlers = setOf(
            Step.Echo::class,
            Step.Shell::class,
            Step.ArchiveArtifacts::class,
            Step.PublishTestResults::class,
            Step.Stash::class,
            Step.Unstash::class,
            // Complex handlers (FASE 3)
            Step.Dir::class,
            Step.WithEnv::class,
            Step.Parallel::class,
            Step.Retry::class,
            Step.Timeout::class
        )
        
        return expectedHandlers.all { StepHandlerRegistry.hasHandler(it) }
    }
}