package dev.rubentxu.hodei.compiler

import kotlin.script.experimental.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.file.*
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Advanced intelligent caching system for Pipeline DSL compilation
 * 
 * Provides multi-level caching with automatic invalidation, performance monitoring,
 * cache warming, and sophisticated eviction policies.
 */
public class CacheManager(
    private val cacheRoot: Path = Files.createTempDirectory("hodei-cache"),
    private val maxCacheSize: Long = 1024 * 1024 * 1024, // 1GB default
    private val maxCacheAge: Duration = 7.hours,
    private val backgroundCleanupInterval: Duration = 30.minutes
) {
    
    // Cache statistics
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val cacheEvictions = AtomicLong(0)
    
    // Cache storage
    private val scriptCache = ConcurrentHashMap<String, CachedScript>()
    private val libraryCache = ConcurrentHashMap<String, CachedLibraryEntry>()
    private val dependencyCache = ConcurrentHashMap<String, CachedDependencyGraph>()
    
    // Cache monitoring  
    private val _cacheStatus = MutableStateFlow<CacheStatus>(CacheStatus.Idle)
    public val cacheStatus: StateFlow<CacheStatus> = _cacheStatus.asStateFlow()
    
    // Background cleanup job
    private var backgroundCleanupJob: Job? = null
    
    init {
        // Ensure cache directories exist
        Files.createDirectories(cacheRoot.resolve("scripts"))
        Files.createDirectories(cacheRoot.resolve("libraries"))
        Files.createDirectories(cacheRoot.resolve("dependencies"))
        
        // Start background cleanup
        startBackgroundCleanup()
    }
    
    // --- Script Caching ---
    
    /**
     * Gets cached script compilation result if available and valid
     */
    public suspend fun getCachedScript(
        scriptContent: String,
        scriptName: String,
        dependencies: List<String> = emptyList()
    ): CompilationResult.Success? {
        val cacheKey = generateScriptCacheKey(scriptContent, scriptName, dependencies)
        val cached = scriptCache[cacheKey]
        
        if (cached != null && isCacheEntryValid(cached.cachedAt, cached.sourceHash, scriptContent)) {
            cacheHits.incrementAndGet()
            return cached.result.copy(fromCache = true)
        }
        
        if (cached != null) {
            // Cache is stale - remove it
            scriptCache.remove(cacheKey)
            cacheEvictions.incrementAndGet()
        }
        
        cacheMisses.incrementAndGet()
        return null
    }
    
    /**
     * Caches a successful script compilation result
     */
    public suspend fun cacheScript(
        scriptContent: String,
        scriptName: String,
        dependencies: List<String>,
        result: CompilationResult.Success
    ) {
        val cacheKey = generateScriptCacheKey(scriptContent, scriptName, dependencies)
        val sourceHash = calculateContentHash(scriptContent)
        
        val cachedScript = CachedScript(
            result = result,
            cachedAt = Instant.now(),
            sourceHash = sourceHash,
            accessCount = AtomicLong(1),
            lastAccessed = Instant.now()
        )
        
        scriptCache[cacheKey] = cachedScript
        
        // Trigger cleanup if cache is getting full
        if (shouldPerformCleanup()) {
            performCacheCleanup()
        }
    }
    
    // --- Library Caching ---
    
    /**
     * Gets cached library compilation result if available and valid
     */
    public suspend fun getCachedLibrary(config: LibraryConfiguration): LibraryCompilationResult.Success? {
        val cacheKey = config.cacheKey()
        val cached = libraryCache[cacheKey]
        
        if (cached != null && isLibraryCacheValid(cached, config)) {
            cacheHits.incrementAndGet()
            cached.accessCount.incrementAndGet()
            cached.lastAccessed = Instant.now()
            return cached.result.copy(fromCache = true)
        }
        
        if (cached != null) {
            // Cache is stale - remove it
            libraryCache.remove(cacheKey)
            cacheEvictions.incrementAndGet()
        }
        
        cacheMisses.incrementAndGet()
        return null
    }
    
    /**
     * Caches a successful library compilation result
     */
    public suspend fun cacheLibrary(
        config: LibraryConfiguration,
        result: LibraryCompilationResult.Success
    ) {
        val cacheKey = config.cacheKey()
        
        val cachedLibrary = CachedLibraryEntry(
            result = result,
            cachedAt = Instant.now(),
            accessCount = AtomicLong(1),
            lastAccessed = Instant.now()
        )
        
        libraryCache[cacheKey] = cachedLibrary
        
        if (shouldPerformCleanup()) {
            performCacheCleanup()
        }
    }
    
    // --- Dependency Graph Caching ---
    
    /**
     * Gets cached dependency analysis if available and valid
     */
    public suspend fun getCachedDependencyGraph(
        configurations: List<LibraryConfiguration>
    ): DependencyAnalysisResult? {
        val cacheKey = generateDependencyCacheKey(configurations)
        val cached = dependencyCache[cacheKey]
        
        if (cached != null && isDependencyCacheValid(cached, configurations)) {
            cacheHits.incrementAndGet()
            return cached.result
        }
        
        if (cached != null) {
            dependencyCache.remove(cacheKey)
            cacheEvictions.incrementAndGet()
        }
        
        cacheMisses.incrementAndGet()
        return null
    }
    
    /**
     * Caches dependency analysis result
     */
    public suspend fun cacheDependencyGraph(
        configurations: List<LibraryConfiguration>,
        result: DependencyAnalysisResult
    ) {
        val cacheKey = generateDependencyCacheKey(configurations)
        val configHash = calculateConfigurationsHash(configurations)
        
        val cached = CachedDependencyGraph(
            result = result,
            cachedAt = Instant.now(),
            configurationHash = configHash
        )
        
        dependencyCache[cacheKey] = cached
    }
    
    // --- Cache Warming ---
    
    /**
     * Warms up the cache by pre-compiling frequently used libraries
     */
    public suspend fun warmupCache(
        commonLibraries: List<LibraryConfiguration>,
        commonScripts: List<Pair<String, String>> = emptyList()
    ): Unit = coroutineScope {
        
        _cacheStatus.value = CacheStatus.Warming
        
        try {
            // Warm up library cache
            commonLibraries.map { config ->
                async {
                    val cached = getCachedLibrary(config)
                    if (cached == null) {
                        // Library not cached - this would trigger compilation
                        // In real implementation, integrate with LibraryManager
                        val mockResult = createMockLibraryResult(config)
                        cacheLibrary(config, mockResult)
                    }
                }
            }.awaitAll()
            
            // Warm up script cache
            commonScripts.map { (content, name) ->
                async {
                    val cached = getCachedScript(content, name)
                    if (cached == null) {
                        // Script not cached - would trigger compilation
                        val mockResult = createMockScriptResult(name)
                        cacheScript(content, name, emptyList(), mockResult)
                    }
                }
            }.awaitAll()
            
            _cacheStatus.value = CacheStatus.Ready
            
        } catch (e: Exception) {
            _cacheStatus.value = CacheStatus.Error("Cache warmup failed: ${e.message}")
        }
    }
    
    // --- Cache Management ---
    
    /**
     * Clears all caches
     */
    public fun clearCache() {
        scriptCache.clear()
        libraryCache.clear()
        dependencyCache.clear()
        cacheHits.set(0)
        cacheMisses.set(0)
        cacheEvictions.set(0)
        _cacheStatus.value = CacheStatus.Idle
    }
    
    /**
     * Performs intelligent cache cleanup based on LRU and size limits
     */
    public suspend fun performCacheCleanup() {
        _cacheStatus.value = CacheStatus.Cleaning
        
        val currentSize = calculateTotalCacheSize()
        
        if (currentSize > maxCacheSize) {
            // Evict least recently used entries
            evictLeastRecentlyUsed()
        }
        
        // Remove expired entries
        removeExpiredEntries()
        
        _cacheStatus.value = CacheStatus.Ready
    }
    
    /**
     * Gets comprehensive cache statistics
     */
    public fun getCacheStatistics(): CacheStatistics {
        val totalHits = cacheHits.get()
        val totalMisses = cacheMisses.get()
        val totalRequests = totalHits + totalMisses
        
        return CacheStatistics(
            scriptCacheEntries = scriptCache.size,
            libraryCacheEntries = libraryCache.size,
            dependencyCacheEntries = dependencyCache.size,
            totalCacheSize = calculateTotalCacheSize(),
            cacheHits = totalHits,
            cacheMisses = totalMisses,
            cacheEvictions = cacheEvictions.get(),
            hitRatio = if (totalRequests > 0) totalHits.toDouble() / totalRequests else 0.0,
            oldestEntry = findOldestCacheEntry(),
            newestEntry = findNewestCacheEntry()
        )
    }
    
    /**
     * Exports cache analysis report for optimization
     */
    public fun exportCacheAnalysis(): CacheAnalysisReport {
        val stats = getCacheStatistics()
        
        val hotSpots = scriptCache.values
            .sortedByDescending { it.accessCount.get() }
            .take(10)
            .map { cached ->
                CacheHotSpot(
                    key = "script-${cached.sourceHash.take(8)}",
                    accessCount = cached.accessCount.get(),
                    lastAccessed = cached.lastAccessed,
                    cacheAge = ChronoUnit.MINUTES.between(cached.cachedAt, Instant.now())
                )
            }
        
        val recommendations = generateCacheOptimizationRecommendations(stats)
        
        return CacheAnalysisReport(
            statistics = stats,
            hotSpots = hotSpots,
            recommendations = recommendations,
            generatedAt = Instant.now()
        )
    }
    
    // --- Private Helper Methods ---
    
    private fun startBackgroundCleanup() {
        backgroundCleanupJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                delay(backgroundCleanupInterval.inWholeMilliseconds)
                performCacheCleanup()
            }
        }
    }
    
    private fun generateScriptCacheKey(
        scriptContent: String,
        scriptName: String,
        dependencies: List<String>
    ): String {
        val contentHash = calculateContentHash(scriptContent)
        val depsHash = calculateContentHash(dependencies.sorted().joinToString(","))
        return "script-$scriptName-$contentHash-$depsHash"
    }
    
    private fun generateDependencyCacheKey(configurations: List<LibraryConfiguration>): String {
        val configString = configurations
            .sortedBy { it.name }
            .joinToString(",") { "${it.name}:${it.version}:${it.sourcePath}" }
        val hash = calculateContentHash(configString)
        return "deps-$hash"
    }
    
    private fun calculateContentHash(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(content.toByteArray())
        return hash.fold("") { str, byte -> str + "%02x".format(byte) }
    }
    
    private fun calculateConfigurationsHash(configurations: List<LibraryConfiguration>): String {
        val configString = configurations
            .sortedBy { it.name }
            .joinToString(",") { it.cacheKey() }
        return calculateContentHash(configString)
    }
    
    private fun isCacheEntryValid(cachedAt: Instant, sourceHash: String, currentContent: String): Boolean {
        val age = ChronoUnit.MINUTES.between(cachedAt, Instant.now())
        val isNotExpired = age < maxCacheAge.inWholeMinutes
        val isContentSame = sourceHash == calculateContentHash(currentContent)
        
        return isNotExpired && isContentSame
    }
    
    private fun isLibraryCacheValid(cached: CachedLibraryEntry, config: LibraryConfiguration): Boolean {
        val age = ChronoUnit.MINUTES.between(cached.cachedAt, Instant.now())
        val isNotExpired = age < maxCacheAge.inWholeMinutes
        val jarExists = cached.result.metadata.jarFile.exists()
        
        return isNotExpired && jarExists
    }
    
    private fun isDependencyCacheValid(
        cached: CachedDependencyGraph,
        configurations: List<LibraryConfiguration>
    ): Boolean {
        val age = ChronoUnit.MINUTES.between(cached.cachedAt, Instant.now())
        val isNotExpired = age < maxCacheAge.inWholeMinutes
        val currentHash = calculateConfigurationsHash(configurations)
        val isSameConfig = cached.configurationHash == currentHash
        
        return isNotExpired && isSameConfig
    }
    
    private fun shouldPerformCleanup(): Boolean {
        return calculateTotalCacheSize() > maxCacheSize * 0.8 // 80% threshold
    }
    
    private fun calculateTotalCacheSize(): Long {
        val scriptSize = scriptCache.values.sumOf { 
            it.result.scriptContent.length.toLong() 
        }
        val librarySize = libraryCache.values.sumOf { 
            it.result.metadata.jarFile.length() 
        }
        return scriptSize + librarySize
    }
    
    private fun evictLeastRecentlyUsed() {
        val allEntries = mutableListOf<Pair<String, Instant>>()
        
        // Collect all entries with their last access times
        scriptCache.forEach { (key, cached) ->
            allEntries.add(key to cached.lastAccessed)
        }
        libraryCache.forEach { (key, cached) ->
            allEntries.add(key to cached.lastAccessed)
        }
        
        // Sort by last accessed (oldest first) and remove 20% of entries
        val toRemove = allEntries
            .sortedBy { it.second }
            .take((allEntries.size * 0.2).toInt())
            .map { it.first }
        
        toRemove.forEach { key ->
            scriptCache.remove(key)
            libraryCache.remove(key)
            cacheEvictions.incrementAndGet()
        }
    }
    
    private fun removeExpiredEntries() {
        val now = Instant.now()
        val maxAgeMinutes = maxCacheAge.inWholeMinutes
        
        scriptCache.entries.removeIf { (_, cached) ->
            ChronoUnit.MINUTES.between(cached.cachedAt, now) > maxAgeMinutes
        }
        
        libraryCache.entries.removeIf { (_, cached) ->
            ChronoUnit.MINUTES.between(cached.cachedAt, now) > maxAgeMinutes
        }
    }
    
    private fun findOldestCacheEntry(): Instant? {
        val oldestScript = scriptCache.values.minByOrNull { it.cachedAt }?.cachedAt
        val oldestLibrary = libraryCache.values.minByOrNull { it.cachedAt }?.cachedAt
        
        return listOfNotNull(oldestScript, oldestLibrary).minOrNull()
    }
    
    private fun findNewestCacheEntry(): Instant? {
        val newestScript = scriptCache.values.maxByOrNull { it.cachedAt }?.cachedAt
        val newestLibrary = libraryCache.values.maxByOrNull { it.cachedAt }?.cachedAt
        
        return listOfNotNull(newestScript, newestLibrary).maxOrNull()
    }
    
    private fun generateCacheOptimizationRecommendations(stats: CacheStatistics): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (stats.hitRatio < 0.5) {
            recommendations.add("Cache hit ratio is low (${String.format("%.1f", stats.hitRatio * 100)}%). Consider cache warming for frequently used libraries.")
        }
        
        if (stats.totalCacheSize > maxCacheSize * 0.9) {
            recommendations.add("Cache size is near limit. Consider increasing maxCacheSize or reducing maxCacheAge.")
        }
        
        if (stats.cacheEvictions > stats.cacheHits * 0.1) {
            recommendations.add("High eviction rate detected. Cache may be too small or entries are expiring too quickly.")
        }
        
        return recommendations
    }
    
    private fun createMockScriptResult(scriptName: String): CompilationResult.Success {
        // Mock implementation for cache warming
        // Create a minimal CompiledScript mock
        val mockScript = object : CompiledScript {
            override val compilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration()
            override val sourceLocationId: String? = null
            override suspend fun getClass(scriptEvaluationConfiguration: ScriptEvaluationConfiguration?): ResultWithDiagnostics<kotlin.reflect.KClass<*>> {
                return ResultWithDiagnostics.Success(String::class)
            }
        }
        
        return CompilationResult.Success(
            scriptContent = "mock-content",
            scriptName = scriptName,
            compiledScript = mockScript,
            availablePlugins = emptyList(),
            activeImports = emptySet(),
            fromCache = false
        )
    }
    
    private fun createMockLibraryResult(config: LibraryConfiguration): LibraryCompilationResult.Success {
        // Mock implementation for cache warming
        val jarFile = File.createTempFile("mock-${config.name}", ".jar")
        val metadata = LibraryMetadata(
            configuration = config,
            jarFile = jarFile,
            compiledAt = Instant.now(),
            sourceHash = "mock-hash",
            compilationTimeMs = 0
        )
        
        return LibraryCompilationResult.Success(
            metadata = metadata,
            fromCache = false
        )
    }
    
    public fun shutdown() {
        backgroundCleanupJob?.cancel()
    }
}

// --- Supporting Data Classes ---

/**
 * Cached script entry with access tracking
 */
public data class CachedScript(
    val result: CompilationResult.Success,
    val cachedAt: Instant,
    val sourceHash: String,
    val accessCount: AtomicLong,
    var lastAccessed: Instant
)

/**
 * Cached library entry with access tracking for CacheManager
 */
public data class CachedLibraryEntry(
    val result: LibraryCompilationResult.Success,
    val cachedAt: Instant,
    val accessCount: AtomicLong,
    var lastAccessed: Instant
)

/**
 * Cached dependency analysis result
 */
public data class CachedDependencyGraph(
    val result: DependencyAnalysisResult,
    val cachedAt: Instant,
    val configurationHash: String
)

/**
 * Cache status for monitoring
 */
public sealed class CacheStatus {
    public object Idle : CacheStatus()
    public object Ready : CacheStatus()
    public object Warming : CacheStatus()
    public object Cleaning : CacheStatus()
    public data class Error(val message: String) : CacheStatus()
}

/**
 * Comprehensive cache statistics
 */
public data class CacheStatistics(
    val scriptCacheEntries: Int,
    val libraryCacheEntries: Int,
    val dependencyCacheEntries: Int,
    val totalCacheSize: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheEvictions: Long,
    val hitRatio: Double,
    val oldestEntry: Instant?,
    val newestEntry: Instant?
) {
    val totalSizeMB: Double
        get() = totalCacheSize / (1024.0 * 1024.0)
}

/**
 * Cache hotspot analysis
 */
public data class CacheHotSpot(
    val key: String,
    val accessCount: Long,
    val lastAccessed: Instant,
    val cacheAge: Long
)

/**
 * Cache analysis report for optimization
 */
public data class CacheAnalysisReport(
    val statistics: CacheStatistics,
    val hotSpots: List<CacheHotSpot>,
    val recommendations: List<String>,
    val generatedAt: Instant
)

/**
 * Dependency analysis result for caching
 */
public data class DependencyAnalysisResult(
    val dependencyGraph: Map<String, Set<String>>,
    val compilationOrder: List<String>,
    val analysisTimeMs: Long
)