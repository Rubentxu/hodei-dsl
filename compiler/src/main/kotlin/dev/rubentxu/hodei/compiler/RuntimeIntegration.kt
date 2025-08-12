package dev.rubentxu.hodei.compiler

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Enhanced Runtime Integration for Pipeline DSL
 * 
 * Provides sophisticated runtime execution with intelligent compilation caching,
 * hot-reload capabilities, and advanced error handling.
 */
public class RuntimeIntegration(
    private val hybridCompiler: HybridCompiler,
    private val cacheManager: CacheManager,
    private val executionScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // Runtime state management
    private val activeExecutions = ConcurrentHashMap<String, RuntimeExecution>()
    private val _executionStatus = MutableStateFlow<Map<String, ExecutionStatus>>(emptyMap())
    
    // Hot-reload management
    private var hotReloadJob: Job? = null
    private val watchedScripts = ConcurrentHashMap<String, ScriptWatchInfo>()
    
    /**
     * Reactive execution status monitoring
     */
    public val executionStatus: StateFlow<Map<String, ExecutionStatus>> = _executionStatus.asStateFlow()
    
    /**
     * Executes a Pipeline DSL script with intelligent caching and error handling
     */
    public suspend fun executeScript(
        scriptContent: String,
        scriptName: String,
        libraries: List<LibraryConfiguration> = emptyList(),
        executionConfig: RuntimeExecutionConfig = RuntimeExecutionConfig.default()
    ): RuntimeExecutionResult {
        
        val executionId = generateExecutionId(scriptName)
        
        updateExecutionStatus(executionId, ExecutionStatus.Compiling(
            startedAt = kotlinx.datetime.Clock.System.now(),
            scriptName = scriptName
        ))
        
        return try {
            // Phase 1: Intelligent compilation with caching
            val compilationResult = compileWithIntelligentCaching(
                scriptContent, scriptName, libraries, executionId
            )
            
            when (compilationResult) {
                is HybridCompilationResult.Success -> {
                    updateExecutionStatus(executionId, ExecutionStatus.Executing(
                        startedAt = kotlinx.datetime.Clock.System.now(),
                        scriptName = scriptName,
                        compilationTimeMs = compilationResult.compilationMetrics.totalDuration.toMillis()
                    ))
                    
                    // Phase 2: Execute with enhanced runtime
                    executeCompiledScript(compilationResult, executionConfig, executionId)
                }
                
                is HybridCompilationResult.Failure -> {
                    val status = ExecutionStatus.Failed(
                        failedAt = kotlinx.datetime.Clock.System.now(),
                        scriptName = scriptName,
                        error = compilationResult.error,
                        phase = "compilation"
                    )
                    updateExecutionStatus(executionId, status)
                    
                    RuntimeExecutionResult.CompilationFailure(
                        executionId = executionId,
                        scriptName = scriptName,
                        compilationErrors = compilationResult.scriptErrors + compilationResult.libraryErrors.map {
                            ScriptError("Library ${it.configuration.name}: ${it.error}")
                        }
                    )
                }
            }
            
        } catch (e: Exception) {
            val status = ExecutionStatus.Failed(
                failedAt = kotlinx.datetime.Clock.System.now(),
                scriptName = scriptName,
                error = "Runtime error: ${e.message}",
                phase = "execution"
            )
            updateExecutionStatus(executionId, status)
            
            RuntimeExecutionResult.RuntimeFailure(
                executionId = executionId,
                scriptName = scriptName,
                error = e.message ?: "Unknown runtime error",
                cause = e
            )
        } finally {
            // Cleanup
            activeExecutions.remove(executionId)
        }
    }
    
    /**
     * Enables hot-reload for automatic script recompilation and execution
     */
    public suspend fun enableHotReload(
        watchConfigs: List<ScriptWatchConfig>,
        onScriptReloaded: suspend (String, RuntimeExecutionResult) -> Unit = { _, _ -> }
    ): Job {
        
        // Cancel existing hot-reload if active
        hotReloadJob?.cancel()
        
        hotReloadJob = executionScope.launch {
            // Initialize watch information
            watchConfigs.forEach { config ->
                watchedScripts[config.scriptName] = ScriptWatchInfo(
                    config = config,
                    lastContentHash = calculateContentHash(config.scriptContent),
                    lastReload = kotlinx.datetime.Clock.System.now()
                )
            }
            
            // Hot-reload monitoring loop
            while (isActive) {
                delay(2.seconds.inWholeMilliseconds) // Poll every 2 seconds
                
                for ((scriptName, watchInfo) in watchedScripts) {
                    try {
                        // Check if script content has changed
                        val currentHash = calculateContentHash(watchInfo.config.scriptContent)
                        
                        if (currentHash != watchInfo.lastContentHash) {
                            // Script changed - trigger reload
                            val reloadResult = executeScript(
                                scriptContent = watchInfo.config.scriptContent,
                                scriptName = scriptName,
                                libraries = watchInfo.config.libraries,
                                executionConfig = watchInfo.config.executionConfig
                            )
                            
                            // Update watch info
                            watchedScripts[scriptName] = watchInfo.copy(
                                lastContentHash = currentHash,
                                lastReload = kotlinx.datetime.Clock.System.now()
                            )
                            
                            // Notify callback
                            onScriptReloaded(scriptName, reloadResult)
                        }
                        
                    } catch (e: Exception) {
                        // Log error but continue monitoring other scripts
                        println("Hot-reload error for $scriptName: ${e.message}")
                    }
                }
            }
        }
        
        return hotReloadJob!!
    }
    
    /**
     * Stops hot-reload monitoring
     */
    public fun disableHotReload() {
        hotReloadJob?.cancel()
        hotReloadJob = null
        watchedScripts.clear()
    }
    
    /**
     * Gets comprehensive runtime statistics and metrics
     */
    public fun getRuntimeStatistics(): RuntimeStatistics {
        val cacheStats = cacheManager.getCacheStatistics()
        val currentExecutions = activeExecutions.values.toList()
        
        return RuntimeStatistics(
            activeExecutions = currentExecutions.size,
            completedExecutions = _executionStatus.value.size - currentExecutions.size,
            cacheStatistics = cacheStats,
            hotReloadActive = hotReloadJob?.isActive == true,
            watchedScriptsCount = watchedScripts.size,
            averageExecutionTime = calculateAverageExecutionTime()
        )
    }
    
    /**
     * Advanced script execution environment with custom classloaders
     */
    public suspend fun executeInIsolatedEnvironment(
        scriptContent: String,
        scriptName: String,
        libraries: List<LibraryConfiguration> = emptyList(),
        isolationConfig: IsolationConfig = IsolationConfig.default()
    ): RuntimeExecutionResult {
        
        return withContext(Dispatchers.IO) {
            val compilationResult = hybridCompiler.compileWithLibraries(
                scriptContent, scriptName, libraries
            )
            
            when (compilationResult) {
                is HybridCompilationResult.Success -> {
                    // Create isolated classloader from library results
                    val libraryJars = compilationResult.libraryResults.values
                        .map { it.metadata.jarFile }
                    val isolatedClassLoader = createIsolatedClassLoader(
                        libraryJars,
                        isolationConfig
                    )
                    
                    try {
                        // Execute in isolated environment
                        executeWithIsolatedClassLoader(
                            compilationResult,
                            isolatedClassLoader,
                            scriptName
                        )
                        
                    } finally {
                        // Cleanup isolated resources
                        cleanupIsolatedClassLoader(isolatedClassLoader)
                    }
                }
                
                is HybridCompilationResult.Failure -> {
                    RuntimeExecutionResult.CompilationFailure(
                        executionId = generateExecutionId(scriptName),
                        scriptName = scriptName,
                        compilationErrors = compilationResult.scriptErrors
                    )
                }
            }
        }
    }
    
    /**
     * Batch execution of multiple scripts with dependency management
     */
    public suspend fun executeBatch(
        scripts: List<BatchScriptConfig>,
        batchConfig: BatchExecutionConfig = BatchExecutionConfig.default()
    ): BatchExecutionResult {
        
        val batchId = generateBatchId()
        val results = mutableMapOf<String, RuntimeExecutionResult>()
        val startTime = kotlinx.datetime.Clock.System.now()
        
        try {
            when (batchConfig.executionMode) {
                BatchExecutionMode.PARALLEL -> {
                    // Execute all scripts in parallel  
                    coroutineScope {
                        val deferredResults = scripts.map { script ->
                            async {
                                script.name to executeScript(
                                    script.content, script.name, script.libraries, script.executionConfig
                                )
                            }
                        }
                        
                        deferredResults.awaitAll().forEach { (name, result) ->
                            results[name] = result
                        }
                    }
                }
                
                BatchExecutionMode.SEQUENTIAL -> {
                    // Execute scripts one by one
                    for (script in scripts) {
                        val result = executeScript(
                            script.content, script.name, script.libraries, script.executionConfig
                        )
                        results[script.name] = result
                        
                        // Stop on first failure if configured
                        if (batchConfig.stopOnFirstFailure && !result.isSuccess) {
                            break
                        }
                    }
                }
                
                BatchExecutionMode.DEPENDENCY_ORDERED -> {
                    // Execute scripts based on dependency order
                    val executionOrder = resolveBatchDependencies(scripts)
                    for (script in executionOrder) {
                        val result = executeScript(
                            script.content, script.name, script.libraries, script.executionConfig
                        )
                        results[script.name] = result
                        
                        if (batchConfig.stopOnFirstFailure && !result.isSuccess) {
                            break
                        }
                    }
                }
            }
            
            val endTime = kotlinx.datetime.Clock.System.now()
            val successCount = results.values.count { it.isSuccess }
            
            return BatchExecutionResult.Success(
                batchId = batchId,
                executionResults = results,
                totalExecutionTime = endTime.minus(startTime),
                successfulScripts = successCount,
                failedScripts = results.size - successCount
            )
            
        } catch (e: Exception) {
            return BatchExecutionResult.Failure(
                batchId = batchId,
                error = "Batch execution failed: ${e.message}",
                partialResults = results,
                cause = e
            )
        }
    }
    
    // --- Private Implementation Methods ---
    
    private suspend fun compileWithIntelligentCaching(
        scriptContent: String,
        scriptName: String,
        libraries: List<LibraryConfiguration>,
        executionId: String
    ): HybridCompilationResult {
        
        // Check cache first
        val cachedScript = cacheManager.getCachedScript(scriptContent, scriptName)
        
        if (cachedScript != null) {
            // Use cached compilation but still need to handle libraries
            val libraryResult = if (libraries.isNotEmpty()) {
                hybridCompiler.compileWithLibraries("", scriptName, libraries)
            } else {
                HybridCompilationResult.Success(
                    scriptResult = CompilationResult.Success(
                        scriptContent = scriptContent,
                        scriptName = scriptName,
                        compiledScript = cachedScript.compiledScript,
                        fromCache = true
                    ),
                    libraryResults = emptyMap(),
                    classLoader = Thread.currentThread().contextClassLoader,
                    compilationMetrics = CompilationMetrics(
                        totalDuration = java.time.Duration.ZERO,
                        scriptCompilationDuration = java.time.Duration.ZERO,
                        libraryCount = 0,
                        cacheHits = 1
                    )
                )
            }
            
            // Cache the successful result
            when (libraryResult) {
                is HybridCompilationResult.Success -> {
                    cacheManager.cacheScript(scriptContent, scriptName, emptyList(), cachedScript)
                }
                else -> {} // Handle library compilation failure
            }
            
            return libraryResult
        }
        
        // Not cached - compile normally
        return hybridCompiler.compileWithLibraries(scriptContent, scriptName, libraries)
    }
    
    private suspend fun executeCompiledScript(
        compilationResult: HybridCompilationResult.Success,
        executionConfig: RuntimeExecutionConfig,
        executionId: String
    ): RuntimeExecutionResult {
        
        return withContext(executionConfig.executionDispatcher) {
            try {
                val startTime = kotlinx.datetime.Clock.System.now()
                
                // Execute the script using the compiler's execute method
                val executionResult = hybridCompiler.scriptCompiler.execute(compilationResult.scriptResult)
                
                val endTime = kotlinx.datetime.Clock.System.now()
                val executionTime = endTime.minus(startTime)
                
                updateExecutionStatus(executionId, ExecutionStatus.Completed(
                    completedAt = endTime,
                    scriptName = compilationResult.scriptResult.scriptName,
                    success = executionResult.isSuccess,
                    executionTimeMs = executionTime.inWholeMilliseconds
                ))
                
                when (executionResult) {
                    is ExecutionResult.Success -> {
                        RuntimeExecutionResult.Success(
                            executionId = executionId,
                            scriptName = compilationResult.scriptResult.scriptName,
                            result = executionResult.result,
                            executionTime = executionTime,
                            fromCache = compilationResult.scriptResult.fromCache,
                            libraryCount = compilationResult.libraryResults.size
                        )
                    }
                    
                    is ExecutionResult.Failure -> {
                        RuntimeExecutionResult.RuntimeFailure(
                            executionId = executionId,
                            scriptName = compilationResult.scriptResult.scriptName,
                            error = executionResult.error
                        )
                    }
                }
                
            } catch (e: Exception) {
                RuntimeExecutionResult.RuntimeFailure(
                    executionId = executionId,
                    scriptName = compilationResult.scriptResult.scriptName,
                    error = "Script execution failed: ${e.message}",
                    cause = e
                )
            }
        }
    }
    
    private fun createIsolatedClassLoader(
        libraryJars: List<java.io.File>,
        isolationConfig: IsolationConfig
    ): URLClassLoader {
        val urls = libraryJars.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, isolationConfig.parentClassLoader)
    }
    
    private suspend fun executeWithIsolatedClassLoader(
        compilationResult: HybridCompilationResult.Success,
        classLoader: URLClassLoader,
        scriptName: String
    ): RuntimeExecutionResult {
        
        val executionId = generateExecutionId(scriptName)
        
        return withContext(Dispatchers.IO) {
            try {
                // Set context classloader for execution
                val originalClassLoader = Thread.currentThread().contextClassLoader
                Thread.currentThread().contextClassLoader = classLoader
                
                try {
                    val executionResult = hybridCompiler.scriptCompiler.execute(compilationResult.scriptResult)
                    
                    when (executionResult) {
                        is ExecutionResult.Success -> {
                            RuntimeExecutionResult.Success(
                                executionId = executionId,
                                scriptName = scriptName,
                                result = executionResult.result,
                                executionTime = kotlin.time.Duration.ZERO,
                                fromCache = false,
                                libraryCount = compilationResult.libraryResults.size
                            )
                        }
                        
                        is ExecutionResult.Failure -> {
                            RuntimeExecutionResult.RuntimeFailure(
                                executionId = executionId,
                                scriptName = scriptName,
                                error = executionResult.error
                            )
                        }
                    }
                } finally {
                    Thread.currentThread().contextClassLoader = originalClassLoader
                }
                
            } catch (e: Exception) {
                RuntimeExecutionResult.RuntimeFailure(
                    executionId = executionId,
                    scriptName = scriptName,
                    error = "Isolated execution failed: ${e.message}",
                    cause = e
                )
            }
        }
    }
    
    private fun cleanupIsolatedClassLoader(classLoader: URLClassLoader) {
        try {
            classLoader.close()
        } catch (e: Exception) {
            // Log cleanup error but don't fail
            println("Warning: Failed to cleanup isolated classloader: ${e.message}")
        }
    }
    
    private fun updateExecutionStatus(executionId: String, status: ExecutionStatus) {
        val currentStatus = _executionStatus.value.toMutableMap()
        currentStatus[executionId] = status
        _executionStatus.value = currentStatus
    }
    
    private fun generateExecutionId(scriptName: String): String {
        val timestamp = System.currentTimeMillis()
        return "${scriptName.replace(" ", "-")}-$timestamp"
    }
    
    private fun generateBatchId(): String {
        return "batch-${System.currentTimeMillis()}"
    }
    
    private fun calculateContentHash(content: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .fold("") { str, byte -> str + "%02x".format(byte) }
    }
    
    private fun calculateAverageExecutionTime(): kotlin.time.Duration {
        val completedExecutions = _executionStatus.value.values
            .filterIsInstance<ExecutionStatus.Completed>()
        
        if (completedExecutions.isEmpty()) return kotlin.time.Duration.ZERO
        
        val totalTimeMs = completedExecutions.sumOf { it.executionTimeMs }
        return kotlin.time.Duration.parse("${totalTimeMs / completedExecutions.size}ms")
    }
    
    private fun resolveBatchDependencies(scripts: List<BatchScriptConfig>): List<BatchScriptConfig> {
        // Simple dependency resolution - for now just return original order
        // In future versions, this would implement proper dependency graph resolution
        return scripts
    }
    
    public fun shutdown() {
        hotReloadJob?.cancel()
        executionScope.cancel()
    }
}

// --- Supporting Data Classes ---

/**
 * Runtime execution configuration
 */
public data class RuntimeExecutionConfig(
    val executionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val timeoutMs: Long = 30_000, // 30 seconds default
    val enableDebugging: Boolean = false,
    val captureOutput: Boolean = true
) {
    public companion object {
        public fun default(): RuntimeExecutionConfig = RuntimeExecutionConfig()
    }
}

/**
 * Script watch configuration for hot-reload
 */
public data class ScriptWatchConfig(
    val scriptName: String,
    val scriptContent: String,
    val libraries: List<LibraryConfiguration> = emptyList(),
    val executionConfig: RuntimeExecutionConfig = RuntimeExecutionConfig.default()
)

/**
 * Watch information for hot-reload
 */
private data class ScriptWatchInfo(
    val config: ScriptWatchConfig,
    val lastContentHash: String,
    val lastReload: kotlinx.datetime.Instant
)

/**
 * Isolation configuration for script execution
 */
public data class IsolationConfig(
    val parentClassLoader: ClassLoader? = Thread.currentThread().contextClassLoader
) {
    public companion object {
        public fun default(): IsolationConfig = IsolationConfig()
    }
}

/**
 * Batch script configuration
 */
public data class BatchScriptConfig(
    val name: String,
    val content: String,
    val libraries: List<LibraryConfiguration> = emptyList(),
    val executionConfig: RuntimeExecutionConfig = RuntimeExecutionConfig.default(),
    val dependencies: List<String> = emptyList()
)

/**
 * Batch execution configuration
 */
public data class BatchExecutionConfig(
    val executionMode: BatchExecutionMode = BatchExecutionMode.PARALLEL,
    val stopOnFirstFailure: Boolean = false,
    val maxConcurrency: Int = Runtime.getRuntime().availableProcessors()
) {
    public companion object {
        public fun default(): BatchExecutionConfig = BatchExecutionConfig()
    }
}

/**
 * Batch execution modes
 */
public enum class BatchExecutionMode {
    PARALLEL,
    SEQUENTIAL,
    DEPENDENCY_ORDERED
}

/**
 * Runtime execution status for monitoring
 */
public sealed class ExecutionStatus {
    public data class Compiling(
        val startedAt: kotlinx.datetime.Instant,
        val scriptName: String
    ) : ExecutionStatus()
    
    public data class Executing(
        val startedAt: kotlinx.datetime.Instant,
        val scriptName: String,
        val compilationTimeMs: Long
    ) : ExecutionStatus()
    
    public data class Completed(
        val completedAt: kotlinx.datetime.Instant,
        val scriptName: String,
        val success: Boolean,
        val executionTimeMs: Long
    ) : ExecutionStatus()
    
    public data class Failed(
        val failedAt: kotlinx.datetime.Instant,
        val scriptName: String,
        val error: String,
        val phase: String
    ) : ExecutionStatus()
}

/**
 * Runtime execution results
 */
public sealed class RuntimeExecutionResult {
    public abstract val executionId: String
    public abstract val scriptName: String
    public abstract val isSuccess: Boolean
    
    public data class Success(
        override val executionId: String,
        override val scriptName: String,
        val result: Any?,
        val executionTime: kotlin.time.Duration,
        val fromCache: Boolean,
        val libraryCount: Int
    ) : RuntimeExecutionResult() {
        override val isSuccess: Boolean = true
    }
    
    public data class CompilationFailure(
        override val executionId: String,
        override val scriptName: String,
        val compilationErrors: List<ScriptError>
    ) : RuntimeExecutionResult() {
        override val isSuccess: Boolean = false
    }
    
    public data class RuntimeFailure(
        override val executionId: String,
        override val scriptName: String,
        val error: String,
        val cause: Throwable? = null
    ) : RuntimeExecutionResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Batch execution results
 */
public sealed class BatchExecutionResult {
    public abstract val batchId: String
    public abstract val isSuccess: Boolean
    
    public data class Success(
        override val batchId: String,
        val executionResults: Map<String, RuntimeExecutionResult>,
        val totalExecutionTime: kotlin.time.Duration,
        val successfulScripts: Int,
        val failedScripts: Int
    ) : BatchExecutionResult() {
        override val isSuccess: Boolean = true
    }
    
    public data class Failure(
        override val batchId: String,
        val error: String,
        val partialResults: Map<String, RuntimeExecutionResult>,
        val cause: Throwable? = null
    ) : BatchExecutionResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Runtime statistics and metrics
 */
public data class RuntimeStatistics(
    val activeExecutions: Int,
    val completedExecutions: Int,
    val cacheStatistics: CacheStatistics,
    val hotReloadActive: Boolean,
    val watchedScriptsCount: Int,
    val averageExecutionTime: kotlin.time.Duration
)

/**
 * Runtime execution tracking
 */
private data class RuntimeExecution(
    val executionId: String,
    val scriptName: String,
    val startTime: kotlinx.datetime.Instant
)