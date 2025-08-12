package dev.rubentxu.hodei.compiler

import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.net.URLClassLoader
import java.net.URL

/**
 * Hybrid compiler that combines Kotlin script compilation with Gradle library compilation
 * 
 * This compiler can compile both Pipeline DSL scripts and external Gradle libraries,
 * creating an integrated compilation pipeline that supports dynamic library loading.
 */
public class HybridCompiler(
    public val scriptCompiler: ScriptCompiler,
    private val gradleCompiler: GradleCompiler,
    private val libraryCache: LibraryCache = InMemoryLibraryCache()
) {
    
    /**
     * Compiles a Pipeline DSL script along with its external library dependencies
     */
    public suspend fun compileWithLibraries(
        scriptContent: String,
        scriptName: String,
        libraries: List<LibraryConfiguration> = emptyList()
    ): HybridCompilationResult = coroutineScope {
        try {
            val startTime = Instant.now()
            
            // Phase 1: Compile libraries in parallel
            val libraryResults = if (libraries.isNotEmpty()) {
                compileLibrariesParallel(libraries)
            } else {
                emptyMap()
            }
            
            // Check for library compilation failures
            val failedLibraries = libraryResults.values.filterIsInstance<LibraryCompilationResult.Failure>()
            if (failedLibraries.isNotEmpty()) {
                return@coroutineScope HybridCompilationResult.Failure(
                    error = "Library compilation failed: ${failedLibraries.joinToString { it.error }}",
                    libraryErrors = failedLibraries
                )
            }
            
            // Phase 2: Create enhanced script configuration with library imports
            val successfulLibraries = libraryResults.values
                .filterIsInstance<LibraryCompilationResult.Success>()
                .associateBy { it.metadata.configuration.name }
            
            val enhancedScriptConfig = createEnhancedScriptConfig(successfulLibraries.values.toList())
            val enhancedScriptCompiler = ScriptCompiler(enhancedScriptConfig)
            
            // Phase 3: Compile script with enhanced configuration
            val scriptResult = enhancedScriptCompiler.compile(scriptContent, scriptName)
            
            val endTime = Instant.now()
            val duration = java.time.Duration.between(startTime, endTime)
            
            when (scriptResult) {
                is CompilationResult.Success -> {
                    // Phase 4: Create hybrid class loader
                    val libraryJars = successfulLibraries.values.map { it.metadata.jarFile }
                    val hybridClassLoader = createHybridClassLoader(libraryJars)
                    
                    HybridCompilationResult.Success(
                        scriptResult = scriptResult,
                        libraryResults = successfulLibraries,
                        classLoader = hybridClassLoader,
                        compilationMetrics = CompilationMetrics(
                            totalDuration = duration,
                            scriptCompilationDuration = java.time.Duration.between(startTime, endTime),
                            libraryCount = libraries.size,
                            cacheHits = successfulLibraries.values.count { it.fromCache }
                        )
                    )
                }
                
                is CompilationResult.Failure -> {
                    HybridCompilationResult.Failure(
                        error = "Script compilation failed: ${scriptResult.errors.joinToString { it.message }}",
                        scriptErrors = scriptResult.errors
                    )
                }
            }
            
        } catch (e: Exception) {
            HybridCompilationResult.Failure(
                error = "Hybrid compilation failed: ${e.message}",
                cause = e
            )
        }
    }
    
    /**
     * Compiles multiple libraries in parallel for optimal performance
     */
    private suspend fun compileLibrariesParallel(
        libraries: List<LibraryConfiguration>
    ): Map<String, LibraryCompilationResult> = coroutineScope {
        
        libraries.map { config ->
            async {
                val cachedResult = libraryCache.get(config.cacheKey())
                if (cachedResult != null && cachedResult.metadata.jarFile.exists()) {
                    config.name to LibraryCompilationResult.Success(
                        metadata = cachedResult.metadata,
                        fromCache = true
                    )
                } else {
                    val result = gradleCompiler.compileAndJar(config.sourcePath, config)
                    
                    // Cache successful compilations
                    if (result is LibraryCompilationResult.Success) {
                        libraryCache.put(config.cacheKey(), result)
                    }
                    
                    config.name to result
                }
            }
        }.awaitAll().toMap()
    }
    
    /**
     * Creates enhanced script configuration with library-specific imports
     */
    private fun createEnhancedScriptConfig(
        successfulLibraries: List<LibraryCompilationResult.Success>
    ): ScriptConfig {
        val baseImports = setOf(
            "dev.rubentxu.hodei.core.dsl.*",
            "dev.rubentxu.hodei.core.dsl.builders.*", 
            "dev.rubentxu.hodei.core.domain.model.*",
            "dev.rubentxu.hodei.core.dsl.pipeline"
        )
        
        // For now, we'll use base imports. In future versions, we could
        // analyze JARs to discover and auto-import library functions
        val enhancedImports = baseImports + successfulLibraries.flatMap { result ->
            // Future: analyze JAR to discover importable functions
            emptyList<String>()
        }
        
        return ScriptConfig(
            defaultImports = enhancedImports,
            enableCache = true
        )
    }
    
    /**
     * Creates a hybrid class loader that includes both script and library classes
     */
    private fun createHybridClassLoader(libraryJars: List<File>): ClassLoader {
        val urls = libraryJars.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, Thread.currentThread().contextClassLoader)
    }
    
    /**
     * Clears the library cache (useful for development/testing)
     */
    public fun clearCache() {
        libraryCache.clear()
    }
    
    /**
     * Gets cache statistics
     */
    public fun getCacheStats(): LibraryCacheStats {
        return libraryCache.getStats()
    }
}

/**
 * Result of hybrid compilation (script + libraries)
 */
public sealed class HybridCompilationResult {
    
    public abstract val isSuccess: Boolean
    
    public data class Success(
        val scriptResult: CompilationResult.Success,
        val libraryResults: Map<String, LibraryCompilationResult.Success>,
        val classLoader: ClassLoader,
        val compilationMetrics: CompilationMetrics
    ) : HybridCompilationResult() {
        override val isSuccess: Boolean = true
        
        /**
         * Gets all library JAR files
         */
        val libraryJars: List<File> 
            get() = libraryResults.values.map { it.metadata.jarFile }
    }
    
    public data class Failure(
        val error: String,
        val scriptErrors: List<ScriptError> = emptyList(),
        val libraryErrors: List<LibraryCompilationResult.Failure> = emptyList(),
        val cause: Throwable? = null
    ) : HybridCompilationResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Compilation performance metrics
 */
public data class CompilationMetrics(
    val totalDuration: java.time.Duration,
    val scriptCompilationDuration: java.time.Duration,
    val libraryCount: Int,
    val cacheHits: Int
) {
    /**
     * Cache hit ratio (0.0 to 1.0)
     */
    val cacheHitRatio: Double 
        get() = if (libraryCount > 0) cacheHits.toDouble() / libraryCount else 0.0
        
    /**
     * Average library compilation time (if any were compiled)
     */
    val averageLibraryCompilationTime: java.time.Duration?
        get() = if (libraryCount > cacheHits) {
            val compiledCount = libraryCount - cacheHits
            java.time.Duration.ofMillis(
                (totalDuration.toMillis() - scriptCompilationDuration.toMillis()) / compiledCount
            )
        } else null
}

/**
 * Simple in-memory library cache interface
 */
public interface LibraryCache {
    public fun get(key: String): LibraryCompilationResult.Success?
    public fun put(key: String, result: LibraryCompilationResult.Success)
    public fun clear()
    public fun getStats(): LibraryCacheStats
}

/**
 * Cache statistics
 */
public data class LibraryCacheStats(
    val size: Int,
    val hits: Long,
    val misses: Long
) {
    val hitRatio: Double 
        get() = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
}

/**
 * Simple in-memory implementation of LibraryCache
 */
private class InMemoryLibraryCache : LibraryCache {
    private val cache = mutableMapOf<String, LibraryCompilationResult.Success>()
    private var hits = 0L
    private var misses = 0L
    
    override fun get(key: String): LibraryCompilationResult.Success? {
        val result = cache[key]
        if (result != null) {
            hits++
        } else {
            misses++
        }
        return result
    }
    
    override fun put(key: String, result: LibraryCompilationResult.Success) {
        cache[key] = result
    }
    
    override fun clear() {
        cache.clear()
        hits = 0L
        misses = 0L
    }
    
    override fun getStats(): LibraryCacheStats {
        return LibraryCacheStats(
            size = cache.size,
            hits = hits,
            misses = misses
        )
    }
}