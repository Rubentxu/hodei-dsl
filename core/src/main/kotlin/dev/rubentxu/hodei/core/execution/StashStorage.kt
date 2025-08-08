package dev.rubentxu.hodei.core.execution

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for stash storage operations
 * 
 * Provides abstraction over stash storage mechanisms, enabling
 * different storage backends while maintaining consistent API.
 */
public interface StashStorage {
    
    /**
     * Stashes files matching the given patterns
     */
    public suspend fun stash(
        stashName: String,
        workspaceRoot: Path,
        includes: String,
        excludes: String
    ): StashResult
    
    /**
     * Unstashes previously stashed files
     */
    public suspend fun unstash(
        stashName: String,
        workspaceRoot: Path
    ): UnstashResult
    
    /**
     * Checks if a stash exists
     */
    public fun hasStash(stashName: String): Boolean
    
    /**
     * Lists all available stashes
     */
    public fun listStashes(): List<StashInfo>
    
    /**
     * Removes a stash
     */
    public fun removeStash(stashName: String): Boolean
}

/**
 * Result of stash operation
 */
public data class StashResult(
    val stashName: String,
    val stashedFiles: List<String>,
    val stashLocation: Path,
    val timestamp: Instant,
    val fileCount: Int,
    val totalSize: Long,
    val checksums: Map<String, String>
)

/**
 * Result of unstash operation
 */
public data class UnstashResult(
    val stashName: String,
    val restoredFiles: List<String>,
    val fileCount: Int
)

/**
 * Information about a stash
 */
public data class StashInfo(
    val name: String,
    val timestamp: Instant,
    val fileCount: Int,
    val totalSize: Long,
    val location: Path
)

/**
 * File system based stash storage implementation
 * 
 * Stores stashed files in a dedicated directory structure,
 * preserving directory hierarchy and file metadata.
 */
public class FileSystemStashStorage(
    private val stashBaseDir: Path
) : StashStorage {
    
    private val stashRegistry = ConcurrentHashMap<String, StashInfo>()
    
    init {
        stashBaseDir.createDirectories()
    }
    
    override suspend fun stash(
        stashName: String,
        workspaceRoot: Path,
        includes: String,
        excludes: String
    ): StashResult {
        val stashDir = stashBaseDir.resolve(stashName)
        
        // Clean existing stash if present
        if (stashDir.exists()) {
            stashDir.toFile().deleteRecursively()
        }
        stashDir.createDirectories()
        
        // Find files matching patterns
        val matchedFiles = findMatchingFiles(workspaceRoot, includes, excludes)
        val stashedFiles = mutableListOf<String>()
        val checksums = mutableMapOf<String, String>()
        var totalSize = 0L
        
        // Copy files to stash directory
        for (sourceFile in matchedFiles) {
            val relativePath = workspaceRoot.relativize(sourceFile)
            val targetFile = stashDir.resolve(relativePath)
            
            // Create parent directories
            targetFile.parent?.createDirectories()
            
            // Copy file
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
            
            // Record file info
            val relativePathStr = relativePath.toString()
            stashedFiles.add(relativePathStr)
            totalSize += sourceFile.fileSize()
            checksums[relativePathStr] = calculateChecksum(sourceFile)
        }
        
        val timestamp = Instant.now()
        val result = StashResult(
            stashName = stashName,
            stashedFiles = stashedFiles,
            stashLocation = stashDir,
            timestamp = timestamp,
            fileCount = stashedFiles.size,
            totalSize = totalSize,
            checksums = checksums
        )
        
        // Register stash
        stashRegistry[stashName] = StashInfo(
            name = stashName,
            timestamp = timestamp,
            fileCount = stashedFiles.size,
            totalSize = totalSize,
            location = stashDir
        )
        
        return result
    }
    
    override suspend fun unstash(
        stashName: String,
        workspaceRoot: Path
    ): UnstashResult {
        val stashDir = stashBaseDir.resolve(stashName)
        
        if (!stashDir.exists()) {
            throw IllegalArgumentException("Stash '$stashName' not found")
        }
        
        val restoredFiles = mutableListOf<String>()
        var fileCount = 0
        
        // Walk through stashed files and restore them
        Files.walk(stashDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .forEach { stashedFile ->
                    val relativePath = stashDir.relativize(stashedFile)
                    val targetFile = workspaceRoot.resolve(relativePath)
                    
                    // Create parent directories
                    targetFile.parent?.createDirectories()
                    
                    // Copy file back
                    Files.copy(stashedFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
                    
                    restoredFiles.add(relativePath.toString())
                    fileCount++
                }
        }
        
        return UnstashResult(
            stashName = stashName,
            restoredFiles = restoredFiles,
            fileCount = fileCount
        )
    }
    
    override fun hasStash(stashName: String): Boolean {
        return stashRegistry.containsKey(stashName) && 
               stashBaseDir.resolve(stashName).exists()
    }
    
    override fun listStashes(): List<StashInfo> {
        return stashRegistry.values.toList()
    }
    
    override fun removeStash(stashName: String): Boolean {
        val stashDir = stashBaseDir.resolve(stashName)
        val removed = if (stashDir.exists()) {
            stashDir.toFile().deleteRecursively()
        } else {
            false
        }
        
        stashRegistry.remove(stashName)
        return removed
    }
    
    /**
     * Finds files matching include/exclude patterns
     */
    private fun findMatchingFiles(
        workspaceRoot: Path,
        includes: String,
        excludes: String
    ): List<Path> {
        if (includes.isBlank()) return emptyList()
        
        val includePatterns = includes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val excludePatterns = excludes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        val matchedFiles = mutableSetOf<Path>()
        
        // Process each include pattern
        for (includePattern in includePatterns) {
            val matches = findFilesByGlobPattern(workspaceRoot, includePattern)
            matchedFiles.addAll(matches)
        }
        
        // Remove files matching exclude patterns
        for (excludePattern in excludePatterns) {
            val excludeMatches = findFilesByGlobPattern(workspaceRoot, excludePattern)
            matchedFiles.removeAll(excludeMatches.toSet())
        }
        
        return matchedFiles.filter { Files.isRegularFile(it) }
    }
    
    /**
     * Finds files matching a single glob pattern
     */
    private fun findFilesByGlobPattern(root: Path, pattern: String): List<Path> {
        val matchedFiles = mutableListOf<Path>()
        
        try {
            val pathMatcher = root.fileSystem.getPathMatcher("glob:$pattern")
            
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) }
                    .forEach { file ->
                        val relativePath = root.relativize(file)
                        if (pathMatcher.matches(relativePath)) {
                            matchedFiles.add(file)
                        }
                    }
            }
        } catch (e: Exception) {
            // If glob pattern is invalid, try as literal path
            val literalPath = root.resolve(pattern)
            if (literalPath.exists() && Files.isRegularFile(literalPath)) {
                matchedFiles.add(literalPath)
            }
        }
        
        return matchedFiles
    }
    
    /**
     * Calculates SHA-256 checksum of a file
     */
    private fun calculateChecksum(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(file.readBytes())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}