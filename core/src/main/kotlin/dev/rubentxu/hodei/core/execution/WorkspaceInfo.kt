package dev.rubentxu.hodei.core.execution

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Workspace information for pipeline execution
 * 
 * Provides details about workspace directories, cleanup status,
 * and workspace-related configuration for pipeline execution.
 */
public data class WorkspaceInfo(
    /**
     * Root directory of the workspace
     */
    val rootDir: Path,
    
    /**
     * Temporary directory for build artifacts
     */
    val tempDir: Path,
    
    /**
     * Cache directory for dependencies and build cache
     */
    val cacheDir: Path = rootDir.resolve(".cache"),
    
    /**
     * Whether this is a clean workspace (no previous build artifacts)
     */
    val isCleanWorkspace: Boolean = false,
    
    /**
     * Workspace cleanup policy
     */
    val cleanupPolicy: WorkspaceCleanupPolicy = WorkspaceCleanupPolicy.AFTER_BUILD,
    
    /**
     * Maximum workspace size in bytes (0 = unlimited)
     */
    val maxSizeBytes: Long = 0L
) {
    
    init {
        require(rootDir.isAbsolute) { "Workspace root directory must be absolute path" }
        require(tempDir.isAbsolute) { "Workspace temp directory must be absolute path" }
    }
    
    /**
     * Gets the relative path from workspace root to given path
     * @param path Path to relativize
     * @return Relative path from workspace root
     */
    public fun relativize(path: Path): Path = rootDir.relativize(path)
    
    /**
     * Resolves a relative path against the workspace root
     * @param relativePath Relative path to resolve
     * @return Absolute path within workspace
     */
    public fun resolve(relativePath: String): Path = rootDir.resolve(relativePath)
    
    public companion object {
        /**
         * Creates default workspace info for current directory
         */
        public fun default(): WorkspaceInfo = WorkspaceInfo(
            rootDir = Paths.get(System.getProperty("user.dir")),
            tempDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("hodei-pipeline"),
            isCleanWorkspace = true
        )
        
        /**
         * Creates workspace info for temporary directory
         * @param prefix Prefix for temporary workspace directory
         */
        public fun temporary(prefix: String = "hodei-pipeline"): WorkspaceInfo {
            val tempRoot = Paths.get(System.getProperty("java.io.tmpdir"))
                .resolve("$prefix-${System.currentTimeMillis()}")
            
            return WorkspaceInfo(
                rootDir = tempRoot,
                tempDir = tempRoot.resolve("tmp"),
                cacheDir = tempRoot.resolve("cache"),
                isCleanWorkspace = true,
                cleanupPolicy = WorkspaceCleanupPolicy.ALWAYS
            )
        }
    }
}

/**
 * Workspace cleanup policy enumeration
 */
public enum class WorkspaceCleanupPolicy {
    /** Never clean workspace */
    NEVER,
    
    /** Clean after successful build */
    AFTER_BUILD,
    
    /** Clean before starting build */
    BEFORE_BUILD,
    
    /** Always clean workspace */
    ALWAYS
}