package dev.rubentxu.hodei.core.dsl.extensions

import dev.rubentxu.hodei.core.dsl.builders.StepsBuilder

/**
 * Contract implemented by third-party step extensions.
 *
 * Each extension registers under a unique [name] and can create its own
 * strongly-typed DSL scope object. The core will pass that scope to [execute]
 * together with the active [StepsBuilder] so the extension can add concrete steps.
 */
public interface StepExtension {
    /** Unique extension name used in the DSL (e.g., "slack", "docker"). */
    public val name: String

    /** Create a new, plugin-specific scope instance that the DSL will configure. */
    public fun createScope(): Any

    /**
     * Execute the extension implementation using the configured [scope] and the receiver [stepsBuilder].
     * Implementations should safely cast [scope] to their specific scope type.
     */
    public fun execute(scope: Any, stepsBuilder: StepsBuilder)
}
