package dev.rubentxu.hodei.core.dsl.builders

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.domain.model.WhenCondition

/**
 * DSL Builder for When conditions
 * 
 * Provides fluent API for creating conditional logic to determine
 * when stages should execute. Supports Jenkins when condition patterns.
 */
@PipelineDSL
public class WhenBuilder {
    private var condition: WhenCondition? = null
    
    /**
     * Check if current branch matches pattern
     * @param pattern Branch name or pattern (supports wildcards)
     */
    public fun branch(pattern: String) {
        this.condition = WhenCondition.branch(pattern)
    }
    
    /**
     * Check environment variable value
     * @param name Environment variable name
     * @param value Expected value
     */
    public fun environment(name: String, value: String) {
        this.condition = WhenCondition.environment(name, value)
    }
    
    /**
     * Use custom expression for complex logic
     * @param expression Boolean expression string
     */
    public fun expression(expression: String) {
        this.condition = WhenCondition.expression(expression)
    }
    
    /**
     * Combine multiple conditions with AND logic
     * @param block Configuration block for nested conditions
     */
    public fun allOf(block: AllOfBuilder.() -> Unit) {
        val builder = AllOfBuilder()
        builder.apply(block)
        this.condition = builder.build()
    }
    
    /**
     * Combine multiple conditions with OR logic  
     * @param block Configuration block for nested conditions
     */
    public fun anyOf(block: AnyOfBuilder.() -> Unit) {
        val builder = AnyOfBuilder()
        builder.apply(block)
        this.condition = builder.build()
    }
    
    /**
     * Negate a condition
     * @param block Configuration block for condition to negate
     */
    public fun not(block: WhenBuilder.() -> Unit) {
        val builder = WhenBuilder()
        builder.apply(block)
        val innerCondition = builder.build()
        this.condition = WhenCondition.not(innerCondition)
    }
    
    internal fun build(): WhenCondition = condition ?: WhenCondition.expression("true")
}

/**
 * Builder for AND logic conditions
 */
@PipelineDSL
public class AllOfBuilder {
    private val conditions: MutableList<WhenCondition> = mutableListOf()
    
    /**
     * Add branch condition
     */
    public fun branch(pattern: String) {
        conditions.add(WhenCondition.branch(pattern))
    }
    
    /**
     * Add environment condition
     */
    public fun environment(name: String, value: String) {
        conditions.add(WhenCondition.environment(name, value))
    }
    
    /**
     * Add expression condition
     */
    public fun expression(expression: String) {
        conditions.add(WhenCondition.expression(expression))
    }
    
    internal fun build(): WhenCondition.And = WhenCondition.and(conditions)
}

/**
 * Builder for OR logic conditions  
 */
@PipelineDSL
public class AnyOfBuilder {
    private val conditions: MutableList<WhenCondition> = mutableListOf()
    
    /**
     * Add branch condition
     */
    public fun branch(pattern: String) {
        conditions.add(WhenCondition.branch(pattern))
    }
    
    /**
     * Add environment condition
     */
    public fun environment(name: String, value: String) {
        conditions.add(WhenCondition.environment(name, value))
    }
    
    /**
     * Add expression condition
     */
    public fun expression(expression: String) {
        conditions.add(WhenCondition.expression(expression))
    }
    
    internal fun build(): WhenCondition.Or = WhenCondition.or(conditions)
}