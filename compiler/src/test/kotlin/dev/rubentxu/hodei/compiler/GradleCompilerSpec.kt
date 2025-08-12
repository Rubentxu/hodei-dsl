package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * BDD Specification for Gradle Compiler integration
 * 
 * Tests the GradleCompiler functionality for compiling external libraries
 * using real Gradle project structures and compilation.
 */
class GradleCompilerSpec : BehaviorSpec({
    
    given("a gradle compiler") {
        val compiler = GradleCompiler()
        
        `when`("validating gradle projects") {
            then("should identify valid gradle projects") {
                val validProjectDir = createMockGradleProject("build.gradle.kts")
                
                try {
                    compiler.isValidGradleProject(validProjectDir.toFile()) shouldBe true
                } finally {
                    validProjectDir.toFile().deleteRecursively()
                }
            }
            
            then("should identify invalid gradle projects") {
                val invalidProjectDir = Files.createTempDirectory("invalid-project")
                
                try {
                    compiler.isValidGradleProject(invalidProjectDir.toFile()) shouldBe false
                } finally {
                    invalidProjectDir.toFile().deleteRecursively()
                }
            }
        }
        
        `when`("resolving and normalizing paths") {
            then("should resolve relative paths to absolute paths") {
                val relativePath = "./test-path"
                val absolutePath = compiler.resolveAndNormalizeAbsolutePath(relativePath)
                
                absolutePath shouldContain "test-path"
                File(absolutePath).isAbsolute shouldBe true
            }
            
            then("should normalize paths with .. segments") {
                val messyPath = "/home/user/../user/project"
                val normalizedPath = compiler.resolveAndNormalizeAbsolutePath(messyPath)
                
                normalizedPath shouldNotBe messyPath
                normalizedPath shouldContain "/home/user/project"
            }
        }
        
        `when`("finding jar files") {
            then("should find jar files in directory") {
                val tempDir = Files.createTempDirectory("jar-test")
                val jarFile = tempDir.resolve("test.jar").toFile()
                jarFile.writeText("fake jar content")
                
                try {
                    val foundJar = compiler.findJarFile(tempDir.toFile())
                    foundJar shouldNotBe null
                    foundJar?.name shouldBe "test.jar"
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
            
            then("should return null when no jar files exist") {
                val emptyDir = Files.createTempDirectory("empty-test")
                
                try {
                    val foundJar = compiler.findJarFile(emptyDir.toFile())
                    foundJar shouldBe null
                } finally {
                    emptyDir.toFile().deleteRecursively()
                }
            }
            
            then("should prefer main jar over sources/javadoc jars") {
                val tempDir = Files.createTempDirectory("multi-jar-test")
                val mainJar = tempDir.resolve("library-1.0.0.jar").toFile()
                val sourcesJar = tempDir.resolve("library-1.0.0-sources.jar").toFile()
                val javadocJar = tempDir.resolve("library-1.0.0-javadoc.jar").toFile()
                
                // Create files with different timestamps
                mainJar.writeText("main jar")
                Thread.sleep(10)
                sourcesJar.writeText("sources jar")
                Thread.sleep(10) 
                javadocJar.writeText("javadoc jar")
                
                try {
                    val foundJar = compiler.findJarFile(tempDir.toFile())
                    foundJar shouldNotBe null
                    foundJar?.name shouldBe "library-1.0.0.jar"
                } finally {
                    tempDir.toFile().deleteRecursively()
                }
            }
        }
        
        `when`("compiling non-existent projects") {
            then("should return failure for non-existent source") {
                val config = LibraryConfiguration.simple("test-lib", "/non/existent/path")
                
                val result = runBlocking {
                    compiler.compileAndJar("/non/existent/path", config)
                }
                
                result.shouldBeInstanceOf<LibraryCompilationResult.Failure>()
                result.error shouldContain "Source directory not found"
            }
        }
        
        `when`("compiling invalid gradle projects") {
            then("should return failure for directory without build files") {
                val emptyDir = Files.createTempDirectory("empty-gradle")
                val config = LibraryConfiguration.simple("test-lib", emptyDir.toString())
                
                try {
                    val result = runBlocking {
                        compiler.compileAndJar(emptyDir.toString(), config)
                    }
                    
                    result.shouldBeInstanceOf<LibraryCompilationResult.Failure>()
                    result.error shouldContain "Not a valid Gradle project"
                } finally {
                    emptyDir.toFile().deleteRecursively()
                }
            }
        }
    }
}) {
    companion object {
        /**
         * Creates a mock Gradle project directory for testing
         */
        private fun createMockGradleProject(buildFileName: String): Path {
            val projectDir = Files.createTempDirectory("mock-gradle-project")
            
            // Create basic Gradle build file
            val buildFile = projectDir.resolve(buildFileName)
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
                    archiveBaseName.set("test-library")
                }
            """.trimIndent())
            
            // Create source directory structure
            val srcMainKotlin = projectDir.resolve("src/main/kotlin")
            Files.createDirectories(srcMainKotlin)
            
            // Create a simple Kotlin class
            val kotlinFile = srcMainKotlin.resolve("TestClass.kt")
            kotlinFile.toFile().writeText("""
                package com.test
                
                class TestClass {
                    fun hello(): String = "Hello from test library!"
                }
            """.trimIndent())
            
            return projectDir
        }
    }
}