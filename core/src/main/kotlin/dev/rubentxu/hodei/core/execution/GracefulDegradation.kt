package dev.rubentxu.hodei.core.execution

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Graceful degradation manager
 * 
 * Provides automatic degradation of service when system is under stress,
 * including load shedding, priority-based execution, and resource throttling.
 */
public class GracefulDegradationManager(
    private val config: GracefulDegradationConfig = GracefulDegradationConfig.default()
) {
    
    private val activeExecutions = AtomicInteger(0)
    private val lastExecutionTime = AtomicLong(System.currentTimeMillis())
    private val errorCount = AtomicInteger(0)
    
    /**
     * Determines if execution should be allowed based on current system state
     */
    public suspend fun shouldExecute(priority: ExecutionPriority = ExecutionPriority.NORMAL): Boolean {
        val currentLoad = getCurrentLoad()
        val currentTime = System.currentTimeMillis()
        
        // Check load-based degradation
        if (currentLoad > config.maxLoadThreshold) {
            return when (priority) {
                ExecutionPriority.CRITICAL -> true
                ExecutionPriority.HIGH -> currentLoad < config.maxLoadThreshold * 1.2
                ExecutionPriority.NORMAL -> false
                ExecutionPriority.LOW -> false
            }
        }
        
        // Check error rate degradation
        val errorRate = calculateErrorRate(currentTime)
        if (errorRate > config.maxErrorRateThreshold) {
            return priority == ExecutionPriority.CRITICAL
        }
        
        return true
    }
    
    /**
     * Records the start of an execution
     */
    public fun recordExecutionStart() {
        activeExecutions.incrementAndGet()
        lastExecutionTime.set(System.currentTimeMillis())
    }
    
    /**
     * Records the completion of an execution
     */
    public fun recordExecutionCompletion(success: Boolean) {
        activeExecutions.decrementAndGet()
        if (!success) {
            errorCount.incrementAndGet()
        }
    }
    
    /**
     * Gets current system load (0.0 to 1.0+)
     */
    private fun getCurrentLoad(): Double {
        val active = activeExecutions.get()
        return active.toDouble() / config.maxConcurrentExecutions
    }
    
    /**
     * Calculates current error rate
     */
    private fun calculateErrorRate(currentTime: Long): Double {
        val timeSinceLastExecution = currentTime - lastExecutionTime.get()
        if (timeSinceLastExecution > config.errorRateWindowMs) {
            errorCount.set(0)
            return 0.0
        }
        
        val errors = errorCount.get()
        val active = activeExecutions.get()
        return if (active > 0) errors.toDouble() / active else 0.0
    }
    
    /**
     * Creates a degradation-aware execution wrapper
     */
    public suspend fun <T> executeWithDegradation(
        priority: ExecutionPriority = ExecutionPriority.NORMAL,
        fallback: (suspend () -> T)? = null,
        block: suspend () -> T
    ): T {
        if (!shouldExecute(priority)) {
            if (fallback != null) {
                return fallback()
            } else {
                throw SystemOverloadException("System is overloaded, execution rejected")
            }
        }
        
        recordExecutionStart()
        return try {
            val result = block()
            recordExecutionCompletion(true)
            result
        } catch (e: Exception) {
            recordExecutionCompletion(false)
            throw e
        }
    }
    
    /**
     * Gets current system health metrics
     */
    public fun getHealthMetrics(): HealthMetrics {
        val currentTime = System.currentTimeMillis()
        return HealthMetrics(
            currentLoad = getCurrentLoad(),
            errorRate = calculateErrorRate(currentTime),
            activeExecutions = activeExecutions.get(),
            isHealthy = getCurrentLoad() < config.maxLoadThreshold && 
                       calculateErrorRate(currentTime) < config.maxErrorRateThreshold
        )
    }
}

/**
 * Execution priority levels
 */
public enum class ExecutionPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * Configuration for graceful degradation
 */
public data class GracefulDegradationConfig(
    /**
     * Maximum concurrent executions before load shedding
     */
    val maxConcurrentExecutions: Int = 100,
    
    /**
     * Load threshold (0.0 to 1.0) before degradation kicks in
     */
    val maxLoadThreshold: Double = 0.8,
    
    /**
     * Error rate threshold (0.0 to 1.0) before degradation
     */
    val maxErrorRateThreshold: Double = 0.1,
    
    /**
     * Time window for error rate calculation (milliseconds)
     */
    val errorRateWindowMs: Long = 60_000 // 1 minute
) {
    
    init {
        require(maxConcurrentExecutions > 0) { "Max concurrent executions must be positive" }
        require(maxLoadThreshold in 0.0..1.0) { "Load threshold must be between 0.0 and 1.0" }
        require(maxErrorRateThreshold in 0.0..1.0) { "Error rate threshold must be between 0.0 and 1.0" }
        require(errorRateWindowMs > 0) { "Error rate window must be positive" }
    }
    
    public companion object {
        public fun default(): GracefulDegradationConfig = GracefulDegradationConfig()
    }
}

/**
 * System health metrics
 */
public data class HealthMetrics(
    val currentLoad: Double,
    val errorRate: Double,
    val activeExecutions: Int,
    val isHealthy: Boolean
)

/**
 * Exception thrown when system is overloaded
 */
public class SystemOverloadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)