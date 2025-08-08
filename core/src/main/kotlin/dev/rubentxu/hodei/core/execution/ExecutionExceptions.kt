package dev.rubentxu.hodei.core.execution

/**
 * Base exception for pipeline execution errors
 */
public abstract class ExecutionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when pipeline execution fails
 */
public class PipelineExecutionException(
    message: String,
    cause: Throwable? = null
) : ExecutionException(message, cause)

/**
 * Exception thrown when pipeline execution times out
 */
public class PipelineTimeoutException(
    message: String,
    val timeout: kotlin.time.Duration,
    cause: Throwable? = null
) : ExecutionException(message, cause)

/**
 * Exception thrown when stage execution fails
 */
public class StageExecutionException(
    val stageName: String,
    message: String,
    cause: Throwable? = null
) : ExecutionException("Stage '$stageName': $message", cause)

/**
 * Exception thrown when stage execution times out
 */
public class StageTimeoutException(
    val stageName: String,
    message: String,
    val timeout: kotlin.time.Duration,
    cause: Throwable? = null
) : ExecutionException("Stage '$stageName' timed out after $timeout: $message", cause)

/**
 * Exception thrown when step execution fails
 */
public class StepExecutionException(
    val stepName: String,
    message: String,
    cause: Throwable? = null
) : ExecutionException("Step '$stepName': $message", cause)

/**
 * Exception thrown when step execution times out
 */
public class StepTimeoutException(
    val stepName: String,
    message: String,
    val timeout: kotlin.time.Duration,
    cause: Throwable? = null
) : ExecutionException("Step '$stepName' timed out after $timeout: $message", cause)

/**
 * Exception thrown when step validation fails
 */
public class StepValidationException(
    val stepName: String,
    val validationErrors: List<ValidationError>,
    message: String = "Step validation failed"
) : ExecutionException("Step '$stepName': $message - ${validationErrors.joinToString(", ") { it.message }}")

/**
 * Exception thrown when dispatcher configuration is invalid
 */
public class DispatcherConfigurationException(
    message: String,
    cause: Throwable? = null
) : ExecutionException(message, cause)

/**
 * Exception thrown when resource management fails
 */
public class ResourceManagementException(
    message: String,
    cause: Throwable? = null
) : ExecutionException(message, cause)

