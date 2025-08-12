package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registry Pattern for managing step handlers
 * 
 * Provides centralized management of step handlers while maintaining
 * the Open/Closed Principle - new step types can be added without
 * modifying existing code.
 */
public object StepHandlerRegistry {
    
    private val handlers = ConcurrentHashMap<KClass<out Step>, StepHandler<*>>()
    
    /**
     * Registers a handler for a specific step type
     * 
     * @param stepClass The step class type
     * @param handler The handler for this step type
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Step> register(stepClass: KClass<T>, handler: StepHandler<T>) {
        handlers[stepClass] = handler
    }
    
    /**
     * Inline function for easier registration with reified types
     */
    public inline fun <reified T : Step> register(handler: StepHandler<T>) {
        register(T::class, handler)
    }
    
    /**
     * Gets a handler for a specific step type
     * 
     * @param stepClass The step class type
     * @return The handler for this step type
     * @throws IllegalArgumentException if no handler is registered
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Step> getHandler(stepClass: KClass<T>): StepHandler<T> {
        val handler = handlers[stepClass] as? StepHandler<T>
            ?: throw IllegalArgumentException("No handler registered for step type: ${stepClass.simpleName}")
        return handler
    }
    
    /**
     * Gets a handler for a step instance
     * 
     * @param step The step instance
     * @return The handler for this step type
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Step> getHandler(step: T): StepHandler<T> {
        return getHandler(step::class as KClass<T>)
    }
    
    /**
     * Checks if a handler is registered for a step type
     * 
     * @param stepClass The step class type
     * @return true if handler is registered
     */
    public fun hasHandler(stepClass: KClass<out Step>): Boolean {
        return handlers.containsKey(stepClass)
    }
    
    /**
     * Unregisters a handler for a step type
     * 
     * @param stepClass The step class type
     */
    public fun unregister(stepClass: KClass<out Step>) {
        handlers.remove(stepClass)
    }
    
    /**
     * Gets all registered step types
     * 
     * @return Set of registered step class types
     */
    public fun getRegisteredTypes(): Set<KClass<out Step>> {
        return handlers.keys.toSet()
    }
    
    /**
     * Clears all registered handlers (primarily for testing)
     */
    public fun clear() {
        handlers.clear()
    }
}