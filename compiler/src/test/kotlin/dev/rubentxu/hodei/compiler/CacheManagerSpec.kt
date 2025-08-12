package dev.rubentxu.hodei.compiler

import kotlin.script.experimental.api.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * BDD Specification for Intelligent Cache Manager
 * 
 * Tests the advanced caching system with multi-level caching, 
 * automatic invalidation, and sophisticated eviction policies.
 */
class CacheManagerSpec : BehaviorSpec({
    
    given("an intelligent cache manager") {
        val tempCacheDir = Files.createTempDirectory("cache-manager-test")
        val cacheManager = CacheManager(
            cacheRoot = tempCacheDir,
            maxCacheSize = 1024 * 1024, // 1MB for testing
            maxCacheAge = 1.hours,
            backgroundCleanupInterval = 10.seconds
        )
        
        afterSpec {
            cacheManager.shutdown()
            tempCacheDir.toFile().deleteRecursively()
        }
        
        `when`("working with script caching") {
            then("should cache and retrieve script compilation results") {
                val scriptContent = """
                    val message = "Hello Cache!"
                    println(message)
                    message
                """.trimIndent()
                
                val scriptName = "test-script.kts"
                val dependencies = listOf("kotlin-stdlib")
                
                // Initially no cache entry
                val initialResult = cacheManager.getCachedScript(scriptContent, scriptName, dependencies)
                initialResult shouldBe null
                
                // Create and cache a successful result
                val mockScript = createMockCompiledScript()
                val successResult = CompilationResult.Success(
                    scriptContent = scriptContent,
                    scriptName = scriptName,
                    compiledScript = mockScript,
                    fromCache = false
                )
                
                cacheManager.cacheScript(scriptContent, scriptName, dependencies, successResult)
                
                // Should now retrieve from cache
                val cachedResult = cacheManager.getCachedScript(scriptContent, scriptName, dependencies)
                cachedResult shouldNotBe null
                cachedResult!!.scriptName shouldBe scriptName
                cachedResult.fromCache shouldBe true
            }
            
            then("should invalidate cache when script content changes") {
                val originalScript = "val x = 1; x"
                val modifiedScript = "val x = 2; x"
                val scriptName = "changeable-script.kts"
                
                val successResult = CompilationResult.Success(
                    scriptContent = originalScript,
                    scriptName = scriptName,
                    compiledScript = createMockCompiledScript(),
                    fromCache = false
                )
                
                // Cache original script
                cacheManager.cacheScript(originalScript, scriptName, emptyList(), successResult)
                
                // Should retrieve cached version
                val cachedOriginal = cacheManager.getCachedScript(originalScript, scriptName, emptyList())
                cachedOriginal shouldNotBe null
                
                // Should not find cache for modified script (different content hash)
                val cachedModified = cacheManager.getCachedScript(modifiedScript, scriptName, emptyList())
                cachedModified shouldBe null
            }
        }
        
        `when`("working with library caching") {
            then("should cache and retrieve library compilation results") {
                val config = LibraryConfiguration.simple("test-lib", "/test/path")
                
                // Initially no cache entry
                val initialResult = cacheManager.getCachedLibrary(config)
                initialResult shouldBe null
                
                // Create mock library result
                val jarFile = File.createTempFile("test-lib", ".jar")
                val metadata = LibraryMetadata(
                    configuration = config,
                    jarFile = jarFile,
                    compiledAt = Instant.now(),
                    sourceHash = "test-hash",
                    compilationTimeMs = 2000
                )
                
                val successResult = LibraryCompilationResult.Success(
                    metadata = metadata,
                    fromCache = false
                )
                
                try {
                    cacheManager.cacheLibrary(config, successResult)
                    
                    // Should now retrieve from cache
                    val cachedResult = cacheManager.getCachedLibrary(config)
                    cachedResult shouldNotBe null
                    cachedResult!!.metadata.configuration.name shouldBe "test-lib"
                    cachedResult.fromCache shouldBe true
                    
                } finally {
                    jarFile.delete()
                }
            }
            
            then("should invalidate cache when JAR file is deleted") {
                val config = LibraryConfiguration.simple("jar-delete-test", "/test/path")
                val jarFile = File.createTempFile("jar-delete-test", ".jar")
                
                val metadata = LibraryMetadata(
                    configuration = config,
                    jarFile = jarFile,
                    compiledAt = Instant.now(),
                    sourceHash = "jar-hash",
                    compilationTimeMs = 1500
                )
                
                val successResult = LibraryCompilationResult.Success(
                    metadata = metadata,
                    fromCache = false
                )
                
                // Cache the result
                cacheManager.cacheLibrary(config, successResult)
                
                // Should retrieve from cache
                val cachedWithJar = cacheManager.getCachedLibrary(config)
                cachedWithJar shouldNotBe null
                
                // Delete JAR file
                jarFile.delete()
                
                // Cache should now be invalid
                val cachedWithoutJar = cacheManager.getCachedLibrary(config)
                cachedWithoutJar shouldBe null
            }
        }
        
        `when`("working with dependency graph caching") {
            then("should cache and retrieve dependency analysis results") {
                val configs = listOf(
                    LibraryConfiguration.simple("lib1", "/path1"),
                    LibraryConfiguration.simple("lib2", "/path2")
                )
                
                val dependencyResult = DependencyAnalysisResult(
                    dependencyGraph = mapOf(
                        "lib1" to emptySet(),
                        "lib2" to setOf("lib1")
                    ),
                    compilationOrder = listOf("lib1", "lib2"),
                    analysisTimeMs = 100
                )
                
                // Initially no cache
                val initialCached = cacheManager.getCachedDependencyGraph(configs)
                initialCached shouldBe null
                
                // Cache the analysis
                cacheManager.cacheDependencyGraph(configs, dependencyResult)
                
                // Should retrieve from cache
                val cachedResult = cacheManager.getCachedDependencyGraph(configs)
                cachedResult shouldNotBe null
                cachedResult!!.compilationOrder shouldBe listOf("lib1", "lib2")
                cachedResult.dependencyGraph["lib2"] shouldBe setOf("lib1")
            }
            
            then("should invalidate cache when configuration changes") {
                val originalConfigs = listOf(
                    LibraryConfiguration.simple("lib1", "/path1")
                )
                
                val modifiedConfigs = listOf(
                    LibraryConfiguration.simple("lib1", "/path1"),
                    LibraryConfiguration.simple("lib2", "/path2")
                )
                
                val dependencyResult = DependencyAnalysisResult(
                    dependencyGraph = mapOf("lib1" to emptySet()),
                    compilationOrder = listOf("lib1"),
                    analysisTimeMs = 50
                )
                
                // Cache original configuration
                cacheManager.cacheDependencyGraph(originalConfigs, dependencyResult)
                
                // Should retrieve cached result for original config
                val cachedOriginal = cacheManager.getCachedDependencyGraph(originalConfigs)
                cachedOriginal shouldNotBe null
                
                // Should not find cache for modified config
                val cachedModified = cacheManager.getCachedDependencyGraph(modifiedConfigs)
                cachedModified shouldBe null
            }
        }
        
        `when`("performing cache warmup") {
            then("should warm up cache with common libraries and scripts") {
                val commonLibraries = listOf(
                    LibraryConfiguration.simple("common-lib1", "/common/path1"),
                    LibraryConfiguration.simple("common-lib2", "/common/path2")
                )
                
                val commonScripts = listOf(
                    "val greeting = \"Hello World!\"" to "greeting.kts",
                    "println(\"Common script\")" to "common.kts"
                )
                
                // Monitor cache status
                val initialStatus = cacheManager.cacheStatus.value
                initialStatus.shouldBeInstanceOf<CacheStatus.Idle>()
                
                // Perform warmup
                cacheManager.warmupCache(commonLibraries, commonScripts)
                
                // Wait for warmup to complete
                delay(100)
                
                val finalStatus = cacheManager.cacheStatus.value
                finalStatus.shouldBeInstanceOf<CacheStatus.Ready>()
                
                // Check that cache has entries (mock entries from warmup)  
                val stats = cacheManager.getCacheStatistics()
                // Warmup may add additional entries, so check that we have at least the expected number
                stats.libraryCacheEntries shouldBeGreaterThanOrEqual 2
                stats.scriptCacheEntries shouldBeGreaterThanOrEqual 2  // Allow for cache pollution from other tests
            }
        }
        
        `when`("managing cache statistics and analysis") {
            then("should provide accurate cache statistics") {
                // Clear cache to start clean
                cacheManager.clearCache()
                
                val initialStats = cacheManager.getCacheStatistics()
                initialStats.scriptCacheEntries shouldBe 0
                initialStats.libraryCacheEntries shouldBe 0
                initialStats.cacheHits shouldBe 0
                initialStats.cacheMisses shouldBe 0
                initialStats.hitRatio shouldBe 0.0
                
                // Add some cache entries
                val scriptResult = CompilationResult.Success("test script", "test.kts", createMockCompiledScript(), emptyList(), emptySet(), false)
                cacheManager.cacheScript("test script", "test.kts", emptyList(), scriptResult)
                
                val config = LibraryConfiguration.simple("test-stat-lib", "/test")
                val jarFile = File.createTempFile("test-stat", ".jar")
                
                try {
                    val metadata = LibraryMetadata(config, jarFile, Instant.now(), "hash", 200)
                    val libResult = LibraryCompilationResult.Success(metadata, false)
                    cacheManager.cacheLibrary(config, libResult)
                    
                    val updatedStats = cacheManager.getCacheStatistics()
                    updatedStats.scriptCacheEntries shouldBe 1
                    updatedStats.libraryCacheEntries shouldBe 1
                    
                } finally {
                    jarFile.delete()
                }
            }
            
            then("should export comprehensive cache analysis report") {
                // Clear cache and add test data
                cacheManager.clearCache()
                
                val scriptResult = CompilationResult.Success("analysis script", "analysis.kts", createMockCompiledScript(), emptyList(), emptySet(), false)
                cacheManager.cacheScript("analysis script", "analysis.kts", emptyList(), scriptResult)
                
                // Simulate some cache hits/misses
                cacheManager.getCachedScript("analysis script", "analysis.kts", emptyList())
                cacheManager.getCachedScript("non-existent script", "missing.kts", emptyList())
                
                val report = cacheManager.exportCacheAnalysis()
                
                report.statistics shouldNotBe null
                report.hotSpots shouldNotBe null
                report.recommendations shouldNotBe null
                report.generatedAt shouldNotBe null
                
                // Should have generated some recommendations
                val stats = report.statistics
                if (stats.hitRatio < 0.5 || stats.cacheEvictions > stats.cacheHits * 0.1) {
                    report.recommendations.size.shouldBeGreaterThan(0)
                }
            }
        }
        
        `when`("testing cache cleanup and eviction") {
            then("should perform cleanup when cache size exceeds limits") {
                // Create cache manager with very small size limit
                val smallCacheManager = CacheManager(
                    cacheRoot = Files.createTempDirectory("small-cache"),
                    maxCacheSize = 1024, // 1KB
                    maxCacheAge = 1.hours
                )
                
                try {
                    val initialStats = smallCacheManager.getCacheStatistics()
                    initialStats.totalCacheSize shouldBe 0L
                    
                    // Add entries that will exceed the size limit
                    repeat(10) { i ->
                        val largeScript = "x".repeat(200) // 200 chars each
                        val result = CompilationResult.Success(largeScript, "large$i.kts", createMockCompiledScript(), emptyList(), emptySet(), false)
                        smallCacheManager.cacheScript(largeScript, "large$i.kts", emptyList(), result)
                    }
                    
                    // Trigger cleanup
                    smallCacheManager.performCacheCleanup()
                    
                    val finalStats = smallCacheManager.getCacheStatistics()
                    // Some entries should have been evicted
                    finalStats.scriptCacheEntries.shouldBeLessThan(10)
                    
                } finally {
                    smallCacheManager.shutdown()
                }
            }
            
            then("should clear all cache entries when requested") {
                // Add some entries
                val scriptResult = CompilationResult.Success("clear test", "clear-test.kts", createMockCompiledScript(), emptyList(), emptySet(), false)
                cacheManager.cacheScript("clear test", "clear-test.kts", emptyList(), scriptResult)
                
                val config = LibraryConfiguration.simple("clear-lib", "/clear/path")
                val jarFile = File.createTempFile("clear-test", ".jar")
                
                try {
                    val metadata = LibraryMetadata(config, jarFile, Instant.now(), "clear-hash", 200)
                    val libResult = LibraryCompilationResult.Success(metadata, false)
                    cacheManager.cacheLibrary(config, libResult)
                    
                    // Verify entries exist
                    val beforeClear = cacheManager.getCacheStatistics()
                    beforeClear.scriptCacheEntries.shouldBeGreaterThan(0)
                    beforeClear.libraryCacheEntries.shouldBeGreaterThan(0)
                    
                    // Clear cache
                    cacheManager.clearCache()
                    
                    // Verify all entries are gone
                    val afterClear = cacheManager.getCacheStatistics()
                    afterClear.scriptCacheEntries shouldBe 0
                    afterClear.libraryCacheEntries shouldBe 0
                    afterClear.dependencyCacheEntries shouldBe 0
                    afterClear.cacheHits shouldBe 0
                    afterClear.cacheMisses shouldBe 0
                    
                } finally {
                    jarFile.delete()
                }
            }
        }
        
        `when`("monitoring cache status") {
            then("should provide reactive cache status updates") {
                val initialStatus = cacheManager.cacheStatus.value
                initialStatus.shouldBeInstanceOf<CacheStatus.Idle>()
                
                // Status should be observable via StateFlow
                cacheManager.cacheStatus shouldNotBe null
                
                // Performing operations should update status
                val commonLibraries = listOf(
                    LibraryConfiguration.simple("status-lib", "/status/path")
                )
                
                // Start warmup in background
                val warmupJob = GlobalScope.async {
                    cacheManager.warmupCache(commonLibraries)
                }
                
                // Status should change to Warming then Ready
                delay(50) // Allow status to update
                
                warmupJob.await()
                
                val finalStatus = cacheManager.cacheStatus.value
                finalStatus.shouldBeInstanceOf<CacheStatus.Ready>()
            }
        }
    }
}) {
    
    companion object {
        private fun Int.shouldBeGreaterThan(expected: Int) {
            (this > expected) shouldBe true
        }
        
        private fun Int.shouldBeLessThan(expected: Int) {
            (this < expected) shouldBe true
        }
        
        private infix fun Int.shouldBeGreaterThanOrEqual(expected: Int) {
            (this >= expected) shouldBe true
        }
        
        private fun createMockCompiledScript(): CompiledScript {
            return object : CompiledScript {
                override val compilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration()
                override val sourceLocationId: String? = null
                override suspend fun getClass(scriptEvaluationConfiguration: ScriptEvaluationConfiguration?): ResultWithDiagnostics<kotlin.reflect.KClass<*>> {
                    return ResultWithDiagnostics.Success(String::class)
                }
            }
        }
    }
}