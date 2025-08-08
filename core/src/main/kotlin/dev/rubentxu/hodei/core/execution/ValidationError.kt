package dev.rubentxu.hodei.core.execution

/**
 * Validation error for execution context and pipeline validation
 * 
 * Represents validation errors with structured information including
 * field name, error message, and error code for programmatic handling.
 */
public data class ValidationError(
    /**
     * The field or property that failed validation
     */
    val field: String,
    
    /**
     * Human-readable error message
     */
    val message: String,
    
    /**
     * Machine-readable error code
     */
    val code: String,
    
    /**
     * Severity of the validation error
     */
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
    
    /**
     * Additional context or metadata about the error
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    /**
     * Creates a formatted error message with field context
     */
    public fun getFormattedMessage(): String = "[$field] $message"
    
    /**
     * Checks if this is a blocking error (ERROR or CRITICAL severity)
     */
    public val isBlocking: Boolean
        get() = severity == ValidationSeverity.ERROR || severity == ValidationSeverity.CRITICAL
    
    public companion object {
        /**
         * Creates a validation error for a required field
         */
        public fun required(field: String): ValidationError = ValidationError(
            field = field,
            message = "$field is required",
            code = "REQUIRED_FIELD_MISSING",
            severity = ValidationSeverity.ERROR
        )
        
        /**
         * Creates a validation error for an invalid format
         */
        public fun invalidFormat(field: String, expectedFormat: String): ValidationError = ValidationError(
            field = field,
            message = "$field has invalid format. Expected: $expectedFormat",
            code = "INVALID_FORMAT",
            severity = ValidationSeverity.ERROR
        )
        
        /**
         * Creates a validation error for an invalid value
         */
        public fun invalidValue(field: String, value: Any?, validValues: List<Any>? = null): ValidationError = ValidationError(
            field = field,
            message = if (validValues != null) {
                "$field has invalid value '$value'. Valid values: ${validValues.joinToString(", ")}"
            } else {
                "$field has invalid value '$value'"
            },
            code = "INVALID_VALUE",
            severity = ValidationSeverity.ERROR,
            metadata = mapOf("invalid_value" to (value ?: "null"), "valid_values" to (validValues ?: emptyList<Any>()))
        )
        
        /**
         * Creates a validation warning
         */
        public fun warning(field: String, message: String): ValidationError = ValidationError(
            field = field,
            message = message,
            code = "WARNING",
            severity = ValidationSeverity.WARNING
        )
        
        /**
         * Creates a validation error for file system issues
         */
        public fun fileSystemError(field: String, path: String, issue: String): ValidationError = ValidationError(
            field = field,
            message = "File system error for '$path': $issue",
            code = "FILE_SYSTEM_ERROR",
            severity = ValidationSeverity.ERROR,
            metadata = mapOf("path" to path, "issue" to issue)
        )
    }
}

/**
 * Severity levels for validation errors
 */
public enum class ValidationSeverity {
    /** Informational message, does not block execution */
    INFO,
    
    /** Warning message, execution can continue but may have issues */
    WARNING,
    
    /** Error message, blocks execution */
    ERROR,
    
    /** Critical error, blocks execution and may cause system instability */
    CRITICAL
}