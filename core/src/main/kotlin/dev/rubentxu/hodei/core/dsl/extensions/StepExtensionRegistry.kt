package dev.rubentxu.hodei.core.dsl.extensions

import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that discovers [StepExtension]s via ServiceLoader and also allows
 * programmatic registration (useful for tests).
 */
public object StepExtensionRegistry {
    private val dynamicExtensions: MutableMap<String, StepExtension> = ConcurrentHashMap()
    private val loader: ServiceLoader<StepExtension> = ServiceLoader.load(StepExtension::class.java)

    /** Reload extensions discovered by the ServiceLoader. */
    public fun reload() {
        loader.reload()
    }

    /** Register an extension programmatically (takes precedence over ServiceLoader). */
    public fun register(extension: StepExtension) {
        dynamicExtensions[extension.name] = extension
    }

    /** Unregister a previously registered extension. */
    public fun unregister(name: String) {
        dynamicExtensions.remove(name)
    }

    /** Get an extension by name, checking dynamic registrations first, then ServiceLoader. */
    public fun get(name: String): StepExtension? {
        dynamicExtensions[name]?.let { return it }
        return loader.asSequence().firstOrNull { it.name == name }
    }

    /** Return all known extensions (dynamic + discovered). */
    public fun all(): List<StepExtension> {
        val discovered = loader.asSequence().toList()
        val merged = (discovered + dynamicExtensions.values)
        return merged.distinctBy { it.name }
    }
}
