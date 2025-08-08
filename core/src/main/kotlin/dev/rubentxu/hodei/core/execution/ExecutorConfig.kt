package dev.rubentxu.hodei.core.execution

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for pipeline executor
 * 
 * Provides comprehensive configuration options for pipeline execution
 * including timeouts, concurrency limits, metrics, and event handling.
 */
public data class ExecutorConfig(
    /**
     * Maximum number of concurrent pipeline executions
     */
    val maxConcurrentPipelines: Int = 10,
    
    /**
     * Default timeout for stages if not specified
     */
    val defaultStageTimeout: Duration = 30.minutes,
    
    /**
     * Default timeout for steps if not specified  
     */
    val defaultStepTimeout: Duration = 15.minutes,
    
    /**
     * Global pipeline timeout (null = no timeout)
     */
    val globalTimeout: Duration? = null,
    
    /**
     * Whether to enable metrics collection
     */
    val enableMetrics: Boolean = true,
    
    /**
     * Whether to enable distributed tracing
     */
    val enableTracing: Boolean = false,
    
    /**
     * Event listener for pipeline events (null = no events)
     */
    val eventListener: PipelineEventListener? = null,
    
    /**
     * Metrics collector for execution metrics
     */
    val metricsCollector: ExecutionMetricsCollector? = null,
    
    /**
     * Dispatcher configuration for workload management
     */
    val dispatcherConfig: ExecutionDispatcherConfig = ExecutionDispatcherConfig.default(),
    
    /**
     * Enable fail-fast behavior by default
     */
    val defaultFailFast: Boolean = true,
    
    /**
     * Maximum retry attempts for failed operations
     */
    val maxRetryAttempts: Int = 3,
    
    /**
     * Base delay for retry operations
     */
    val retryBaseDelay: Duration = 1.seconds,
    
    /**
     * Maximum delay for retry operations
     */
    val retryMaxDelay: Duration = 30.seconds,
    
    /**
     * Enable resource cleanup on cancellation
     */
    val enableResourceCleanup: Boolean = true,
    
    /**
     * Buffer size for event channels
     */
    val eventChannelBufferSize: Int = 1000,
    
    /**
     * Fault tolerance configuration
     */
    val faultToleranceConfig: FaultToleranceConfig = FaultToleranceConfig.default()
) {
    
    init {
        require(maxConcurrentPipelines > 0) { "Max concurrent pipelines must be positive" }
        require(defaultStageTimeout.isPositive()) { "Default stage timeout must be positive" }
        require(defaultStepTimeout.isPositive()) { "Default step timeout must be positive" }
        require(globalTimeout == null || globalTimeout.isPositive()) { "Global timeout must be positive if specified" }
        require(maxRetryAttempts >= 0) { "Max retry attempts must be non-negative" }
        require(retryBaseDelay.isPositive()) { "Retry base delay must be positive" }
        require(retryMaxDelay.isPositive()) { "Retry max delay must be positive" }
        require(eventChannelBufferSize > 0) { "Event channel buffer size must be positive" }
    }
    
    public companion object {
        /**
         * Creates default executor configuration
         */
        public fun default(): ExecutorConfig = ExecutorConfig()
        
        /**
         * Creates builder for custom executor configuration
         */
        public fun builder(): Builder = Builder()
    }
    
    /**
     * Builder for ExecutorConfig
     */
    public class Builder {
        private var maxConcurrentPipelines: Int = 10
        private var defaultStageTimeout: Duration = 30.minutes
        private var defaultStepTimeout: Duration = 15.minutes
        private var globalTimeout: Duration? = null
        private var enableMetrics: Boolean = true
        private var enableTracing: Boolean = false
        private var eventListener: PipelineEventListener? = null
        private var metricsCollector: ExecutionMetricsCollector? = null
        private var dispatcherConfig: ExecutionDispatcherConfig = ExecutionDispatcherConfig.default()
        private var defaultFailFast: Boolean = true
        private var maxRetryAttempts: Int = 3
        private var retryBaseDelay: Duration = 1.seconds
        private var retryMaxDelay: Duration = 30.seconds
        private var enableResourceCleanup: Boolean = true
        private var eventChannelBufferSize: Int = 1000
        private var faultToleranceConfig: FaultToleranceConfig = FaultToleranceConfig.default()
        
        public fun maxConcurrentPipelines(max: Int): Builder = apply {
            this.maxConcurrentPipelines = max
        }
        
        public fun defaultStageTimeout(timeout: Duration): Builder = apply {
            this.defaultStageTimeout = timeout
        }
        
        public fun defaultStepTimeout(timeout: Duration): Builder = apply {
            this.defaultStepTimeout = timeout
        }
        
        public fun globalTimeout(timeout: Duration?): Builder = apply {
            this.globalTimeout = timeout
        }
        
        public fun enableMetrics(enable: Boolean): Builder = apply {
            this.enableMetrics = enable
        }
        
        public fun enableTracing(enable: Boolean): Builder = apply {
            this.enableTracing = enable
        }
        
        public fun eventListener(listener: PipelineEventListener?): Builder = apply {
            this.eventListener = listener
        }
        
        public fun metricsCollector(collector: ExecutionMetricsCollector?): Builder = apply {
            this.metricsCollector = collector
        }
        
        public fun dispatcherConfig(config: ExecutionDispatcherConfig): Builder = apply {
            this.dispatcherConfig = config
        }
        
        public fun defaultFailFast(failFast: Boolean): Builder = apply {
            this.defaultFailFast = failFast
        }
        
        public fun maxRetryAttempts(attempts: Int): Builder = apply {
            this.maxRetryAttempts = attempts
        }
        
        public fun retryBaseDelay(delay: Duration): Builder = apply {
            this.retryBaseDelay = delay
        }
        
        public fun retryMaxDelay(delay: Duration): Builder = apply {
            this.retryMaxDelay = delay
        }
        
        public fun enableResourceCleanup(enable: Boolean): Builder = apply {
            this.enableResourceCleanup = enable
        }
        
        public fun eventChannelBufferSize(size: Int): Builder = apply {
            this.eventChannelBufferSize = size
        }
        
        public fun faultToleranceConfig(config: FaultToleranceConfig): Builder = apply {
            this.faultToleranceConfig = config
        }
        
        public fun build(): ExecutorConfig = ExecutorConfig(
            maxConcurrentPipelines = maxConcurrentPipelines,
            defaultStageTimeout = defaultStageTimeout,
            defaultStepTimeout = defaultStepTimeout,
            globalTimeout = globalTimeout,
            enableMetrics = enableMetrics,
            enableTracing = enableTracing,
            eventListener = eventListener,
            metricsCollector = metricsCollector,
            dispatcherConfig = dispatcherConfig,
            defaultFailFast = defaultFailFast,
            maxRetryAttempts = maxRetryAttempts,
            retryBaseDelay = retryBaseDelay,
            retryMaxDelay = retryMaxDelay,
            enableResourceCleanup = enableResourceCleanup,
            eventChannelBufferSize = eventChannelBufferSize,
            faultToleranceConfig = faultToleranceConfig
        )
    }
}

/**
 * Configuration for step executor
 */
public data class StepExecutorConfig(
    /**
     * Default timeout for step execution
     */
    val defaultTimeout: Duration = 15.minutes,
    
    /**
     * Whether to enable metrics collection
     */
    val enableMetrics: Boolean = true,
    
    /**
     * Metrics collector for step execution
     */
    val metricsCollector: Any? = null, // Will be properly typed later
    
    /**
     * Maximum output buffer size
     */
    val maxOutputBufferSize: Int = 1024 * 1024, // 1MB
    
    /**
     * Whether to capture stderr separately
     */
    val separateStderr: Boolean = true
) {
    
    public companion object {
        public fun builder(): StepExecutorConfigBuilder = StepExecutorConfigBuilder()
    }
}

/**
 * Builder for StepExecutorConfig
 */
public class StepExecutorConfigBuilder {
    private var defaultTimeout: Duration = 15.minutes
    private var enableMetrics: Boolean = true
    private var metricsCollector: Any? = null
    private var maxOutputBufferSize: Int = 1024 * 1024
    private var separateStderr: Boolean = true
    
    public fun defaultTimeout(timeout: Duration): StepExecutorConfigBuilder = apply {
        this.defaultTimeout = timeout
    }
    
    public fun enableMetrics(enable: Boolean): StepExecutorConfigBuilder = apply {
        this.enableMetrics = enable
    }
    
    public fun metricsCollector(collector: Any?): StepExecutorConfigBuilder = apply {
        this.metricsCollector = collector
    }
    
    public fun maxOutputBufferSize(size: Int): StepExecutorConfigBuilder = apply {
        this.maxOutputBufferSize = size
    }
    
    public fun separateStderr(separate: Boolean): StepExecutorConfigBuilder = apply {
        this.separateStderr = separate
    }
    
    public fun build(): StepExecutorConfig = StepExecutorConfig(
        defaultTimeout = defaultTimeout,
        enableMetrics = enableMetrics,
        metricsCollector = metricsCollector,
        maxOutputBufferSize = maxOutputBufferSize,
        separateStderr = separateStderr
    )
}

/**
 * Interface for collecting execution metrics
 */
public interface ExecutionMetricsCollector {
    public fun recordPipelineStart(executionId: String, pipelineName: String)
    public fun recordPipelineCompletion(executionId: String, duration: java.time.Duration, status: PipelineStatus)
    public fun recordStageExecution(stageName: String, duration: java.time.Duration, status: StageStatus)
    public fun recordStepExecution(stepName: String, stepType: String, duration: java.time.Duration, status: StepStatus)
}