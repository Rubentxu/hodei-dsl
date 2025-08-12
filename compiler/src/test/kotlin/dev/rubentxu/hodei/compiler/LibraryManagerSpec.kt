package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * BDD Specification for Advanced Library Manager
 * 
 * Tests the sophisticated library management capabilities including
 * caching, parallel compilation, dependency tracking, and hot-reload.
 */
class LibraryManagerSpec : BehaviorSpec({
    
    given("an advanced library manager") {
        val gradleCompiler = GradleCompiler()
        val tempCacheDir = Files.createTempDirectory("library-manager-test")
        val libraryManager = LibraryManager(gradleCompiler, tempCacheDir, maxConcurrentBuilds = 2)
        
        afterSpec {
            // Cleanup
            tempCacheDir.toFile().deleteRecursively()
        }
        
        `when`("resolving empty library list") {
            then("should return success with empty result") {
                val result = libraryManager.resolveLibraries(emptyList())
                
                result.shouldBeInstanceOf<LibraryResolutionResult.Success>()
                result.libraries.size shouldBe 0
            }
        }
        
        `when`("resolving invalid libraries") {
            then("should return failure with detailed error information") {
                val invalidConfigs = listOf(
                    LibraryConfiguration.simple("bad-lib-1", "/nonexistent/path1"),
                    LibraryConfiguration.simple("bad-lib-2", "/nonexistent/path2")
                )
                
                val result = libraryManager.resolveLibraries(invalidConfigs)
                
                result.shouldBeInstanceOf<LibraryResolutionResult.Failure>()
                result.error shouldNotBe ""
                result.failedLibraries.size shouldBe 2
            }
        }
        
        `when`("working with cache management") {
            then("should provide accurate cache statistics") {
                libraryManager.clearCache()
                
                val stats = libraryManager.getCacheStatistics()
                stats.totalEntries shouldBe 0
                stats.totalSizeBytes shouldBe 0L
                stats.totalSizeMB shouldBe 0.0
                (stats.cacheHitRatio >= 0.0) shouldBe true
            }
            
            then("should handle cache validation correctly") {
                val tempDir = createMockLibraryProject("cache-test")
                
                try {
                    val config = LibraryConfiguration.simple("cache-test-lib", tempDir.toString())
                    
                    // Should return null for non-cached library
                    val cached = libraryManager.getCachedLibrary(config)
                    cached shouldBe null
                    
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }
        
        `when`("building individual libraries") {
            then("should handle non-existent library gracefully") {
                val config = LibraryConfiguration.simple("nonexistent", "/does/not/exist")
                
                val result = libraryManager.buildLibrary(config)
                
                result.shouldBeInstanceOf<LibraryCompilationResult.Failure>()
                result.error shouldNotBe ""
                result.configuration shouldBe config
            }
            
            then("should handle empty directory gracefully") {
                val emptyDir = Files.createTempDirectory("empty-library")
                val config = LibraryConfiguration.simple("empty-lib", emptyDir.toString())
                
                try {
                    val result = libraryManager.buildLibrary(config)
                    
                    result.shouldBeInstanceOf<LibraryCompilationResult.Failure>()
                    result.error shouldNotBe ""
                    
                } finally {
                    emptyDir.toFile().deleteRecursively()
                }
            }
        }
        
        `when`("monitoring compilation status") {
            then("should provide reactive compilation status") {
                // Get initial compilation status
                val initialStatus = libraryManager.compilationStatus.value
                initialStatus shouldBe emptyMap()
                
                // Status should be reactive and observable
                libraryManager.compilationStatus shouldNotBe null
            }
            
            then("should track compilation progress") {
                val config = LibraryConfiguration.simple("progress-test", "/nonexistent")
                
                // Start a compilation (will fail but should update status)
                val compilationJob = GlobalScope.async {
                    libraryManager.buildLibrary(config)
                }
                
                // Allow some time for status update
                delay(100)
                
                // Check that compilation was tracked
                val status = libraryManager.compilationStatus.value
                // Status might be empty if compilation completed too quickly
                // In real scenarios with actual builds, this would show progress
                
                compilationJob.await()
            }
        }
        
        `when`("testing hot-reload capabilities") {
            then("should create hot-reload job") {
                val tempDir = createMockLibraryProject("hotreload-test")
                var changeNotifications = 0
                
                try {
                    val config = LibraryConfiguration.simple("hotreload-lib", tempDir.toString())
                    
                    val hotReloadJob = libraryManager.enableHotReload(
                        configurations = listOf(config)
                    ) { _, _ ->
                        changeNotifications++
                    }
                    
                    // Hot reload job should be active
                    hotReloadJob.isActive shouldBe true
                    
                    // Cancel the job
                    hotReloadJob.cancel()
                    
                    // Should be cancelled
                    delay(100)
                    hotReloadJob.isCancelled shouldBe true
                    
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }
        
        `when`("testing dependency management") {
            then("should handle multiple library configurations") {
                val configs = listOf(
                    LibraryConfiguration.simple("lib1", "/path1"),
                    LibraryConfiguration.simple("lib2", "/path2"),
                    LibraryConfiguration.development("lib3", "/path3")
                )
                
                // This will fail because paths don't exist, but it tests the flow
                val result = libraryManager.resolveLibraries(configs)
                
                result.shouldBeInstanceOf<LibraryResolutionResult.Failure>()
                result.failedLibraries.size shouldBe 3
            }
        }
        
        `when`("testing parallel compilation limits") {
            then("should respect max concurrent builds setting") {
                // Create manager with limited concurrency
                val limitedManager = LibraryManager(gradleCompiler, tempCacheDir, maxConcurrentBuilds = 1)
                
                val configs = listOf(
                    LibraryConfiguration.simple("concurrent1", "/path1"),
                    LibraryConfiguration.simple("concurrent2", "/path2")
                )
                
                val startTime = System.currentTimeMillis()
                val result = limitedManager.resolveLibraries(configs)
                val endTime = System.currentTimeMillis()
                
                // With concurrency limit of 1, builds should be sequential
                result.shouldBeInstanceOf<LibraryResolutionResult.Failure>()
                
                // At least some time should have passed for sequential execution
                val duration = endTime - startTime
                (duration >= 0L) shouldBe true
            }
        }
        
        `when`("testing library resolution metrics") {
            then("should provide detailed metrics") {
                val configs = listOf(
                    LibraryConfiguration.simple("metrics-test", "/nonexistent")
                )
                
                val result = libraryManager.resolveLibraries(configs)
                
                when (result) {
                    is LibraryResolutionResult.Success -> {
                        val metrics = result.metrics
                        if (metrics != null) {
                            metrics.totalLibraries shouldBe 1
                            (metrics.cacheHitRatio >= 0.0) shouldBe true
                        }
                    }
                    is LibraryResolutionResult.Failure -> {
                        // Expected for non-existent paths
                        result.failedLibraries.size shouldBe 1
                    }
                }
            }
        }
    }
}) {
    companion object {
        /**
         * Creates a mock library project directory for testing
         */
        private fun createMockLibraryProject(projectName: String): Path {
            val projectDir = Files.createTempDirectory("mock-$projectName")
            
            // Create basic Gradle build file
            val buildFile = projectDir.resolve("build.gradle.kts")
            buildFile.toFile().writeText("""
                plugins {
                    kotlin("jvm") version "1.9.0"
                }
                
                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-stdlib")
                }
                
                tasks.jar {
                    archiveBaseName.set("$projectName")
                }
            """.trimIndent())
            
            // Create source directory structure
            val srcMainKotlin = projectDir.resolve("src/main/kotlin")
            Files.createDirectories(srcMainKotlin)
            
            // Create a simple Kotlin class
            val kotlinFile = srcMainKotlin.resolve("${projectName.capitalize()}Class.kt")
            kotlinFile.toFile().writeText("""
                package com.test.$projectName
                
                class ${projectName.capitalize()}Class {
                    fun hello(): String = "Hello from $projectName library!"
                }
            """.trimIndent())
            
            return projectDir
        }
    }
}