package dev.rubentxu.hodei.core.execution

/**
 * Workload types for dispatcher selection
 * 
 * Defines different types of workloads to enable optimal
 * dispatcher selection for pipeline execution.
 */
public enum class WorkloadType {
    /**
     * CPU-intensive operations (compilation, tests, computation)
     */
    CPU_INTENSIVE,
    
    /**
     * I/O intensive operations (file operations, disk access)
     */
    IO_INTENSIVE,
    
    /**
     * Network operations (HTTP requests, remote calls)
     */
    NETWORK,
    
    /**
     * Blocking operations (waiting, sleep, synchronous operations)
     */
    BLOCKING,
    
    /**
     * System operations (management, control)
     */
    SYSTEM,
    
    /**
     * Default operations (general purpose)
     */
    DEFAULT
}