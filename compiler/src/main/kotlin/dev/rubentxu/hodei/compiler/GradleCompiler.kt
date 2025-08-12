package dev.rubentxu.hodei.compiler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Gradle-based compiler for external pipeline libraries
 * 
 * Compiles Gradle projects into JAR artifacts that can be loaded
 * dynamically into pipeline execution context.
 */
public class GradleCompiler {
    
    /**
     * Compiles a Gradle project and returns the compiled JAR artifact
     */
    public suspend fun compileAndJar(
        sourcePath: String, 
        libraryConfiguration: LibraryConfiguration
    ): LibraryCompilationResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val sourceDir = File(resolveAndNormalizeAbsolutePath(sourcePath))
            
            // Validate source directory exists
            if (!sourceDir.exists()) {
                return@withContext LibraryCompilationResult.Failure(
                    configuration = libraryConfiguration,
                    error = "Source directory not found: ${sourceDir.path}"
                )
            }
            
            // Check if it's a valid Gradle project
            if (!isValidGradleProject(sourceDir)) {
                return@withContext LibraryCompilationResult.Failure(
                    configuration = libraryConfiguration,
                    error = "Not a valid Gradle project: ${sourceDir.path} (missing build.gradle or build.gradle.kts)"
                )
            }
            
            // Calculate source hash for caching
            val sourceHash = calculateSourceHash(sourceDir)
            
            val connection: ProjectConnection = GradleConnector.newConnector()
                .forProjectDirectory(sourceDir)
                .connect()
                
            try {
                val project: GradleProject = connection.getModel(GradleProject::class.java)
                
                // Build Gradle arguments
                val buildArgs = mutableListOf<String>().apply {
                    addAll(libraryConfiguration.gradleArgs)
                    
                    // Add custom archive name if specified
                    libraryConfiguration.customArchiveName?.let { customName ->
                        add("-ParchiveBaseName=$customName")
                    }
                }
                
                // Execute Gradle build
                connection.newBuild()
                    .withArguments(buildArgs)
                    .run()
                
                // Find generated JAR file
                val jarFile = findJarFile(File(sourceDir, "build/libs"))
                    ?: throw JarFileNotFoundException("No JAR file found in ${sourceDir.path}/build/libs/")
                
                if (!jarFile.exists()) {
                    throw JarFileNotFoundException("JAR file not found: ${jarFile.path}")
                }
                
                val compilationTime = System.currentTimeMillis() - startTime
                val metadata = LibraryMetadata(
                    configuration = libraryConfiguration,
                    jarFile = jarFile,
                    compiledAt = Instant.now(),
                    sourceHash = sourceHash,
                    compilationTimeMs = compilationTime
                )
                
                LibraryCompilationResult.Success(metadata = metadata)
                
            } finally {
                connection.close()
            }
            
        } catch (e: Exception) {
            LibraryCompilationResult.Failure(
                configuration = libraryConfiguration,
                error = "Gradle compilation failed: ${e.message}",
                cause = e
            )
        }
    }
    
    /**
     * Gets the project name from a Gradle project directory
     */
    public suspend fun getProjectName(projectDir: String): String = withContext(Dispatchers.IO) {
        val connection: ProjectConnection = GradleConnector.newConnector()
            .forProjectDirectory(File(projectDir))
            .connect()
            
        try {
            val project: GradleProject = connection.getModel(GradleProject::class.java)
            project.name
        } finally {
            connection.close()
        }
    }
    
    /**
     * Checks if a directory is a valid Gradle project
     */
    public fun isValidGradleProject(directory: File): Boolean {
        return directory.isDirectory && (
            File(directory, "build.gradle").exists() || 
            File(directory, "build.gradle.kts").exists()
        )
    }
    
    /**
     * Finds JAR files in the specified directory
     */
    public fun findJarFile(directory: File): File? {
        if (!directory.exists() || !directory.isDirectory) {
            return null
        }
        
        return directory.walk()
            .filter { it.isFile && it.extension == "jar" }
            .filter { !it.name.contains("sources") && !it.name.contains("javadoc") }
            .maxByOrNull { it.lastModified() } // Return the most recently modified JAR
    }
    
    /**
     * Resolves and normalizes an absolute path
     */
    public fun resolveAndNormalizeAbsolutePath(relativePath: String): String {
        val path = Path.of(relativePath)
        return path.toAbsolutePath().normalize().toString()
    }
    
    /**
     * Calculates SHA-256 hash of source files for caching
     */
    private fun calculateSourceHash(sourceDir: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        
        // Hash build files and source files for dependency detection
        val filesToHash = sourceDir.walkTopDown()
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
        
        for (file in filesToHash) {
            try {
                md.update(file.readBytes())
                md.update(file.absolutePath.toByteArray())
            } catch (e: Exception) {
                // Skip files that can't be read
                continue
            }
        }
        
        val hash = md.digest()
        return hash.fold("") { str, byte -> str + "%02x".format(byte) }
    }
}