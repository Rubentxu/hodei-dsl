package dev.rubentxu.hodei.core.execution

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant

/**
 * BDD Specification for Configurable Logger System
 * 
 * Tests the complete logging system with level-based filtering,
 * environment-specific configuration, and structured output formatting.
 */
class ConfigurableLoggerSpec : BehaviorSpec({
    
    given("a configurable logger system") {
        
        `when`("using logger with DEBUG level") {
            then("should show all log messages") {
                val config = LoggerConfig(
                    level = LogLevel.DEBUG,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                // Capture output
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    logger.debug("Debug message")
                    logger.info("Info message") 
                    logger.warn("Warning message")
                    logger.error("Error message")
                    
                    val output = outputStream.toString()
                    output shouldContain "Debug message"
                    output shouldContain "Info message"
                    output shouldContain "Warning message"
                    output shouldContain "Error message"
                } finally {
                    System.setOut(originalOut)
                }
            }
        }
        
        `when`("using logger with INFO level") {
            then("should show INFO, WARN, and ERROR messages only") {
                val config = LoggerConfig(
                    level = LogLevel.INFO,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    logger.debug("Debug message")
                    logger.info("Info message")
                    logger.warn("Warning message")
                    logger.error("Error message")
                    
                    val output = outputStream.toString()
                    output shouldNotContain "Debug message"
                    output shouldContain "Info message"
                    output shouldContain "Warning message"
                    output shouldContain "Error message"
                } finally {
                    System.setOut(originalOut)
                }
            }
        }
        
        `when`("using logger with ERROR level") {
            then("should show ERROR messages only") {
                val config = LoggerConfig(
                    level = LogLevel.ERROR,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    logger.debug("Debug message")
                    logger.info("Info message")
                    logger.warn("Warning message")
                    logger.error("Error message")
                    
                    val output = outputStream.toString()
                    output shouldNotContain "Debug message"
                    output shouldNotContain "Info message"
                    output shouldNotContain "Warning message"
                    output shouldContain "Error message"
                } finally {
                    System.setOut(originalOut)
                }
            }
        }
        
        `when`("using structured JSON format") {
            then("should output properly formatted JSON") {
                val config = LoggerConfig(
                    level = LogLevel.INFO,
                    format = LogFormat.JSON,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    logger.info("Test message", mapOf("key1" to "value1", "key2" to 42))
                    
                    val output = outputStream.toString()
                    output shouldContain "\"level\":\"INFO\""
                    output shouldContain "\"message\":\"Test message\""
                    output shouldContain "\"key1\":\"value1\""
                    output shouldContain "\"key2\":42"
                    output shouldContain "\"timestamp\""
                } finally {
                    System.setOut(originalOut)
                }
            }
        }
        
        `when`("using logger with sections") {
            then("should format sections correctly") {
                val config = LoggerConfig(
                    level = LogLevel.INFO,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    logger.startSection("Build Phase")
                    logger.info("Building project...")
                    logger.endSection()
                    
                    val output = outputStream.toString()
                    output shouldContain "=== Starting Section: Build Phase ==="
                    output shouldContain "[Build Phase]"
                    output shouldContain "[INFO] Building project..."
                    output shouldContain "=== Ending Section: Build Phase ==="
                } finally {
                    System.setOut(originalOut)
                }
            }
        }
        
        `when`("using factory with environment-specific configuration") {
            then("should create logger for development environment") {
                val factory = ConfigurablePipelineLoggerFactory()
                val devLogger = factory.createForEnvironment("development")
                
                devLogger shouldNotBe null
                // Development logger should be verbose (DEBUG level)
            }
            
            then("should create logger for production environment") {
                val factory = ConfigurablePipelineLoggerFactory()
                val prodLogger = factory.createForEnvironment("production")
                
                prodLogger shouldNotBe null
                // Production logger should be minimal (INFO level, JSON format)
            }
            
            then("should create logger for test environment") {
                val factory = ConfigurablePipelineLoggerFactory()
                val testLogger = factory.createForEnvironment("test")
                
                testLogger shouldNotBe null
                // Test logger should be quiet (ERROR level only)
            }
        }
        
        `when`("handling metadata and timestamps") {
            then("should include metadata in log messages") {
                val config = LoggerConfig(
                    level = LogLevel.INFO,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    val metadata = mapOf(
                        "stage" to "test",
                        "step" to "unit-tests",
                        "duration" to "2.5s"
                    )
                    logger.info("Tests completed", metadata)
                    
                    val output = outputStream.toString()
                    output shouldContain "Tests completed"
                    output shouldContain "stage=test"
                    output shouldContain "step=unit-tests"
                    output shouldContain "duration=2.5s"
                } finally {
                    System.setOut(originalOut)
                }
            }
            
            then("should support custom timestamps") {
                val config = LoggerConfig(
                    level = LogLevel.INFO,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val outputStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputStream)
                val originalOut = System.out
                System.setOut(printStream)
                
                try {
                    val customTime = Instant.parse("2023-12-25T10:15:30Z")
                    logger.logWithTimestamp(LogLevel.INFO, "Custom timestamp test", customTime)
                    
                    val output = outputStream.toString()
                    output shouldContain "2023-12-25T10:15:30Z"
                    output shouldContain "Custom timestamp test"
                } finally {
                    System.setOut(originalOut)
                }
            }
        }
        
        `when`("handling stdout and stderr separately") {
            then("should route stdout and stderr correctly") {
                val config = LoggerConfig(
                    level = LogLevel.DEBUG,
                    format = LogFormat.SIMPLE,
                    destination = LogDestination.CONSOLE
                )
                val logger = ConfigurablePipelineLogger(config)
                
                val stdoutStream = ByteArrayOutputStream()
                val stderrStream = ByteArrayOutputStream()
                val originalOut = System.out
                val originalErr = System.err
                
                System.setOut(PrintStream(stdoutStream))
                System.setErr(PrintStream(stderrStream))
                
                try {
                    logger.stdout("Standard output message")
                    logger.stderr("Standard error message")
                    
                    val stdoutOutput = stdoutStream.toString()
                    val stderrOutput = stderrStream.toString()
                    
                    stdoutOutput shouldContain "[STDOUT] Standard output message"
                    stderrOutput shouldContain "[STDERR] Standard error message"
                } finally {
                    System.setOut(originalOut)
                    System.setErr(originalErr)
                }
            }
        }
    }
})