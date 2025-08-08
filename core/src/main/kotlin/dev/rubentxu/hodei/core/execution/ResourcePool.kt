package dev.rubentxu.hodei.core.execution

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Generic resource pool for managing expensive resources
 * 
 * Provides resource pooling with configurable size limits,
 * idle timeout, and health checking.
 */
public class ResourcePool<T>(
    private val name: String,
    private val maxSize: Int = 10,
    private val idleTimeout: Duration = 5.minutes,
    private val factory: suspend () -> T,
    private val validator: suspend (T) -> Boolean = { true },
    private val cleanup: suspend (T) -> Unit = { }
) {
    
    private val available = Channel<PooledResource<T>>(maxSize)
    private val active = mutableSetOf<PooledResource<T>>()
    private val mutex = Mutex()
    
    private data class PooledResource<T>(
        val resource: T,
        val createdAt: Instant,
        var lastUsed: Instant
    )
    
    /**
     * Acquires resource from pool or creates new one
     */
    public suspend fun acquire(): T {
        // Try to get available resource
        val pooled = available.tryReceive().getOrNull()
        
        if (pooled != null) {
            mutex.withLock {
                active.add(pooled)
            }
            
            // Validate resource before use
            if (validator(pooled.resource)) {
                pooled.lastUsed = Instant.now()
                return pooled.resource
            } else {
                // Resource is invalid, clean up and try again
                cleanup(pooled.resource)
                return acquire()
            }
        }
        
        // Create new resource if pool not full
        mutex.withLock {
            if (active.size < maxSize) {
                val newResource = factory()
                val pooledResource = PooledResource(
                    resource = newResource,
                    createdAt = Instant.now(),
                    lastUsed = Instant.now()
                )
                active.add(pooledResource)
                return newResource
            }
        }
        
        throw ResourcePoolExhaustedException(name, maxSize)
    }
    
    /**
     * Returns resource to pool
     */
    public suspend fun release(resource: T) {
        mutex.withLock {
            val pooled = active.find { it.resource == resource }
            if (pooled != null) {
                active.remove(pooled)
                pooled.lastUsed = Instant.now()
                
                // Only return to pool if still valid and not timed out
                if (validator(pooled.resource) && !isTimedOut(pooled)) {
                    available.trySend(pooled)
                } else {
                    cleanup(pooled.resource)
                }
            }
        }
    }
    
    /**
     * Uses resource with automatic release
     */
    public suspend fun <R> use(block: suspend (T) -> R): R {
        val resource = acquire()
        return try {
            block(resource)
        } finally {
            release(resource)
        }
    }
    
    /**
     * Cleans up expired resources
     */
    public suspend fun cleanupExpired() {
        val expiredResources = mutableListOf<PooledResource<T>>()
        
        // Check available resources
        while (true) {
            val pooled = available.tryReceive().getOrNull() ?: break
            if (isTimedOut(pooled)) {
                expiredResources.add(pooled)
            } else {
                available.trySend(pooled)
            }
        }
        
        // Check active resources (shouldn't happen normally)
        mutex.withLock {
            val activeExpired = active.filter { isTimedOut(it) }
            expiredResources.addAll(activeExpired)
            active.removeAll(activeExpired)
        }
        
        // Cleanup expired resources
        expiredResources.forEach { cleanup(it.resource) }
    }
    
    private fun isTimedOut(pooled: PooledResource<T>): Boolean {
        val elapsed = java.time.Duration.between(pooled.lastUsed, Instant.now())
        return elapsed >= java.time.Duration.ofMillis(idleTimeout.inWholeMilliseconds)
    }
    
    /**
     * Closes pool and cleans up all resources
     */
    public suspend fun close() {
        // Clean up available resources
        while (true) {
            val pooled = available.tryReceive().getOrNull() ?: break
            cleanup(pooled.resource)
        }
        
        // Clean up active resources
        mutex.withLock {
            active.forEach { cleanup(it.resource) }
            active.clear()
        }
        
        available.close()
    }
    
    public suspend fun getStats(): PoolStats {
        return mutex.withLock {
            PoolStats(
                maxSize = maxSize,
                activeCount = active.size,
                availableCount = available.isEmpty.let { if (it) 0 else 1 }, // Approximation
                totalCount = active.size + (if (available.isEmpty) 0 else 1)
            )
        }
    }
}

/**
 * Pool statistics
 */
public data class PoolStats(
    val maxSize: Int,
    val activeCount: Int,
    val availableCount: Int,
    val totalCount: Int
)

/**
 * Exception thrown when resource pool is exhausted
 */
public class ResourcePoolExhaustedException(
    poolName: String,
    maxSize: Int
) : Exception("Resource pool '$poolName' is exhausted (max size: $maxSize)")

/**
 * Coroutine dispatcher pool for workload-specific optimization
 */
public class DispatcherPool(
    private val config: ExecutorConfig
) {
    
    private val computePool = ResourcePool<CoroutineDispatcher>(
        name = "compute-dispatcher",
        maxSize = 10,  // Default pool size
        factory = { 
            config.dispatcherConfig.cpuDispatcher
        }
    )
    
    private val ioPool = ResourcePool<CoroutineDispatcher>(
        name = "io-dispatcher", 
        maxSize = 20,  // Default pool size
        factory = {
            config.dispatcherConfig.ioDispatcher
        }
    )
    
    /**
     * Gets optimized dispatcher for workload type
     */
    public suspend fun getDispatcher(workloadType: WorkloadType): CoroutineDispatcher {
        return when (workloadType) {
            WorkloadType.CPU_INTENSIVE -> computePool.acquire()
            WorkloadType.IO_INTENSIVE -> ioPool.acquire()
            WorkloadType.NETWORK -> ioPool.acquire()
            WorkloadType.BLOCKING -> config.dispatcherConfig.blockingDispatcher
            WorkloadType.SYSTEM -> config.dispatcherConfig.systemDispatcher
            WorkloadType.DEFAULT -> Dispatchers.Default
        }
    }
    
    /**
     * Returns dispatcher to pool
     */
    public suspend fun releaseDispatcher(dispatcher: CoroutineDispatcher, workloadType: WorkloadType) {
        when (workloadType) {
            WorkloadType.CPU_INTENSIVE -> computePool.release(dispatcher)
            WorkloadType.IO_INTENSIVE -> ioPool.release(dispatcher)
            WorkloadType.NETWORK -> ioPool.release(dispatcher)
            WorkloadType.BLOCKING -> { /* No-op for blocking dispatcher */ }
            WorkloadType.SYSTEM -> { /* No-op for system dispatcher */ }
            WorkloadType.DEFAULT -> { /* No-op for default dispatcher */ }
        }
    }
    
    /**
     * Uses dispatcher with automatic release
     */
    public suspend fun <T> useDispatcher(
        workloadType: WorkloadType,
        block: suspend (CoroutineDispatcher) -> T
    ): T {
        return when (workloadType) {
            WorkloadType.DEFAULT -> block(Dispatchers.Default)
            WorkloadType.BLOCKING -> block(config.dispatcherConfig.blockingDispatcher)
            WorkloadType.SYSTEM -> block(config.dispatcherConfig.systemDispatcher)
            WorkloadType.CPU_INTENSIVE -> computePool.use { block(it) }
            WorkloadType.IO_INTENSIVE, WorkloadType.NETWORK -> ioPool.use { block(it) }
        }
    }
    
    public suspend fun close() {
        computePool.close()
        ioPool.close()
    }
}