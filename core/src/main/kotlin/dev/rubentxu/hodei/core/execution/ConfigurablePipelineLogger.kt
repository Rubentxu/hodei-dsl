package dev.rubentxu.hodei.core.execution

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Configuration for pipeline logger behavior
 */
public data class LoggerConfig(
    val level: LogLevel = LogLevel.INFO,
    val format: LogFormat = LogFormat.SIMPLE,
    val destination: LogDestination = LogDestination.CONSOLE,
    val includeTimestamp: Boolean = true,
    val includeThreadName: Boolean = false
)

/**
 * Log output format options
 */
public enum class LogFormat {
    /** Simple text format: [LEVEL] message */
    SIMPLE,
    
    /** Structured JSON format for parsing */
    JSON,
    
    /** Colored console output with ANSI codes */
    COLORED
}

/**
 * Log destination options
 */
public enum class LogDestination {
    /** Output to console (stdout/stderr) */
    CONSOLE,
    
    /** Output to file */
    FILE,
    
    /** Output to both console and file */
    BOTH
}

/**
 * Configurable pipeline logger implementation
 * 
 * Supports different log levels, formats, and output destinations
 * based on environment and configuration requirements.
 */
public class ConfigurablePipelineLogger(
    private val config: LoggerConfig
) : PipelineLogger {
    
    private var currentSection: String? = null
    private val dateFormatter = DateTimeFormatter.ISO_INSTANT
    
    override fun info(message: String, metadata: Map<String, Any>) {
        if (shouldLog(LogLevel.INFO)) {
            log(LogLevel.INFO, message, metadata)
        }
    }
    
    override fun warn(message: String, metadata: Map<String, Any>) {
        if (shouldLog(LogLevel.WARN)) {
            log(LogLevel.WARN, message, metadata)
        }
    }
    
    override fun error(message: String, throwable: Throwable?, metadata: Map<String, Any>) {
        if (shouldLog(LogLevel.ERROR)) {
            log(LogLevel.ERROR, message, metadata, throwable)
        }
    }
    
    override fun debug(message: String, metadata: Map<String, Any>) {
        if (shouldLog(LogLevel.DEBUG)) {
            log(LogLevel.DEBUG, message, metadata)
        }
    }
    
    override fun stdout(output: String) {
        println("[STDOUT] $output")
    }
    
    override fun stderr(output: String) {
        System.err.println("[STDERR] $output")
    }
    
    override fun startSection(name: String) {
        currentSection = name
        if (shouldLog(LogLevel.INFO)) {
            println("=== Starting Section: $name ===")
        }
    }
    
    override fun endSection() {
        currentSection?.let { name ->
            if (shouldLog(LogLevel.INFO)) {
                println("=== Ending Section: $name ===")
            }
        }
        currentSection = null
    }
    
    override fun logWithTimestamp(level: LogLevel, message: String, timestamp: Instant) {
        if (shouldLog(level)) {
            val formatted = formatMessage(level, message, emptyMap(), timestamp, null)
            output(formatted)
        }
    }
    
    /**
     * Determines if a message should be logged based on current level
     */
    private fun shouldLog(level: LogLevel): Boolean {
        return level.ordinal >= config.level.ordinal
    }
    
    /**
     * Core logging method with formatting
     */
    private fun log(
        level: LogLevel,
        message: String,
        metadata: Map<String, Any>,
        throwable: Throwable? = null
    ) {
        val formatted = formatMessage(level, message, metadata, Instant.now(), throwable)
        output(formatted)
        
        throwable?.printStackTrace()
    }
    
    /**
     * Formats log message according to configuration
     */
    private fun formatMessage(
        level: LogLevel,
        message: String,
        metadata: Map<String, Any>,
        timestamp: Instant,
        throwable: Throwable?
    ): String {
        return when (config.format) {
            LogFormat.SIMPLE -> formatSimple(level, message, metadata, timestamp, throwable)
            LogFormat.JSON -> formatJson(level, message, metadata, timestamp, throwable)
            LogFormat.COLORED -> formatColored(level, message, metadata, timestamp, throwable)
        }
    }
    
    /**
     * Simple text format implementation
     */
    private fun formatSimple(
        level: LogLevel,
        message: String,
        metadata: Map<String, Any>,
        timestamp: Instant,
        throwable: Throwable?
    ): String {
        val parts = mutableListOf<String>()
        
        if (config.includeTimestamp) {
            parts.add("[${dateFormatter.format(timestamp)}]")
        }
        
        parts.add("[${level.name}]")
        parts.add(message)
        
        if (metadata.isNotEmpty()) {
            val metadataStr = metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }
            parts.add("| $metadataStr")
        }
        
        val formatted = parts.joinToString(" ")
        
        return if (currentSection != null) {
            "[$currentSection] $formatted"
        } else {
            formatted
        }
    }
    
    /**
     * JSON format implementation
     */
    private fun formatJson(
        level: LogLevel,
        message: String,
        metadata: Map<String, Any>,
        timestamp: Instant,
        throwable: Throwable?
    ): String {
        val jsonParts = mutableMapOf<String, Any>()
        
        jsonParts["timestamp"] = dateFormatter.format(timestamp)
        jsonParts["level"] = level.name
        jsonParts["message"] = message
        
        if (currentSection != null) {
            jsonParts["section"] = currentSection!!
        }
        
        if (metadata.isNotEmpty()) {
            jsonParts.putAll(metadata)
        }
        
        if (throwable != null) {
            jsonParts["error"] = throwable.message ?: throwable::class.simpleName!!
            jsonParts["stackTrace"] = throwable.stackTrace.take(5).joinToString(", ")
        }
        
        // Simple JSON serialization (in production, use a proper JSON library)
        return jsonParts.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ","
        ) { (key, value) ->
            when (value) {
                is String -> "\"$key\":\"$value\""
                is Number -> "\"$key\":$value"
                else -> "\"$key\":\"$value\""
            }
        }
    }
    
    /**
     * Colored console format implementation
     */
    private fun formatColored(
        level: LogLevel,
        message: String,
        metadata: Map<String, Any>,
        timestamp: Instant,
        throwable: Throwable?
    ): String {
        val colorCode = when (level) {
            LogLevel.DEBUG -> "\u001B[37m"    // White
            LogLevel.INFO -> "\u001B[32m"     // Green  
            LogLevel.WARN -> "\u001B[33m"     // Yellow
            LogLevel.ERROR -> "\u001B[31m"    // Red
        }
        val resetCode = "\u001B[0m"
        
        val simple = formatSimple(level, message, metadata, timestamp, throwable)
        return "$colorCode$simple$resetCode"
    }
    
    /**
     * Outputs formatted message to configured destination
     */
    private fun output(formatted: String) {
        when (config.destination) {
            LogDestination.CONSOLE -> println(formatted)
            LogDestination.FILE -> {
                // In a real implementation, this would write to a file
                // For now, just print to console with [FILE] prefix
                println("[FILE] $formatted")
            }
            LogDestination.BOTH -> {
                println(formatted)
                println("[FILE] $formatted")
            }
        }
    }
}

/**
 * Factory for creating environment-specific configurable loggers
 */
public class ConfigurablePipelineLoggerFactory : PipelineLoggerFactory {
    
    override fun createDefault(): PipelineLogger {
        val config = LoggerConfig(
            level = LogLevel.INFO,
            format = LogFormat.SIMPLE,
            destination = LogDestination.CONSOLE
        )
        return ConfigurablePipelineLogger(config)
    }
    
    override fun createForEnvironment(environment: String): PipelineLogger {
        val config = when (environment.lowercase()) {
            "development", "dev" -> LoggerConfig(
                level = LogLevel.DEBUG,
                format = LogFormat.COLORED,
                destination = LogDestination.CONSOLE,
                includeTimestamp = true,
                includeThreadName = true
            )
            
            "production", "prod" -> LoggerConfig(
                level = LogLevel.INFO,
                format = LogFormat.JSON,
                destination = LogDestination.BOTH,
                includeTimestamp = true,
                includeThreadName = false
            )
            
            "test", "testing" -> LoggerConfig(
                level = LogLevel.ERROR,
                format = LogFormat.SIMPLE,
                destination = LogDestination.CONSOLE,
                includeTimestamp = false,
                includeThreadName = false
            )
            
            "staging", "stage" -> LoggerConfig(
                level = LogLevel.WARN,
                format = LogFormat.JSON,
                destination = LogDestination.CONSOLE,
                includeTimestamp = true,
                includeThreadName = false
            )
            
            else -> LoggerConfig() // Default configuration
        }
        
        return ConfigurablePipelineLogger(config)
    }
}