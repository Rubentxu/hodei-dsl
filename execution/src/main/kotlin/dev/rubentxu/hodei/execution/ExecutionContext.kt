package dev.rubentxu.hodei.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Execution context for pipeline runs
 * 
 * Provides execution environment, configuration, and shared state for pipeline execution.
 * Includes metrics collection, environment variables, and execution parameters.
 */
public data class ExecutionContext(
    /**
     * Unique execution ID for tracking
     */
    val executionId: String,
    
    /**
     * Environment variables for execution
     */
    val environment: Map<String, String> = emptyMap(),
    
    /**
     * Execution timeout
     */
    val timeout: Duration = Duration.ofMinutes(30),
    
    /**
     * Working directory for execution
     */
    val workingDirectory: String = System.getProperty("user.dir"),
    
    /**
     * Parallel execution configuration
     */
    val parallelism: Int = Runtime.getRuntime().availableProcessors(),
    
    /**
     * Shared context for step execution
     */
    val sharedContext: MutableMap<String, Any> = ConcurrentHashMap()
) {
    
    /**
     * Creates a coroutine scope for execution
     */
    public fun createScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    
    public companion object {
        /**
         * Creates default execution context
         */
        public fun default(): ExecutionContext = ExecutionContext(
            executionId = java.util.UUID.randomUUID().toString()
        )
        
        /**
         * Creates execution context with custom environment
         */
        public fun withEnvironment(env: Map<String, String>): ExecutionContext = ExecutionContext(
            executionId = java.util.UUID.randomUUID().toString(),
            environment = env
        )
    }
}