package dev.rubentxu.hodei.core.execution

import java.time.Instant

/**
 * Logger especializado para pipelines con soporte para
 * diferentes niveles y output estructurado
 * 
 * Provides structured logging capabilities for pipeline execution
 * with support for different log levels, sections, and metadata.
 */
public interface PipelineLogger {
    
    /**
     * Log de información general
     */
    public fun info(message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log de warnings
     */
    public fun warn(message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log de errores
     */
    public fun error(message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Log de debug (solo en modo debug)
     */
    public fun debug(message: String, metadata: Map<String, Any> = emptyMap())
    
    /**
     * Output directo del comando (stdout)
     */
    public fun stdout(output: String)
    
    /**
     * Error output del comando (stderr) 
     */
    public fun stderr(output: String)
    
    /**
     * Inicia una sección colapsible en el log
     */
    public fun startSection(name: String)
    
    /**
     * Finaliza la sección actual
     */
    public fun endSection()
    
    /**
     * Log con timestamp personalizado
     */
    public fun logWithTimestamp(level: LogLevel, message: String, timestamp: Instant = Instant.now())
}

/**
 * Log level enumeration
 */
public enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Default implementation of PipelineLogger
 * 
 * Provides console-based logging with structured output formatting.
 * In production, this would be replaced with more sophisticated logging backends.
 */
public class DefaultPipelineLogger : PipelineLogger {
    private var currentSection: String? = null
    
    override fun info(message: String, metadata: Map<String, Any>) {
        log(LogLevel.INFO, message, metadata)
    }
    
    override fun warn(message: String, metadata: Map<String, Any>) {
        log(LogLevel.WARN, message, metadata)
    }
    
    override fun error(message: String, throwable: Throwable?, metadata: Map<String, Any>) {
        log(LogLevel.ERROR, message, metadata, throwable)
    }
    
    override fun debug(message: String, metadata: Map<String, Any>) {
        log(LogLevel.DEBUG, message, metadata)
    }
    
    override fun stdout(output: String) {
        println("[STDOUT] $output")
    }
    
    override fun stderr(output: String) {
        System.err.println("[STDERR] $output")
    }
    
    override fun startSection(name: String) {
        currentSection = name
        println("=== Starting Section: $name ===")
    }
    
    override fun endSection() {
        currentSection?.let { name ->
            println("=== Ending Section: $name ===")
        }
        currentSection = null
    }
    
    override fun logWithTimestamp(level: LogLevel, message: String, timestamp: Instant) {
        val formatted = "[$timestamp] [${level.name}] $message"
        currentSection?.let { section ->
            println("[$section] $formatted")
        } ?: println(formatted)
    }
    
    private fun log(level: LogLevel, message: String, metadata: Map<String, Any>, throwable: Throwable? = null) {
        val metadataStr = if (metadata.isNotEmpty()) " | ${metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}" else ""
        val formatted = "[${level.name}] $message$metadataStr"
        
        currentSection?.let { section ->
            println("[$section] $formatted")
        } ?: println(formatted)
        
        throwable?.printStackTrace()
    }
}