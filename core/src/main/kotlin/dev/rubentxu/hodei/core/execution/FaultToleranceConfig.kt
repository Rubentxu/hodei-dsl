package dev.rubentxu.hodei.core.execution

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Fault tolerance configuration
 * 
 * Configures circuit breakers, retry policies, bulkheads, and other
 * fault tolerance patterns used throughout the execution engine.
 */
public data class FaultToleranceConfig(
    /**
     * Circuit breaker configuration
     */
    val circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig.default(),
    
    /**
     * Retry policy configuration
     */
    val retryPolicyConfig: RetryPolicyConfig = RetryPolicyConfig.default(),
    
    /**
     * Bulkhead configuration
     */
    val bulkheadConfig: BulkheadConfig = BulkheadConfig.default(),
    
    /**
     * Enable fault tolerance for pipeline execution
     */
    val enablePipelineFaultTolerance: Boolean = true,
    
    /**
     * Enable fault tolerance for stage execution
     */
    val enableStageFaultTolerance: Boolean = true,
    
    /**
     * Enable fault tolerance for step execution
     */
    val enableStepFaultTolerance: Boolean = false
) {
    
    public companion object {
        /**
         * Creates default fault tolerance configuration
         */
        public fun default(): FaultToleranceConfig = FaultToleranceConfig()
        
        /**
         * Creates disabled fault tolerance configuration
         */
        public fun disabled(): FaultToleranceConfig = FaultToleranceConfig(
            enablePipelineFaultTolerance = false,
            enableStageFaultTolerance = false,
            enableStepFaultTolerance = false
        )
    }
}

/**
 * Circuit breaker configuration
 */
public data class CircuitBreakerConfig(
    /**
     * Number of failures before opening circuit
     */
    val failureThreshold: Int = 5,
    
    /**
     * Time window for failure counting
     */
    val timeoutWindow: Duration = 60.seconds,
    
    /**
     * Timeout before allowing test calls in half-open state
     */
    val halfOpenRetryTimeout: Duration = 10.seconds
) {
    
    init {
        require(failureThreshold > 0) { "Failure threshold must be positive" }
        require(timeoutWindow.isPositive()) { "Timeout window must be positive" }
        require(halfOpenRetryTimeout.isPositive()) { "Half-open retry timeout must be positive" }
    }
    
    public companion object {
        public fun default(): CircuitBreakerConfig = CircuitBreakerConfig()
    }
}

/**
 * Retry policy configuration
 */
public data class RetryPolicyConfig(
    /**
     * Maximum number of retry attempts
     */
    val maxAttempts: Int = 3,
    
    /**
     * Base delay between retries
     */
    val baseDelay: Duration = 100.milliseconds,
    
    /**
     * Maximum delay between retries
     */
    val maxDelay: Duration = 30.seconds,
    
    /**
     * Exponential backoff multiplier
     */
    val multiplier: Double = 2.0,
    
    /**
     * Jitter factor (0.0 to 1.0)
     */
    val jitter: Double = 0.1
) {
    
    init {
        require(maxAttempts > 0) { "Max attempts must be positive" }
        require(baseDelay.isPositive()) { "Base delay must be positive" }
        require(maxDelay.isPositive()) { "Max delay must be positive" }
        require(multiplier > 1.0) { "Multiplier must be greater than 1.0" }
        require(jitter in 0.0..1.0) { "Jitter must be between 0.0 and 1.0" }
    }
    
    public companion object {
        public fun default(): RetryPolicyConfig = RetryPolicyConfig()
    }
}

/**
 * Bulkhead configuration
 */
public data class BulkheadConfig(
    /**
     * Maximum concurrent calls for pipeline execution
     */
    val maxConcurrentPipelines: Int = 10,
    
    /**
     * Maximum concurrent calls for stage execution
     */
    val maxConcurrentStages: Int = 20,
    
    /**
     * Maximum concurrent calls for step execution
     */
    val maxConcurrentSteps: Int = 50
) {
    
    init {
        require(maxConcurrentPipelines > 0) { "Max concurrent pipelines must be positive" }
        require(maxConcurrentStages > 0) { "Max concurrent stages must be positive" }
        require(maxConcurrentSteps > 0) { "Max concurrent steps must be positive" }
    }
    
    public companion object {
        public fun default(): BulkheadConfig = BulkheadConfig()
    }
}