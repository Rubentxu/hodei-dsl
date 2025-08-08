package dev.rubentxu.hodei.core.execution

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Circuit breaker pattern implementation
 * 
 * Provides automatic failure detection and recovery with configurable
 * failure thresholds, timeout windows, and state transitions.
 */
public class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 5,
    private val timeoutWindow: Duration = 60.seconds,
    private val halfOpenRetryTimeout: Duration = 10.seconds
) {
    
    private var state = State.CLOSED
    private val failureCount = AtomicInteger(0)
    private var lastFailureTime: Instant? = null
    private val mutex = Mutex()
    
    public enum class State {
        CLOSED,    // Normal operation
        OPEN,      // Failing fast
        HALF_OPEN  // Testing recovery
    }
    
    /**
     * Executes block with circuit breaker protection
     */
    public suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            when (state) {
                State.OPEN -> {
                    if (shouldAttemptReset()) {
                        state = State.HALF_OPEN
                    } else {
                        throw CircuitBreakerOpenException(name)
                    }
                }
                State.HALF_OPEN -> {
                    // Allow single test call
                }
                State.CLOSED -> {
                    // Normal operation
                }
            }
        }
        
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
    
    private suspend fun onSuccess() {
        mutex.withLock {
            failureCount.set(0)
            state = State.CLOSED
        }
    }
    
    private suspend fun onFailure() {
        mutex.withLock {
            val failures = failureCount.incrementAndGet()
            lastFailureTime = Instant.now()
            
            if (failures >= failureThreshold) {
                state = State.OPEN
            }
        }
    }
    
    private fun shouldAttemptReset(): Boolean {
        val lastFailure = lastFailureTime ?: return true
        val elapsed = java.time.Duration.between(lastFailure, Instant.now())
        return elapsed >= java.time.Duration.ofMillis(halfOpenRetryTimeout.inWholeMilliseconds)
    }
    
    public fun getState(): State = state
    public fun getFailureCount(): Int = failureCount.get()
}

/**
 * Retry policy with exponential backoff and jitter
 */
public class RetryPolicy(
    private val maxAttempts: Int = 3,
    private val baseDelay: Duration = 100.milliseconds,
    private val maxDelay: Duration = 30.seconds,
    private val multiplier: Double = 2.0,
    private val jitter: Double = 0.1
) {
    
    /**
     * Executes block with retry policy
     */
    public suspend fun <T> execute(
        retryableExceptions: Set<Class<out Throwable>> = setOf(Exception::class.java),
        block: suspend (attempt: Int) -> T
    ): T {
        var lastException: Throwable? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Throwable) {
                lastException = e
                
                // Don't retry if exception is not retryable
                if (!isRetryable(e, retryableExceptions)) {
                    throw e
                }
                
                // Don't delay on last attempt
                if (attempt < maxAttempts - 1) {
                    val delay = calculateDelay(attempt)
                    delay(delay.inWholeMilliseconds)
                }
            }
        }
        
        throw RetryExhaustedException(maxAttempts, lastException!!)
    }
    
    private fun calculateDelay(attempt: Int): Duration {
        val exponentialDelay = baseDelay.inWholeMilliseconds * multiplier.pow(attempt.toDouble())
        val cappedDelay = min(exponentialDelay, maxDelay.inWholeMilliseconds.toDouble())
        val jitteredDelay = cappedDelay * (1.0 + (Math.random() - 0.5) * jitter)
        return jitteredDelay.toLong().milliseconds
    }
    
    private fun isRetryable(exception: Throwable, retryableExceptions: Set<Class<out Throwable>>): Boolean {
        return retryableExceptions.any { it.isInstance(exception) }
    }
}

/**
 * Bulkhead pattern for resource isolation
 */
public class Bulkhead(
    private val name: String,
    private val maxConcurrentCalls: Int = 10
) {
    private val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrentCalls)
    
    /**
     * Executes block with bulkhead protection
     */
    public suspend fun <T> execute(block: suspend () -> T): T {
        if (!semaphore.tryAcquire()) {
            throw BulkheadFullException(name, maxConcurrentCalls)
        }
        
        return try {
            block()
        } finally {
            semaphore.release()
        }
    }
    
    public fun getAvailablePermits(): Int = semaphore.availablePermits
}

/**
 * Composite fault tolerance executor
 */
public class FaultTolerantExecutor(
    private val name: String,
    private val circuitBreaker: CircuitBreaker? = null,
    private val retryPolicy: RetryPolicy? = null,
    private val bulkhead: Bulkhead? = null
) {
    
    /**
     * Executes block with all configured fault tolerance patterns
     */
    public suspend fun <T> execute(block: suspend () -> T): T {
        var operation: suspend () -> T = block
        
        // Apply bulkhead first (outermost)
        if (bulkhead != null) {
            operation = { bulkhead.execute(operation) }
        }
        
        // Apply circuit breaker
        if (circuitBreaker != null) {
            operation = { circuitBreaker.execute(operation) }
        }
        
        // Apply retry policy (innermost)
        return if (retryPolicy != null) {
            retryPolicy.execute { retryPolicy.execute(block = { operation() }) }
        } else {
            operation()
        }
    }
}

/**
 * Exception thrown when circuit breaker is open
 */
public class CircuitBreakerOpenException(
    circuitBreakerName: String
) : Exception("Circuit breaker '$circuitBreakerName' is open")

/**
 * Exception thrown when retry attempts are exhausted
 */
public class RetryExhaustedException(
    attempts: Int,
    cause: Throwable
) : Exception("Retry exhausted after $attempts attempts", cause)

/**
 * Exception thrown when bulkhead is full
 */
public class BulkheadFullException(
    bulkheadName: String,
    maxConcurrentCalls: Int
) : Exception("Bulkhead '$bulkheadName' is full (max: $maxConcurrentCalls)")