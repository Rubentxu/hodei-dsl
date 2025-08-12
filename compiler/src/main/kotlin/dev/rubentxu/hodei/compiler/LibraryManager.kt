package dev.rubentxu.hodei.compiler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Advanced library manager for sophisticated dependency management
 * 
 * Provides intelligent caching, parallel compilation, dependency tracking,
 * and hot-reload capabilities for external Gradle libraries.
 */
public class LibraryManager(
    private val gradleCompiler: GradleCompiler,
    private val cacheDir: Path = Files.createTempDirectory("hodei-library-cache"),
    private val maxConcurrentBuilds: Int = Runtime.getRuntime().availableProcessors()
) {
    
    private val libraryCache = ConcurrentHashMap<String, CachedLibrary>()
    private val compilationSemaphore = Channel<Unit>(maxConcurrentBuilds)
    private val _compilationStatus = MutableStateFlow<Map<String, CompilationStatus>>(emptyMap())
    private val compilationCounter = AtomicLong(0)
    
    init {
        // Initialize semaphore
        repeat(maxConcurrentBuilds) {
            compilationSemaphore.trySend(Unit)
        }
        
        // Ensure cache directory exists
        Files.createDirectories(cacheDir)
    }
    
    /**
     * Reactive compilation status for monitoring
     */
    public val compilationStatus: StateFlow<Map<String, CompilationStatus>> = _compilationStatus.asStateFlow()
    
    /**
     * Resolves multiple libraries with intelligent dependency management and parallel compilation
     */
    public suspend fun resolveLibraries(
        configurations: List<LibraryConfiguration>
    ): LibraryResolutionResult = coroutineScope {
        
        if (configurations.isEmpty()) {
            return@coroutineScope LibraryResolutionResult.Success(emptyMap())
        }
        
        try {
            // Phase 1: Dependency analysis
            val dependencyGraph = analyzeDependencies(configurations)
            val compilationOrder = topologicalSort(dependencyGraph)
            
            // Phase 2: Cache validation
            val (cachedLibraries, librariesToCompile) = validateCache(compilationOrder)
            
            // Phase 3: Parallel compilation of libraries that need building
            val compiledLibraries = if (librariesToCompile.isNotEmpty()) {
                compileLibrariesInParallel(librariesToCompile)
            } else {
                emptyMap()
            }
            
            // Phase 4: Combine cached and newly compiled libraries
            val allLibraries = cachedLibraries + compiledLibraries
            
            // Check for any compilation failures
            val failures = allLibraries.values.filterIsInstance<LibraryCompilationResult.Failure>()
            if (failures.isNotEmpty()) {
                return@coroutineScope LibraryResolutionResult.Failure(
                    error = "Library compilation failed: ${failures.joinToString { it.error }}",
                    failedLibraries = failures
                )
            }
            
            // Success - return all successfully compiled libraries
            val successfulLibraries = allLibraries.values
                .filterIsInstance<LibraryCompilationResult.Success>()
                .associateBy { it.metadata.configuration.name }
            
            LibraryResolutionResult.Success(
                libraries = successfulLibraries,
                metrics = LibraryResolutionMetrics(
                    totalLibraries = configurations.size,
                    compiledFromCache = cachedLibraries.size,
                    compiledFromSource = compiledLibraries.size,
                    resolutionTime = System.currentTimeMillis()
                )
            )
            
        } catch (e: Exception) {
            LibraryResolutionResult.Failure(
                error = "Library resolution failed: ${e.message}",
                cause = e
            )
        }
    }
    
    /**
     * Gets a cached library if available and up-to-date
     */
    public suspend fun getCachedLibrary(config: LibraryConfiguration): LibraryCompilationResult.Success? {
        val cacheKey = config.cacheKey()
        val cached = libraryCache[cacheKey] ?: return null
        
        // Validate cache is still valid
        val currentHash = calculateSourceHash(File(config.sourcePath))
        if (cached.result.metadata.sourceHash == currentHash && cached.result.metadata.jarFile.exists()) {
            return cached.result.copy(fromCache = true)
        }
        
        // Cache is stale, remove it
        libraryCache.remove(cacheKey)
        return null
    }
    
    /**
     * Builds a single library with progress tracking
     */
    public suspend fun buildLibrary(config: LibraryConfiguration): LibraryCompilationResult {
        val compilationId = compilationCounter.incrementAndGet()
        
        updateCompilationStatus(config.name, CompilationStatus.InProgress(
            startedAt = Instant.now(),
            compilationId = compilationId
        ))
        
        return try {
            // Acquire compilation slot
            compilationSemaphore.receive()
            
            val result = gradleCompiler.compileAndJar(config.sourcePath, config)
            
            when (result) {
                is LibraryCompilationResult.Success -> {
                    // Cache successful result
                    val cachedLibrary = CachedLibrary(
                        result = result,
                        cachedAt = Instant.now()
                    )
                    libraryCache[config.cacheKey()] = cachedLibrary
                    
                    updateCompilationStatus(config.name, CompilationStatus.Completed(
                        completedAt = Instant.now(),
                        compilationId = compilationId,
                        success = true
                    ))
                    
                    result
                }
                
                is LibraryCompilationResult.Failure -> {
                    updateCompilationStatus(config.name, CompilationStatus.Completed(
                        completedAt = Instant.now(),
                        compilationId = compilationId,
                        success = false,
                        error = result.error
                    ))
                    
                    result
                }
            }
            
        } catch (e: Exception) {
            updateCompilationStatus(config.name, CompilationStatus.Completed(
                completedAt = Instant.now(),
                compilationId = compilationId,
                success = false,
                error = e.message ?: "Unknown error"
            ))
            
            LibraryCompilationResult.Failure(
                configuration = config,
                error = "Build failed: ${e.message}",
                cause = e
            )
            
        } finally {
            // Release compilation slot
            compilationSemaphore.trySend(Unit)
        }
    }
    
    /**
     * Enables hot-reload by watching library source changes
     */
    public suspend fun enableHotReload(
        configurations: List<LibraryConfiguration>,
        onLibraryChanged: suspend (LibraryConfiguration, LibraryCompilationResult) -> Unit
    ): Job = coroutineScope {
        
        launch {
            // Simple polling-based hot reload (in production, use file watchers)
            val sourceHashes = configurations.associateWith { config ->
                try {
                    calculateSourceHash(File(config.sourcePath))
                } catch (e: Exception) {
                    ""
                }
            }.toMutableMap()
            
            while (isActive) {
                delay(2000) // Poll every 2 seconds
                
                for (config in configurations) {
                    try {
                        val currentHash = calculateSourceHash(File(config.sourcePath))
                        val lastHash = sourceHashes[config]
                        
                        if (currentHash != lastHash && currentHash.isNotEmpty()) {
                            sourceHashes[config] = currentHash
                            
                            // Source changed - recompile
                            val result = buildLibrary(config)
                            onLibraryChanged(config, result)
                        }
                        
                    } catch (e: Exception) {
                        // Ignore errors during hot reload monitoring
                        continue
                    }
                }
            }
        }
    }
    
    /**
     * Clears the entire library cache
     */
    public fun clearCache() {
        libraryCache.clear()
        _compilationStatus.value = emptyMap()
    }
    
    /**
     * Gets comprehensive cache statistics
     */
    public fun getCacheStatistics(): LibraryCacheStatistics {
        val totalSize = libraryCache.values.sumOf { 
            it.result.metadata.jarFile.length() 
        }
        
        val libraryStats = libraryCache.values.groupBy { 
            it.result.metadata.configuration.name 
        }.mapValues { (_, cached) ->
            cached.maxByOrNull { it.cachedAt }!!
        }
        
        return LibraryCacheStatistics(
            totalEntries = libraryCache.size,
            totalSizeBytes = totalSize,
            libraryStats = libraryStats,
            cacheHitRatio = calculateCacheHitRatio()
        )
    }
    
    // Private helper methods
    
    private fun analyzeDependencies(configurations: List<LibraryConfiguration>): Map<String, Set<String>> {
        // For now, assume no inter-library dependencies
        // In the future, this could parse build files to detect dependencies
        return configurations.associate { it.name to emptySet<String>() }
    }
    
    private fun topologicalSort(dependencyGraph: Map<String, Set<String>>): List<LibraryConfiguration> {
        // Simple topological sort - for now just return original order
        // In future versions, this would implement proper dependency ordering
        return dependencyGraph.keys.map { name ->
            LibraryConfiguration.simple(name, "/placeholder")
        }
    }
    
    private suspend fun validateCache(
        configurations: List<LibraryConfiguration>
    ): Pair<Map<String, LibraryCompilationResult>, List<LibraryConfiguration>> {
        
        val cached = mutableMapOf<String, LibraryCompilationResult>()
        val toCompile = mutableListOf<LibraryConfiguration>()
        
        for (config in configurations) {
            val cachedResult = getCachedLibrary(config)
            if (cachedResult != null) {
                cached[config.name] = cachedResult
            } else {
                toCompile.add(config)
            }
        }
        
        return cached to toCompile
    }
    
    private suspend fun compileLibrariesInParallel(
        configurations: List<LibraryConfiguration>
    ): Map<String, LibraryCompilationResult> = coroutineScope {
        
        configurations.map { config ->
            async {
                config.name to buildLibrary(config)
            }
        }.awaitAll().toMap()
    }
    
    private fun updateCompilationStatus(libraryName: String, status: CompilationStatus) {
        val currentStatus = _compilationStatus.value.toMutableMap()
        currentStatus[libraryName] = status
        _compilationStatus.value = currentStatus
    }
    
    private fun calculateSourceHash(sourceDir: File): String {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return ""
        }
        
        val md = MessageDigest.getInstance("SHA-256")
        
        sourceDir.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                when {
                    file.name.startsWith("build.gradle") -> true
                    file.name == "gradle.properties" -> true
                    file.name == "settings.gradle.kts" -> true
                    file.extension in setOf("kt", "java", "scala", "groovy") -> true
                    else -> false
                }
            }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                try {
                    md.update(file.readBytes())
                    md.update(file.absolutePath.toByteArray())
                } catch (e: Exception) {
                    // Skip files that can't be read
                }
            }
        
        val hash = md.digest()
        return hash.fold("") { str, byte -> str + "%02x".format(byte) }
    }
    
    private fun calculateCacheHitRatio(): Double {
        // This would be calculated based on actual usage statistics
        // For now, return a placeholder
        return 0.0
    }
}

/**
 * Cached library entry
 */
public data class CachedLibrary(
    val result: LibraryCompilationResult.Success,
    val cachedAt: Instant
)

/**
 * Compilation status for reactive monitoring
 */
public sealed class CompilationStatus {
    public data class InProgress(
        val startedAt: Instant,
        val compilationId: Long
    ) : CompilationStatus()
    
    public data class Completed(
        val completedAt: Instant,
        val compilationId: Long,
        val success: Boolean,
        val error: String? = null
    ) : CompilationStatus()
}

/**
 * Result of library resolution operation
 */
public sealed class LibraryResolutionResult {
    
    public abstract val isSuccess: Boolean
    
    public data class Success(
        val libraries: Map<String, LibraryCompilationResult.Success>,
        val metrics: LibraryResolutionMetrics? = null
    ) : LibraryResolutionResult() {
        override val isSuccess: Boolean = true
    }
    
    public data class Failure(
        val error: String,
        val failedLibraries: List<LibraryCompilationResult.Failure> = emptyList(),
        val cause: Throwable? = null
    ) : LibraryResolutionResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Metrics for library resolution performance
 */
public data class LibraryResolutionMetrics(
    val totalLibraries: Int,
    val compiledFromCache: Int,
    val compiledFromSource: Int,
    val resolutionTime: Long
) {
    val cacheHitRatio: Double
        get() = if (totalLibraries > 0) compiledFromCache.toDouble() / totalLibraries else 0.0
}

/**
 * Comprehensive cache statistics
 */
public data class LibraryCacheStatistics(
    val totalEntries: Int,
    val totalSizeBytes: Long,
    val libraryStats: Map<String, CachedLibrary>,
    val cacheHitRatio: Double
) {
    val totalSizeMB: Double
        get() = totalSizeBytes / (1024.0 * 1024.0)
}