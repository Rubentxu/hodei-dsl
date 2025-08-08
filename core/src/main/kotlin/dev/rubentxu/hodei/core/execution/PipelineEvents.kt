package dev.rubentxu.hodei.core.execution

import java.time.Instant

/**
 * Base interface for all pipeline events
 */
public sealed interface PipelineEvent {
    /**
     * Timestamp when the event occurred
     */
    val timestamp: Instant
    
    /**
     * Execution ID of the pipeline
     */
    val executionId: String
    
    /**
     * Pipeline started event
     */
    public data class PipelineStarted(
        override val executionId: String,
        val pipelineName: String,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Pipeline completed event
     */
    public data class PipelineCompleted(
        override val executionId: String,
        val status: PipelineStatus,
        val duration: java.time.Duration,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Stage started event
     */
    public data class StageStarted(
        override val executionId: String,
        val stageName: String,
        val stageIndex: Int,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Stage completed event
     */
    public data class StageCompleted(
        override val executionId: String,
        val stageName: String,
        val stageResult: StageResult,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Step started event
     */
    public data class StepStarted(
        override val executionId: String,
        val stageName: String,
        val stepName: String,
        val stepIndex: Int,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Step completed event
     */
    public data class StepCompleted(
        override val executionId: String,
        val stageName: String,
        val stepName: String,
        val stepResult: StepResult,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Branch started event (for parallel stages/steps)
     */
    public data class BranchStarted(
        override val executionId: String,
        val stageName: String,
        val branchName: String,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Branch completed event (for parallel stages/steps)
     */
    public data class BranchCompleted(
        override val executionId: String,
        val stageName: String,
        val branchName: String,
        val branchResult: StageResult,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Error occurred event
     */
    public data class ErrorOccurred(
        override val executionId: String,
        val component: String,
        val error: Throwable,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
    
    /**
     * Cancellation requested event
     */
    public data class CancellationRequested(
        override val executionId: String,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : PipelineEvent
}

/**
 * Interface for pipeline event listeners
 */
public interface PipelineEventListener {
    /**
     * Called when a pipeline event occurs
     * @param event The pipeline event
     */
    public suspend fun onEvent(event: PipelineEvent)
}

/**
 * Event bus interface for pipeline event distribution
 */
public interface PipelineEventBus {
    /**
     * Publishes an event to all registered listeners
     * @param event The event to publish
     */
    public suspend fun publish(event: PipelineEvent)
    
    /**
     * Registers an event listener
     * @param listener The listener to register
     */
    public fun subscribe(listener: PipelineEventListener)
    
    /**
     * Unregisters an event listener
     * @param listener The listener to unregister
     */
    public fun unsubscribe(listener: PipelineEventListener)
    
    /**
     * Closes the event bus and releases resources
     */
    public suspend fun close()
}