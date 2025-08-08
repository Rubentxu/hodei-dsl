package dev.rubentxu.hodei.core.execution

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Configuration for execution dispatchers
 * 
 * Provides specialized dispatchers for different types of workloads
 * to optimize pipeline execution performance.
 */
public class ExecutionDispatcherConfig private constructor(
    /**
     * Dispatcher for CPU-intensive operations
     */
    public val cpuDispatcher: CoroutineDispatcher,
    
    /**
     * Dispatcher for I/O intensive operations
     */
    public val ioDispatcher: CoroutineDispatcher,
    
    /**
     * Dispatcher for network operations
     */
    public val networkDispatcher: CoroutineDispatcher,
    
    /**
     * Dispatcher for blocking operations
     */
    public val blockingDispatcher: CoroutineDispatcher,
    
    /**
     * Dispatcher for system operations
     */
    public val systemDispatcher: CoroutineDispatcher
) {
    
    /**
     * Selects appropriate dispatcher based on workload type
     * @param workloadType The type of workload
     * @return Appropriate dispatcher for the workload
     */
    public fun selectDispatcher(workloadType: WorkloadType): CoroutineDispatcher {
        return when (workloadType) {
            WorkloadType.CPU_INTENSIVE -> cpuDispatcher
            WorkloadType.IO_INTENSIVE -> ioDispatcher
            WorkloadType.NETWORK -> networkDispatcher
            WorkloadType.BLOCKING -> blockingDispatcher
            WorkloadType.SYSTEM -> systemDispatcher
            WorkloadType.DEFAULT -> Dispatchers.Default
        }
    }
    
    /**
     * Determines workload type from step characteristics
     * @param stepName Name of the step
     * @param command Command being executed (if applicable)
     * @return Detected workload type
     */
    public fun detectWorkloadType(stepName: String, command: String? = null): WorkloadType {
        return when {
            stepName == "sh" && command != null -> detectShellWorkload(command)
            stepName == "docker" -> WorkloadType.NETWORK
            stepName == "archiveArtifacts" -> WorkloadType.IO_INTENSIVE
            stepName == "publishTestResults" -> WorkloadType.IO_INTENSIVE
            stepName == "stash" -> WorkloadType.IO_INTENSIVE
            stepName == "unstash" -> WorkloadType.IO_INTENSIVE
            stepName.contains("build") || stepName.contains("compile") -> WorkloadType.CPU_INTENSIVE
            stepName.contains("test") -> WorkloadType.CPU_INTENSIVE
            stepName.contains("deploy") -> WorkloadType.NETWORK
            else -> WorkloadType.DEFAULT
        }
    }
    
    private fun detectShellWorkload(command: String): WorkloadType {
        return when {
            command.contains(Regex("(gcc|javac|kotlinc|build|compile|make)")) -> WorkloadType.CPU_INTENSIVE
            command.contains(Regex("(curl|wget|rsync|scp|git clone|docker pull)")) -> WorkloadType.NETWORK
            command.contains(Regex("(cp|mv|tar|zip|find|grep|awk|sed)")) -> WorkloadType.IO_INTENSIVE
            command.contains(Regex("(sleep|wait)")) -> WorkloadType.BLOCKING
            else -> WorkloadType.DEFAULT
        }
    }
    
    public companion object {
        /**
         * Creates default dispatcher configuration
         */
        public fun default(): ExecutionDispatcherConfig {
            return builder().build()
        }
        
        /**
         * Creates builder for custom dispatcher configuration
         */
        public fun builder(): Builder = Builder()
    }
    
    /**
     * Builder for ExecutionDispatcherConfig
     */
    public class Builder {
        private var cpuThreads: Int = Runtime.getRuntime().availableProcessors()
        private var ioThreads: Int = max(64, Runtime.getRuntime().availableProcessors() * 8)
        private var networkThreads: Int = max(256, Runtime.getRuntime().availableProcessors() * 16)
        private var blockingThreads: Int = max(64, Runtime.getRuntime().availableProcessors() * 4)
        private var systemThreads: Int = 1
        
        /**
         * Sets the number of threads for CPU-intensive operations
         */
        public fun cpuThreads(threads: Int): Builder = apply {
            this.cpuThreads = threads
        }
        
        /**
         * Sets the number of threads for I/O intensive operations
         */
        public fun ioThreads(threads: Int): Builder = apply {
            this.ioThreads = threads
        }
        
        /**
         * Sets the number of threads for network operations
         */
        public fun networkThreads(threads: Int): Builder = apply {
            this.networkThreads = threads
        }
        
        /**
         * Sets the number of threads for blocking operations
         */
        public fun blockingThreads(threads: Int): Builder = apply {
            this.blockingThreads = threads
        }
        
        /**
         * Sets the number of threads for system operations
         */
        public fun systemThreads(threads: Int): Builder = apply {
            this.systemThreads = threads
        }
        
        /**
         * Builds the dispatcher configuration
         */
        public fun build(): ExecutionDispatcherConfig {
            return ExecutionDispatcherConfig(
                cpuDispatcher = if (cpuThreads <= 0) Dispatchers.Default 
                    else Dispatchers.Default.limitedParallelism(cpuThreads),
                ioDispatcher = if (ioThreads <= 0) Dispatchers.IO
                    else Dispatchers.IO.limitedParallelism(ioThreads),
                networkDispatcher = if (networkThreads <= 0) Dispatchers.IO
                    else Dispatchers.IO.limitedParallelism(networkThreads),
                blockingDispatcher = Executors.newFixedThreadPool(blockingThreads) { thread ->
                    Thread(thread, "blocking-pool-${thread.hashCode()}").apply {
                        isDaemon = true
                    }
                }.asCoroutineDispatcher(),
                systemDispatcher = if (systemThreads <= 1) 
                    Executors.newSingleThreadExecutor { thread ->
                        Thread(thread, "system-pool").apply { isDaemon = true }
                    }.asCoroutineDispatcher()
                    else Executors.newFixedThreadPool(systemThreads) { thread ->
                        Thread(thread, "system-pool-${thread.hashCode()}").apply { isDaemon = true }
                    }.asCoroutineDispatcher()
            )
        }
    }
}

/**
 * Workload analyzer for automatic dispatcher selection
 */
public class WorkloadAnalyzer {
    private val dispatcherConfig = ExecutionDispatcherConfig.default()
    
    /**
     * Analyzes step characteristics and returns appropriate dispatcher
     * @param stepName Name of the step
     * @param command Command to be executed
     * @param hint Workload type hint
     * @return Appropriate dispatcher
     */
    public fun analyzeAndSelect(
        stepName: String,
        command: String? = null,
        hint: WorkloadType? = null
    ): CoroutineDispatcher {
        val workloadType = hint ?: dispatcherConfig.detectWorkloadType(stepName, command)
        return dispatcherConfig.selectDispatcher(workloadType)
    }
    
    /**
     * Gets dispatcher configuration
     */
    public fun getDispatcherConfig(): ExecutionDispatcherConfig = dispatcherConfig
}