package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.Agent
import java.time.Duration
import java.time.Instant

/**
 * Result of pipeline execution
 * 
 * Immutable data class containing complete execution results including
 * status, timing information, stage results, and metadata for analysis.
 */
public data class PipelineResult(
    /**
     * Unique identifier for this execution
     */
    val executionId: String,
    
    /**
     * Overall pipeline execution status
     */
    val status: PipelineStatus,
    
    /**
     * Results from all executed stages
     */
    val stages: List<StageResult>,
    
    /**
     * Total pipeline execution duration
     */
    val duration: Duration,
    
    /**
     * Pipeline start timestamp
     */
    val startedAt: Instant,
    
    /**
     * Pipeline completion timestamp
     */
    val finishedAt: Instant,
    
    /**
     * Error information if pipeline failed
     */
    val error: Throwable? = null,
    
    /**
     * Additional metadata about the execution
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    /**
     * Checks if pipeline execution was successful
     */
    val isSuccessful: Boolean
        get() = status == PipelineStatus.SUCCESS
    
    /**
     * Gets the total number of executed stages
     */
    val totalStages: Int
        get() = stages.size
    
    /**
     * Gets the number of successful stages
     */
    val successfulStages: Int
        get() = stages.count { it.status == StageStatus.SUCCESS }
    
    /**
     * Gets the number of failed stages
     */
    val failedStages: Int
        get() = stages.count { it.status == StageStatus.FAILURE }
}

/**
 * Result of stage execution
 * 
 * Contains stage execution details including step results, branch results
 * for parallel stages, and timing information.
 */
public data class StageResult(
    /**
     * Name of the executed stage
     */
    val stageName: String,
    
    /**
     * Stage execution status
     */
    val status: StageStatus,
    
    /**
     * Results from all executed steps
     */
    val steps: List<StepResult>,
    
    /**
     * Stage execution duration
     */
    val duration: Duration,
    
    /**
     * Agent used for stage execution
     */
    val agent: Agent? = null,
    
    /**
     * Branch results for parallel stages
     */
    val branches: List<StageResult>? = null,
    
    /**
     * Error information if stage failed
     */
    val error: Throwable? = null,
    
    /**
     * Additional metadata about stage execution
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    /**
     * Checks if stage execution was successful
     */
    val isSuccessful: Boolean
        get() = status == StageStatus.SUCCESS
    
    /**
     * Gets the total number of executed steps
     */
    val totalSteps: Int
        get() = steps.size
    
    /**
     * Gets the number of successful steps
     */
    val successfulSteps: Int
        get() = steps.count { it.status == StepStatus.SUCCESS }
}

/**
 * Result of step execution
 * 
 * Contains detailed information about individual step execution including
 * output, exit codes, timing, and nested results for composite steps.
 */
public data class StepResult(
    /**
     * Name/type of the executed step
     */
    val stepName: String,
    
    /**
     * Step execution status
     */
    val status: StepStatus,
    
    /**
     * Step execution duration
     */
    val duration: Duration,
    
    /**
     * Command output (stdout)
     */
    val output: String = "",
    
    /**
     * Error output (stderr)
     */
    val errorOutput: String = "",
    
    /**
     * Command exit code (0 for success)
     */
    val exitCode: Int = 0,
    
    /**
     * Error information if step failed
     */
    val error: Throwable? = null,
    
    /**
     * Results from nested steps (for composite steps like dir, withEnv)
     */
    val nestedResults: List<StepResult> = emptyList(),
    
    /**
     * Results from parallel branches (for parallel steps)
     */
    val branchResults: Map<String, StepResult> = emptyMap(),
    
    /**
     * Additional metadata about step execution
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    /**
     * Checks if step execution was successful
     */
    val isSuccessful: Boolean
        get() = status == StepStatus.SUCCESS
}

/**
 * Pipeline execution status enumeration
 */
public enum class PipelineStatus {
    /** Pipeline is currently running */
    RUNNING,
    
    /** Pipeline completed successfully */
    SUCCESS,
    
    /** Pipeline completed with some failures but continued */
    PARTIAL_SUCCESS,
    
    /** Pipeline failed */
    FAILURE,
    
    /** Pipeline was cancelled */
    CANCELLED,
    
    /** Pipeline exceeded timeout */
    TIMEOUT
}

/**
 * Stage execution status enumeration
 */
public enum class StageStatus {
    /** Stage is currently running */
    RUNNING,
    
    /** Stage completed successfully */
    SUCCESS,
    
    /** Stage completed with some failures but continued */
    PARTIAL_SUCCESS,
    
    /** Stage failed */
    FAILURE,
    
    /** Stage was cancelled */
    CANCELLED,
    
    /** Stage exceeded timeout */
    TIMEOUT,
    
    /** Stage was skipped due to when condition */
    SKIPPED,
    
    /** Stage had partial failures in parallel branches */
    PARTIAL_FAILURE
}

/**
 * Step execution status enumeration
 */
public enum class StepStatus {
    /** Step is currently running */
    RUNNING,
    
    /** Step completed successfully */
    SUCCESS,
    
    /** Step failed */
    FAILURE,
    
    /** Step was cancelled */
    CANCELLED,
    
    /** Step exceeded timeout */
    TIMEOUT,
    
    /** Step was skipped */
    SKIPPED,
    
    /** Step failed validation */
    VALIDATION_FAILED
}

