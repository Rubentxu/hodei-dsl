package dev.rubentxu.hodei.core.domain.model

/**
 * DSL Builder for creating complex WhenCondition expressions
 * with fluent syntax and type-safe functional predicates.
 * 
 * Enables elegant condition building:
 * ```kotlin
 * val condition = whenCondition {
 *     branch("main") and environment("ENV", "prod") and {
 *         it["buildNumber"] as Int > 100
 *     }
 * }
 * ```
 */
public class WhenConditionBuilder {
    
    /**
     * Creates branch condition
     */
    public fun branch(pattern: String): WhenCondition.Branch = WhenCondition.branch(pattern)
    
    /**
     * Creates environment condition
     */
    public fun environment(name: String, value: String): WhenCondition.Environment = 
        WhenCondition.environment(name, value)
    
    /**
     * Creates changeset condition
     */
    public fun changeSet(pattern: String): WhenCondition.ChangeSet = 
        WhenCondition.changeSet(pattern)
    
    /**
     * Creates functional predicate condition
     */
    public fun predicate(predicate: (Map<String, Any>) -> Boolean): WhenCondition.Predicate = 
        WhenCondition.predicate(predicate)
    
    /**
     * Creates NOT condition
     */
    public fun not(condition: WhenCondition): WhenCondition.Not = WhenCondition.not(condition)
    
    /**
     * Infix AND operator for fluent chaining
     */
    public infix fun WhenCondition.and(other: WhenCondition): WhenCondition.And = 
        WhenCondition.and(this, other)
    
    /**
     * Infix OR operator for fluent chaining  
     */
    public infix fun WhenCondition.or(other: WhenCondition): WhenCondition.Or = 
        WhenCondition.or(this, other)
    
    /**
     * Lambda receiver for direct predicate creation
     */
    public operator fun ((Map<String, Any>) -> Boolean).invoke(): WhenCondition.Predicate =
        WhenCondition.predicate(this)
}

/**
 * Entry point for WhenCondition DSL
 * 
 * Usage:
 * ```kotlin
 * val condition = whenCondition {
 *     branch("main") and environment("DEPLOY_ENV", "prod")
 * }
 * ```
 */
public fun whenCondition(builder: WhenConditionBuilder.() -> WhenCondition): WhenCondition =
    WhenConditionBuilder().builder()