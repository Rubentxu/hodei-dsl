package dev.rubentxu.hodei.core.integration.utils

import dev.rubentxu.hodei.core.integration.container.ContainerCommandLauncher
import org.testcontainers.containers.GenericContainer
import java.util.jar.JarFile
import java.util.jar.Manifest

/**
 * Validator for build artifacts generated during integration tests
 */
public class BuildArtifactValidator(
    private val container: GenericContainer<*>,
    private val workspaceDir: String = "/workspace"
) {
    
    private val commandLauncher = ContainerCommandLauncher(container)
    
    /**
     * Expected JAR files for the hodei-dsl project
     */
    private val expectedJars = listOf(
        "core/build/libs/core.jar",
        "compiler/build/libs/compiler.jar",
        "execution/build/libs/execution.jar",
        "cli/build/libs/cli.jar",
        "library/build/libs/library.jar",
        "steps/build/libs/steps.jar",
        "plugins/build/libs/plugins.jar"
    )
    
    /**
     * Validates all expected build artifacts
     */
    public suspend fun validateAllArtifacts(): ValidationResult {
        val validationResults = mutableListOf<ArtifactValidation>()
        
        for (jarPath in expectedJars) {
            val validation = validateJarArtifact(jarPath)
            validationResults.add(validation)
        }
        
        val allValid = validationResults.all { it.isValid }
        val totalSize = validationResults.sumOf { it.sizeBytes }
        
        return ValidationResult(
            success = allValid,
            artifacts = validationResults,
            totalSizeBytes = totalSize,
            summary = generateSummary(validationResults)
        )
    }
    
    /**
     * Validates a specific JAR artifact
     */
    public suspend fun validateJarArtifact(jarPath: String): ArtifactValidation {
        val fullPath = "$workspaceDir/$jarPath"
        
        // Check if file exists
        val existsResult = commandLauncher.execute("test -f $fullPath && echo 'exists' || echo 'missing'", workspaceDir)
        val exists = existsResult.success && existsResult.stdout.trim() == "exists"
        
        if (!exists) {
            return ArtifactValidation(
                path = jarPath,
                isValid = false,
                exists = false,
                sizeBytes = 0,
                hasClasses = false,
                hasManifest = false,
                errors = listOf("JAR file does not exist: $fullPath")
            )
        }
        
        // Get file size
        val sizeResult = commandLauncher.execute("stat -c%s $fullPath", workspaceDir)
        val size = if (sizeResult.success) {
            sizeResult.stdout.trim().toLongOrNull() ?: 0L
        } else {
            0L
        }
        
        // Check for minimum size (should be at least 1KB for a valid JAR)
        val isValidSize = size > 1024
        
        // Check if JAR contains .class files
        val classesResult = commandLauncher.execute("jar tf $fullPath | grep '\\.class$' | head -1", workspaceDir)
        val hasClasses = classesResult.success && classesResult.stdout.isNotBlank()
        
        // Check if JAR has manifest
        val manifestResult = commandLauncher.execute("jar tf $fullPath | grep 'META-INF/MANIFEST.MF'", workspaceDir)
        val hasManifest = manifestResult.success && manifestResult.stdout.isNotBlank()
        
        // Check JAR integrity
        val integrityResult = commandLauncher.execute("jar tf $fullPath > /dev/null", workspaceDir)
        val isIntact = integrityResult.success
        
        val errors = mutableListOf<String>()
        if (!isValidSize) errors.add("JAR file is too small ($size bytes, expected > 1024)")
        if (!hasClasses) errors.add("JAR file contains no .class files")
        if (!hasManifest) errors.add("JAR file has no manifest")
        if (!isIntact) errors.add("JAR file is corrupted or invalid")
        
        return ArtifactValidation(
            path = jarPath,
            isValid = isValidSize && hasClasses && hasManifest && isIntact,
            exists = true,
            sizeBytes = size,
            hasClasses = hasClasses,
            hasManifest = hasManifest,
            errors = errors
        )
    }
    
    /**
     * Lists all JAR files found in the workspace
     */
    public suspend fun listAllJars(): List<String> {
        val result = commandLauncher.execute("find . -name '*.jar' -type f", workspaceDir)
        return if (result.success) {
            result.stdout.split("\n").filter { it.isNotBlank() }.map { it.removePrefix("./") }
        } else {
            emptyList()
        }
    }
    
    /**
     * Gets detailed information about the build environment
     */
    public suspend fun getBuildEnvironmentInfo(): BuildEnvironmentInfo {
        val javaVersionResult = commandLauncher.execute("java -version", workspaceDir)
        val gradleVersionResult = commandLauncher.execute("gradle --version", workspaceDir)
        val diskUsageResult = commandLauncher.execute("du -sh .", workspaceDir)
        
        return BuildEnvironmentInfo(
            javaVersion = javaVersionResult.stderr.takeIf { javaVersionResult.success } ?: "Unknown",
            gradleVersion = gradleVersionResult.stdout.takeIf { gradleVersionResult.success } ?: "Unknown",
            diskUsage = diskUsageResult.stdout.takeIf { diskUsageResult.success } ?: "Unknown",
            workspaceSize = getDiskUsageBytes()
        )
    }
    
    private suspend fun getDiskUsageBytes(): Long {
        val result = commandLauncher.execute("du -sb .", workspaceDir)
        return if (result.success) {
            result.stdout.split("\t").firstOrNull()?.toLongOrNull() ?: 0L
        } else {
            0L
        }
    }
    
    private fun generateSummary(validations: List<ArtifactValidation>): String {
        val valid = validations.count { it.isValid }
        val total = validations.size
        val totalSize = validations.sumOf { it.sizeBytes }
        
        return "Validated $valid/$total artifacts successfully. Total size: ${formatBytes(totalSize)}"
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes bytes"
        }
    }
}

/**
 * Result of artifact validation
 */
public data class ValidationResult(
    val success: Boolean,
    val artifacts: List<ArtifactValidation>,
    val totalSizeBytes: Long,
    val summary: String
)

/**
 * Validation details for a single artifact
 */
public data class ArtifactValidation(
    val path: String,
    val isValid: Boolean,
    val exists: Boolean,
    val sizeBytes: Long,
    val hasClasses: Boolean,
    val hasManifest: Boolean,
    val errors: List<String>
)

/**
 * Information about the build environment
 */
public data class BuildEnvironmentInfo(
    val javaVersion: String,
    val gradleVersion: String,
    val diskUsage: String,
    val workspaceSize: Long
)