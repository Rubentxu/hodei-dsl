package dev.rubentxu.hodei.core.integration.utils

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

/**
 * Utilities for copying project source code to containers for integration testing
 */
public object ProjectCopyUtils {
    
    /**
     * Directories and files to exclude when copying project to container
     */
    private val EXCLUDE_PATTERNS = setOf(
        "build",
        ".gradle",
        ".idea",
        ".junie",
        "*.iml",
        "*.log",
        "target",
        "node_modules",
        ".git",
        "*.tmp",
        "*.temp"
    )
    
    /**
     * Copies the current project to a container, excluding build artifacts and IDE files
     * 
     * @param container The target container
     * @param projectRoot The root directory of the project
     * @param containerPath The target path inside the container (default: /workspace)
     */
    public fun copyProjectToContainer(
        container: GenericContainer<*>,
        projectRoot: Path = findProjectRoot(),
        containerPath: String = "/workspace"
    ) {
        // Create a temporary directory for filtered copy
        val tempDir = Files.createTempDirectory("hodei-project-copy")
        
        try {
            // Copy project files excluding build artifacts
            copyProjectFiltered(projectRoot, tempDir)
            
            // Copy the filtered project to container
            container.copyFileToContainer(
                MountableFile.forHostPath(tempDir),
                containerPath
            )
            
        } finally {
            // Clean up temporary directory
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * Copies project files to a temporary directory, excluding specified patterns
     */
    private fun copyProjectFiltered(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { sourcePath ->
                val relativePath = source.relativize(sourcePath)
                val targetPath = target.resolve(relativePath)
                
                // Skip excluded files and directories
                if (shouldExclude(relativePath)) {
                    return@forEach
                }
                
                try {
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath)
                    } else {
                        Files.createDirectories(targetPath.parent)
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                } catch (e: Exception) {
                    // Log warning but continue with other files
                    println("Warning: Failed to copy $sourcePath: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Determines if a path should be excluded from copying
     */
    private fun shouldExclude(path: Path): Boolean {
        val pathString = path.toString()
        
        return EXCLUDE_PATTERNS.any { pattern ->
            when {
                pattern.startsWith("*.") -> {
                    // Handle file extensions
                    val extension = pattern.substring(1)
                    pathString.endsWith(extension)
                }
                pattern.endsWith("*") -> {
                    // Handle prefix patterns
                    val prefix = pattern.substring(0, pattern.length - 1)
                    pathString.startsWith(prefix)
                }
                else -> {
                    // Handle exact matches and directory names
                    pathString == pattern || 
                    pathString.startsWith("$pattern/") ||
                    pathString.contains("/$pattern/") ||
                    pathString.endsWith("/$pattern")
                }
            }
        }
    }
    
    /**
     * Finds the project root directory by looking for characteristic files
     */
    private fun findProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        
        while (current.parent != null) {
            // Look for Gradle project markers
            if (current.resolve("settings.gradle.kts").exists() || 
                current.resolve("settings.gradle").exists() ||
                current.resolve("gradlew").exists()) {
                return current
            }
            current = current.parent
        }
        
        // Fallback to current directory
        return Path.of("").toAbsolutePath()
    }
    
    /**
     * Creates a minimal Gradle project structure for testing
     */
    public fun createMinimalGradleProject(targetDir: Path) {
        Files.createDirectories(targetDir)
        
        // Create minimal settings.gradle.kts
        val settingsFile = targetDir.resolve("settings.gradle.kts")
        settingsFile.writeText("""
            rootProject.name = "test-project"
            include(":core")
        """.trimIndent())
        
        // Create minimal build.gradle.kts
        val buildFile = targetDir.resolve("build.gradle.kts")
        buildFile.writeText("""
            plugins {
                kotlin("jvm") version "2.2.0"
            }
            
            repositories {
                mavenCentral()
            }
            
            subprojects {
                apply(plugin = "kotlin")
                
                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        """.trimIndent())
        
        // Create core module structure
        val coreDir = targetDir.resolve("core")
        Files.createDirectories(coreDir.resolve("src/main/kotlin"))
        Files.createDirectories(coreDir.resolve("src/test/kotlin"))
        
        val coreBuildFile = coreDir.resolve("build.gradle.kts")
        coreBuildFile.writeText("""
            plugins {
                kotlin("jvm")
            }
            
            dependencies {
                implementation(kotlin("stdlib"))
                testImplementation(kotlin("test"))
            }
        """.trimIndent())
        
        // Create a simple Kotlin file
        val kotlinFile = coreDir.resolve("src/main/kotlin/TestClass.kt")
        kotlinFile.writeText("""
            package test
            
            class TestClass {
                fun hello(): String = "Hello from Gradle build!"
            }
        """.trimIndent())
    }
}

/**
 * Extension function to delete directory recursively
 */
private fun Path.deleteRecursively() {
    if (Files.exists(this)) {
        Files.walk(this)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}