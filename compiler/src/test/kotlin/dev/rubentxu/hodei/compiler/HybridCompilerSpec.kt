package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/**
 * BDD Specification for Hybrid Compiler
 * 
 * Tests the integration between script compilation and library compilation,
 * ensuring that the hybrid system works correctly with caching and performance optimizations.
 */
class HybridCompilerSpec : BehaviorSpec({
    
    given("a hybrid compiler system") {
        val scriptCompiler = ScriptCompiler(ScriptConfig.defaultConfig())
        val gradleCompiler = GradleCompiler()
        val hybridCompiler = HybridCompiler(scriptCompiler, gradleCompiler)
        
        `when`("compiling script without libraries") {
            then("should compile successfully") {
                val scriptContent = """
                    val result = "Hello from hybrid compilation!"
                    result
                """.trimIndent()
                
                val result = runBlocking {
                    hybridCompiler.compileWithLibraries(
                        scriptContent = scriptContent,
                        scriptName = "hybrid-test.kts",
                        libraries = emptyList()
                    )
                }
                
                result.shouldBeInstanceOf<HybridCompilationResult.Success>()
                result.scriptResult.scriptName shouldBe "hybrid-test.kts"
                result.libraryResults.size shouldBe 0
                result.compilationMetrics.libraryCount shouldBe 0
                result.compilationMetrics.cacheHits shouldBe 0
            }
        }
        
        `when`("compiling script with invalid libraries") {
            then("should return failure when library compilation fails") {
                val scriptContent = """
                    val result = "Script with bad library"
                    result
                """.trimIndent()
                
                val badLibraryConfig = LibraryConfiguration.simple(
                    name = "bad-lib",
                    sourcePath = "/non/existent/path"
                )
                
                val result = runBlocking {
                    hybridCompiler.compileWithLibraries(
                        scriptContent = scriptContent,
                        scriptName = "bad-lib-test.kts",
                        libraries = listOf(badLibraryConfig)
                    )
                }
                
                result.shouldBeInstanceOf<HybridCompilationResult.Failure>()
                result.error shouldContain "Library compilation failed"
                result.libraryErrors.size shouldBe 1
                result.libraryErrors.first().configuration.name shouldBe "bad-lib"
            }
        }
        
        `when`("working with compilation cache") {
            then("should provide accurate cache statistics") {
                // Clear cache first
                hybridCompiler.clearCache()
                
                val initialStats = hybridCompiler.getCacheStats()
                initialStats.size shouldBe 0
                initialStats.hits shouldBe 0L
                initialStats.misses shouldBe 0L
                initialStats.hitRatio shouldBe 0.0
            }
        }
        
        `when`("measuring compilation performance") {
            then("should track compilation metrics accurately") {
                val scriptContent = """
                    val message = "Performance test script"
                    println(message)
                    message
                """.trimIndent()
                
                val result = runBlocking {
                    hybridCompiler.compileWithLibraries(
                        scriptContent = scriptContent,
                        scriptName = "performance-test.kts"
                    )
                }
                
                result.shouldBeInstanceOf<HybridCompilationResult.Success>()
                
                val metrics = result.compilationMetrics
                metrics.totalDuration.toMillis() shouldBeGreaterThan 0L
                metrics.scriptCompilationDuration.toMillis() shouldBeGreaterThan 0L
                metrics.libraryCount shouldBe 0
                metrics.cacheHits shouldBe 0
                metrics.cacheHitRatio shouldBe 0.0
                metrics.averageLibraryCompilationTime shouldBe null
            }
        }
        
        `when`("testing library configuration options") {
            then("should handle simple configuration correctly") {
                val config = LibraryConfiguration.simple("test-lib", "/test/path")
                
                config.name shouldBe "test-lib"
                config.sourcePath shouldBe "/test/path"
                config.version shouldBe "1.0.0"
                config.gradleArgs shouldBe listOf("clean", "build")
                config.customArchiveName shouldBe null
            }
            
            then("should handle development configuration correctly") {
                val config = LibraryConfiguration.development("dev-lib", "/dev/path")
                
                config.name shouldBe "dev-lib"
                config.sourcePath shouldBe "/dev/path"
                config.gradleArgs.contains("--info") shouldBe true
                config.gradleArgs.contains("--stacktrace") shouldBe true
            }
            
            then("should generate consistent cache keys") {
                val config1 = LibraryConfiguration.simple("lib", "/path")
                val config2 = LibraryConfiguration.simple("lib", "/path")
                val config3 = LibraryConfiguration.simple("lib", "/different")
                
                config1.cacheKey() shouldBe config2.cacheKey()
                config1.cacheKey() shouldNotBe config3.cacheKey()
            }
        }
        
        `when`("handling script compilation errors") {
            then("should return failure for invalid script syntax") {
                val invalidScript = """
                    val unclosedString = "this string is not closed
                    // This will cause a compilation error
                """.trimIndent()
                
                val result = runBlocking {
                    hybridCompiler.compileWithLibraries(
                        scriptContent = invalidScript,
                        scriptName = "invalid-script.kts"
                    )
                }
                
                result.shouldBeInstanceOf<HybridCompilationResult.Failure>()
                result.error shouldContain "Script compilation failed"
                result.scriptErrors.isEmpty() shouldBe false
            }
        }
        
        `when`("testing compilation result integration") {
            then("should create proper class loader for libraries") {
                val simpleScript = """
                    val greeting = "Hello World"
                    greeting
                """.trimIndent()
                
                val result = runBlocking {
                    hybridCompiler.compileWithLibraries(
                        scriptContent = simpleScript,
                        scriptName = "classloader-test.kts"
                    )
                }
                
                result.shouldBeInstanceOf<HybridCompilationResult.Success>()
                result.classLoader shouldNotBe null
                result.libraryJars.size shouldBe 0
            }
        }
        
        `when`("testing library metadata") {
            then("should validate library metadata fields") {
                val tempDir = Files.createTempDirectory("metadata-test")
                val jarFile = tempDir.resolve("test.jar").toFile()
                jarFile.writeText("fake jar")
                
                try {
                    val config = LibraryConfiguration.simple("meta-lib", tempDir.toString())
                    val metadata = LibraryMetadata(
                        configuration = config,
                        jarFile = jarFile,
                        compiledAt = java.time.Instant.now(),
                        sourceHash = "hash123",
                        compilationTimeMs = 1000L
                    )
                    
                    metadata.isUpToDate("hash123") shouldBe true
                    metadata.isUpToDate("different-hash") shouldBe false
                    
                    // Test when JAR doesn't exist
                    jarFile.delete()
                    metadata.isUpToDate("hash123") shouldBe false
                    
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }
    }
})