package dev.rubentxu.hodei.core.execution

import java.nio.file.Path
import java.time.Instant

/**
 * Execution context interface
 * 
 * Placeholder interface for execution context that will be fully implemented
 * in subsequent tasks. Currently provides minimal contract for domain model tests.
 */
public interface ExecutionContext {
    public val workDir: Path
    public val environment: Map<String, String>
    public val executionId: String
    public val buildId: String
    
    public companion object {
        /**
         * Creates default execution context for testing
         */
        public fun default(): ExecutionContext = DefaultExecutionContext()
    }
}

/**
 * Default implementation for testing purposes
 */
internal class DefaultExecutionContext : ExecutionContext {
    override val workDir: Path = Path.of(System.getProperty("user.dir"))
    override val environment: Map<String, String> = System.getenv()
    override val executionId: String = "test-execution-${System.currentTimeMillis()}"
    override val buildId: String = "test-build-1"
}