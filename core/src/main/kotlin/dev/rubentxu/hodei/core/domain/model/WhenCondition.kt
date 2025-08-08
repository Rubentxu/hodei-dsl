package dev.rubentxu.hodei.core.domain.model

/**
 * When condition sealed class hierarchy
 * 
 * Defines conditional logic for stage execution,
 * supporting various condition types compatible with Jenkins.
 */
public sealed class WhenCondition {
    
    /**
     * Evaluates this condition against the given context
     * @param context Execution context containing variables and state
     * @return true if the condition is satisfied
     */
    public abstract fun evaluate(context: Map<String, Any>): Boolean
    
    /**
     * Branch condition
     */
    public data class Branch(
        val pattern: String
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            val currentBranch = context["BRANCH_NAME"] as? String ?: return false
            return currentBranch.matches(pattern.toRegex())
        }
    }
    
    /**
     * Environment variable condition
     */
    public data class Environment(
        val name: String,
        val value: String
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            val envValue = context[name] as? String ?: return false
            return envValue == value
        }
    }
    
    /**
     * Custom expression condition
     */
    public data class Expression(
        val expression: String
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            // TODO: Implement expression evaluation engine
            // For now, return true as a placeholder
            return true
        }
    }
    
    /**
     * Change detection condition
     */
    public data class ChangeSet(
        val pattern: String
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            val changedFiles = context["CHANGED_FILES"] as? List<String> ?: return false
            val regex = pattern.toRegex()
            return changedFiles.any { file -> regex.containsMatchIn(file) }
        }
    }
    
    /**
     * Combined AND condition
     */
    public data class And(
        val conditions: List<WhenCondition>
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean =
            conditions.all { it.evaluate(context) }
    }
    
    /**
     * Combined OR condition
     */
    public data class Or(
        val conditions: List<WhenCondition>
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean =
            conditions.any { it.evaluate(context) }
    }
    
    /**
     * Negated condition
     */
    public data class Not(
        val condition: WhenCondition
    ) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean =
            !condition.evaluate(context)
    }
    
    public companion object {
        /**
         * Creates branch condition
         */
        public fun branch(pattern: String): Branch = Branch(pattern)
        
        /**
         * Creates environment condition
         */
        public fun environment(name: String, value: String): Environment = 
            Environment(name, value)
        
        /**
         * Creates expression condition
         */
        public fun expression(expression: String): Expression = Expression(expression)
        
        /**
         * Creates changeset condition
         */
        public fun changeSet(pattern: String): ChangeSet = ChangeSet(pattern)
        
        /**
         * Creates AND condition
         */
        public fun and(vararg conditions: WhenCondition): And = 
            And(conditions.toList())
        
        /**
         * Creates OR condition
         */
        public fun or(vararg conditions: WhenCondition): Or = 
            Or(conditions.toList())
        
        /**
         * Creates NOT condition
         */
        public fun not(condition: WhenCondition): Not = Not(condition)
    }
}