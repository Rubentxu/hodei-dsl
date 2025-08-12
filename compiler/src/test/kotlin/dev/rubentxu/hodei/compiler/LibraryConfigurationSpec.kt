package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import java.time.Instant

/**
 * BDD Specification for Library Configuration system
 * 
 * Tests the library configuration data structures and their behavior
 * for managing external Gradle library compilation settings.
 */
class LibraryConfigurationSpec : BehaviorSpec({
    
    given("library configuration system") {
        
        `when`("creating basic configurations") {
            then("should create simple configuration with defaults") {
                val config = LibraryConfiguration.simple("my-lib", "/path/to/source")
                
                config.name shouldBe "my-lib"
                config.sourcePath shouldBe "/path/to/source"
                config.version shouldBe "1.0.0"
                config.gradleArgs shouldBe listOf("clean", "build")
                config.customArchiveName shouldBe null
            }
            
            then("should create development configuration with verbose options") {
                val config = LibraryConfiguration.development("dev-lib", "/dev/source")
                
                config.name shouldBe "dev-lib"
                config.sourcePath shouldBe "/dev/source"
                config.gradleArgs shouldContain "clean"
                config.gradleArgs shouldContain "build"
                config.gradleArgs shouldContain "--info"
                config.gradleArgs shouldContain "--stacktrace"
            }
            
            then("should create custom configuration with all options") {
                val customArgs = listOf("clean", "build", "publishToMavenLocal")
                val config = LibraryConfiguration(
                    name = "custom-lib",
                    sourcePath = "/custom/path",
                    version = "2.1.0",
                    gradleArgs = customArgs,
                    customArchiveName = "my-custom-name"
                )
                
                config.name shouldBe "custom-lib"
                config.sourcePath shouldBe "/custom/path"
                config.version shouldBe "2.1.0"
                config.gradleArgs shouldBe customArgs
                config.customArchiveName shouldBe "my-custom-name"
            }
        }
        
        `when`("working with cache keys") {
            then("should generate consistent cache keys for identical configurations") {
                val config1 = LibraryConfiguration.simple("lib", "/path")
                val config2 = LibraryConfiguration.simple("lib", "/path")
                
                config1.cacheKey() shouldBe config2.cacheKey()
            }
            
            then("should generate different cache keys for different configurations") {
                val config1 = LibraryConfiguration.simple("lib1", "/path")
                val config2 = LibraryConfiguration.simple("lib2", "/path")
                val config3 = LibraryConfiguration.simple("lib1", "/different-path")
                
                config1.cacheKey() shouldNotBe config2.cacheKey()
                config1.cacheKey() shouldNotBe config3.cacheKey()
            }
            
            then("should include version and path hash in cache key") {
                val config = LibraryConfiguration(
                    name = "test-lib",
                    sourcePath = "/test/path",
                    version = "3.0.0"
                )
                
                val cacheKey = config.cacheKey()
                cacheKey shouldContain "test-lib"
                cacheKey shouldContain "3.0.0"
                // Hash component should be present
                cacheKey.split("-").size shouldBe 5  // name-version-pathHash components may have internal hyphens
            }
        }
        
        `when`("working with library metadata") {
            then("should create metadata with all required fields") {
                val config = LibraryConfiguration.simple("meta-test", "/source")
                val jarFile = java.io.File("/fake/path/test.jar")
                val compiledTime = Instant.now()
                
                val metadata = LibraryMetadata(
                    configuration = config,
                    jarFile = jarFile,
                    compiledAt = compiledTime,
                    sourceHash = "abc123",
                    compilationTimeMs = 5000L
                )
                
                metadata.configuration shouldBe config
                metadata.jarFile shouldBe jarFile
                metadata.compiledAt shouldBe compiledTime
                metadata.sourceHash shouldBe "abc123"
                metadata.compilationTimeMs shouldBe 5000L
            }
            
            then("should validate up-to-date status correctly") {
                val config = LibraryConfiguration.simple("update-test", "/source")
                val jarFile = java.io.File.createTempFile("test", ".jar")
                
                try {
                    val metadata = LibraryMetadata(
                        configuration = config,
                        jarFile = jarFile,
                        compiledAt = Instant.now(),
                        sourceHash = "current-hash",
                        compilationTimeMs = 1000L
                    )
                    
                    // Should be up to date with same hash
                    metadata.isUpToDate("current-hash") shouldBe true
                    
                    // Should not be up to date with different hash
                    metadata.isUpToDate("different-hash") shouldBe false
                    
                    // Should not be up to date if JAR doesn't exist
                    jarFile.delete()
                    metadata.isUpToDate("current-hash") shouldBe false
                    
                } finally {
                    if (jarFile.exists()) jarFile.delete()
                }
            }
        }
        
        `when`("working with compilation results") {
            then("should create successful compilation result") {
                val config = LibraryConfiguration.simple("success-test", "/source")
                val jarFile = java.io.File.createTempFile("success", ".jar")
                
                try {
                    val metadata = LibraryMetadata(
                        configuration = config,
                        jarFile = jarFile,
                        compiledAt = Instant.now(),
                        sourceHash = "success-hash",
                        compilationTimeMs = 2000L
                    )
                    
                    val result = LibraryCompilationResult.Success(
                        metadata = metadata,
                        fromCache = false
                    )
                    
                    result.isSuccess shouldBe true
                    result.metadata shouldBe metadata
                    result.fromCache shouldBe false
                    
                    // Test cached result
                    val cachedResult = result.copy(fromCache = true)
                    cachedResult.fromCache shouldBe true
                    
                } finally {
                    if (jarFile.exists()) jarFile.delete()
                }
            }
            
            then("should create failed compilation result") {
                val config = LibraryConfiguration.simple("failure-test", "/nonexistent")
                val exception = RuntimeException("Compilation failed")
                
                val result = LibraryCompilationResult.Failure(
                    configuration = config,
                    error = "Build failed",
                    cause = exception
                )
                
                result.isSuccess shouldBe false
                result.configuration shouldBe config
                result.error shouldBe "Build failed"
                result.cause shouldBe exception
            }
        }
        
        `when`("handling library exceptions") {
            then("should create appropriate exception types") {
                val sourceException = SourceNotFoundException("Source not found")
                sourceException.message shouldBe "Source not found"
                sourceException.cause shouldBe null
                
                val jarException = JarFileNotFoundException("JAR not found")
                jarException.message shouldBe "JAR not found"
                
                val gradleException = GradleCompilationException("Gradle failed")
                gradleException.message shouldBe "Gradle failed"
            }
            
            then("should support exception chaining") {
                val rootCause = RuntimeException("Root cause")
                val libraryException = SourceNotFoundException("Source missing", rootCause)
                
                libraryException.message shouldBe "Source missing"
                libraryException.cause shouldBe rootCause
            }
        }
    }
})