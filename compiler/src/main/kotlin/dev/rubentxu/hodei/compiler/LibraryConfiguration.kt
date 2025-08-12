package dev.rubentxu.hodei.compiler

import java.time.Instant

/**
 * Configuration for external library compilation using Gradle
 * 
 * Defines the parameters needed to compile an external Gradle project
 * into a JAR that can be loaded dynamically into pipeline execution.
 */
public data class LibraryConfiguration(
    /**
     * Unique name for the library (used for caching and identification)
     */
    val name: String,
    
    /**
     * Path to the Gradle project source directory
     */
    val sourcePath: String,
    
    /**
     * Library version (semantic versioning recommended)
     */
    val version: String = "1.0.0",
    
    /**
     * Additional Gradle arguments for compilation
     */
    val gradleArgs: List<String> = listOf("clean", "build"),
    
    /**
     * Custom archive base name (overrides default project name)
     */
    val customArchiveName: String? = null
) {
    
    /**
     * Generates a unique cache key for this library configuration
     */
    public fun cacheKey(): String {
        return "$name-$version-${sourcePath.hashCode()}"
    }
    
    public companion object {
        /**
         * Creates a simple library configuration with minimal setup
         */
        public fun simple(name: String, sourcePath: String): LibraryConfiguration {
            return LibraryConfiguration(
                name = name,
                sourcePath = sourcePath
            )
        }
        
        /**
         * Creates a library configuration for development with verbose output
         */
        public fun development(name: String, sourcePath: String): LibraryConfiguration {
            return LibraryConfiguration(
                name = name,
                sourcePath = sourcePath,
                gradleArgs = listOf("clean", "build", "--info", "--stacktrace")
            )
        }
    }
}

/**
 * Metadata about a compiled library
 */
public data class LibraryMetadata(
    val configuration: LibraryConfiguration,
    val jarFile: java.io.File,
    val compiledAt: Instant,
    val sourceHash: String,
    val compilationTimeMs: Long
) {
    /**
     * Checks if the library is up to date based on source hash
     */
    public fun isUpToDate(currentSourceHash: String): Boolean {
        return sourceHash == currentSourceHash && jarFile.exists()
    }
}

/**
 * Result of library compilation operation
 */
public sealed class LibraryCompilationResult {
    
    public abstract val isSuccess: Boolean
    
    public data class Success(
        val metadata: LibraryMetadata,
        val fromCache: Boolean = false
    ) : LibraryCompilationResult() {
        override val isSuccess: Boolean = true
    }
    
    public data class Failure(
        val configuration: LibraryConfiguration,
        val error: String,
        val cause: Throwable? = null
    ) : LibraryCompilationResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Exception thrown when library source is not found
 */
public class SourceNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when compiled JAR file is not found
 */
public class JarFileNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when Gradle compilation fails
 */
public class GradleCompilationException(message: String, cause: Throwable? = null) : Exception(message, cause)