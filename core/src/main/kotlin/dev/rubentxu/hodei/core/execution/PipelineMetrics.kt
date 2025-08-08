package dev.rubentxu.hodei.core.execution

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pipeline metrics for tracking performance and execution statistics
 * 
 * Provides comprehensive metrics collection for pipeline execution including
 * timing information, step counts, and custom metrics tracking.
 */
public data class PipelineMetrics(
    /**
     * Pipeline execution start time
     */
    val startTime: Instant = Instant.now(),
    
    /**
     * Pipeline execution end time (null if still running)
     */
    val endTime: Instant? = null,
    
    /**
     * Number of steps executed
     */
    val stepCount: Int = 0,
    
    /**
     * Number of stages executed
     */
    val stageCount: Int = 0,
    
    /**
     * Number of failed steps
     */
    val failedSteps: Int = 0,
    
    /**
     * Custom metrics map
     */
    val customMetrics: Map<String, Any> = emptyMap(),
    
    /**
     * Step execution times
     */
    val stepTimes: Map<String, Duration> = emptyMap()
) {
    
    /**
     * Gets total pipeline execution duration
     * @return Duration from start to end (or current time if still running)
     */
    public fun getElapsedTime(): Duration {
        val end = endTime ?: Instant.now()
        return (end.toEpochMilli() - startTime.toEpochMilli()).milliseconds
    }
    
    /**
     * Records the start of a new step
     * @param stepName Name of the step being started
     * @return Updated metrics with incremented step count
     */
    public fun recordStepStart(stepName: String): PipelineMetrics = copy(
        stepCount = stepCount + 1,
        customMetrics = customMetrics + ("last_step_started" to stepName)
    )
    
    /**
     * Records the completion of a step with timing
     * @param stepName Name of the completed step
     * @param duration Duration the step took to execute
     * @return Updated metrics with step timing recorded
     */
    public fun recordStepComplete(stepName: String, duration: Duration): PipelineMetrics = copy(
        stepTimes = stepTimes + (stepName to duration),
        customMetrics = customMetrics + ("last_step_completed" to stepName)
    )
    
    /**
     * Records a step failure
     * @param stepName Name of the failed step
     * @param error Error that caused the failure
     * @return Updated metrics with failure recorded
     */
    public fun recordStepFailure(stepName: String, error: Throwable): PipelineMetrics = copy(
        failedSteps = failedSteps + 1,
        customMetrics = customMetrics + mapOf(
            "last_failed_step" to stepName,
            "last_error" to (error.message ?: "Unknown error")
        )
    )
    
    /**
     * Records the start of a new stage
     * @param stageName Name of the stage being started
     * @return Updated metrics with incremented stage count
     */
    public fun recordStageStart(stageName: String): PipelineMetrics = copy(
        stageCount = stageCount + 1,
        customMetrics = customMetrics + ("current_stage" to stageName)
    )
    
    /**
     * Records a custom metric value
     * @param name Metric name
     * @param value Metric value
     * @return Updated metrics with custom metric recorded
     */
    public fun recordCustomMetric(name: String, value: Any): PipelineMetrics = copy(
        customMetrics = customMetrics + (name to value)
    )
    
    /**
     * Marks the pipeline as completed
     * @return Updated metrics with end time set
     */
    public fun markCompleted(): PipelineMetrics = copy(
        endTime = Instant.now()
    )
    
    /**
     * Gets the success rate of executed steps
     * @return Success rate as percentage (0.0 to 1.0)
     */
    public fun getSuccessRate(): Double = 
        if (stepCount == 0) 1.0 
        else (stepCount - failedSteps).toDouble() / stepCount.toDouble()
    
    /**
     * Gets the average step execution time
     * @return Average duration per step, or null if no steps completed
     */
    public fun getAverageStepTime(): Duration? =
        if (stepTimes.isEmpty()) null
        else stepTimes.values.fold(Duration.ZERO) { acc, duration -> acc + duration } / stepTimes.size
    
    /**
     * Gets the slowest step execution time
     * @return Pair of step name and duration, or null if no steps completed
     */
    public fun getSlowestStep(): Pair<String, Duration>? =
        stepTimes.maxByOrNull { it.value }?.toPair()
    
    /**
     * Exports metrics as a map for external reporting
     * @return Map of metric names to values
     */
    public fun export(): Map<String, Any> = mutableMapOf<String, Any>().apply {
        put("start_time", startTime.toString())
        endTime?.let { put("end_time", it.toString()) }
        put("elapsed_time_ms", getElapsedTime().inWholeMilliseconds)
        put("step_count", stepCount)
        put("stage_count", stageCount)
        put("failed_steps", failedSteps)
        put("success_rate", getSuccessRate())
        
        getAverageStepTime()?.let { 
            put("average_step_time_ms", it.inWholeMilliseconds) 
        }
        
        getSlowestStep()?.let { (name, duration) ->
            put("slowest_step", name)
            put("slowest_step_time_ms", duration.inWholeMilliseconds)
        }
        
        putAll(customMetrics)
    }
    
    public companion object {
        /**
         * Creates new metrics instance for pipeline start
         */
        public fun start(): PipelineMetrics = PipelineMetrics()
        
        /**
         * Creates metrics instance with custom start time
         */
        public fun startAt(startTime: Instant): PipelineMetrics = 
            PipelineMetrics(startTime = startTime)
    }
}