package dev.rubentxu.hodei.compiler

import kotlinx.coroutines.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.reflect.KClass
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

/**
 * Real Script Compiler implementation with Kotlin Scripting API
 * Template-based auto-imports without receiver conflicts, compatible with plugin extension patterns.
 */
public class ScriptCompiler(
    private val config: ScriptConfig
) {
    
    private val scriptingHost = BasicJvmScriptingHost()
    private val compilationCache = ConcurrentHashMap<String, CompiledScript>()
    private val contentHashes = ConcurrentHashMap<String, String>()
    
    /**
     * Compiles a Pipeline script with auto-imports and plugin support using real Kotlin Scripting API
     */
    public suspend fun compile(scriptContent: String, scriptName: String): CompilationResult {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first if enabled
                if (config.enableCache) {
                    val cachedResult = checkCache(scriptContent, scriptName)
                    if (cachedResult != null) {
                        return@withContext cachedResult
                    }
                }
                
                // Create script source
                val scriptSource = scriptContent.toScriptSource(scriptName)
                
                // Build compilation configuration with auto-imports
                val compilationConfig = createCompilationConfiguration()
                
                // Compile script using Kotlin Scripting API
                val compilationResult = scriptingHost.compiler(scriptSource, compilationConfig)
                
                when (compilationResult) {
                    is ResultWithDiagnostics.Success -> {
                        val compiledScript = compilationResult.value
                        
                        // Cache successful compilation
                        if (config.enableCache) {
                            cacheCompilation(scriptContent, scriptName, compiledScript)
                        }
                        
                        CompilationResult.Success(
                            scriptContent = scriptContent,
                            scriptName = scriptName,
                            compiledScript = compiledScript,
                            availablePlugins = config.pluginRegistry?.getLoadedPlugins() ?: emptyList(),
                            activeImports = config.defaultImports,
                            fromCache = false
                        )
                    }
                    is ResultWithDiagnostics.Failure -> {
                        val errors = compilationResult.reports
                            .filter { it.severity == ScriptDiagnostic.Severity.ERROR }
                            .map { report ->
                                ScriptError(
                                    message = report.message,
                                    line = report.location?.start?.line,
                                    column = report.location?.start?.col
                                )
                            }
                        
                        CompilationResult.Failure(errors = errors)
                    }
                }
            } catch (e: Exception) {
                CompilationResult.Failure(
                    errors = listOf(
                        ScriptError(
                            message = "Compilation failed: ${e.message}",
                            line = null,
                            column = null
                        )
                    )
                )
            }
        }
    }
    
    /**
     * Creates script compilation configuration with template-based auto-imports
     */
    private fun createCompilationConfiguration(): ScriptCompilationConfiguration {
        return ScriptCompilationConfiguration {
            
            // Add default imports (template-based auto-imports)
            defaultImports(config.defaultImports.toList())
            
            // Add plugin imports if available
            config.pluginRegistry?.let { registry ->
                val pluginImports = registry.getLoadedPlugins()
                    .mapNotNull { pluginId -> registry.getPlugin(pluginId) }
                    .flatMap { plugin -> plugin.generateImports() }
                
                if (pluginImports.isNotEmpty()) {
                    defaultImports.append(pluginImports)
                }
            }
            
            // JVM configuration
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
            }
            
            // IDE support
            ide {
                acceptedLocations(ScriptAcceptedLocation.Everywhere)
            }
        }
    }
    
    /**
     * Checks compilation cache for existing compiled script
     */
    private fun checkCache(scriptContent: String, scriptName: String): CompilationResult.Success? {
        if (!config.enableCache) return null
        
        val contentHash = hashContent(scriptContent)
        val cachedHash = contentHashes[scriptName]
        
        if (cachedHash == contentHash) {
            val cachedScript = compilationCache[scriptName]
            if (cachedScript != null) {
                return CompilationResult.Success(
                    scriptContent = scriptContent,
                    scriptName = scriptName,
                    compiledScript = cachedScript,
                    availablePlugins = config.pluginRegistry?.getLoadedPlugins() ?: emptyList(),
                    activeImports = config.defaultImports,
                    fromCache = true
                )
            }
        }
        
        return null
    }
    
    /**
     * Caches successful compilation
     */
    private fun cacheCompilation(scriptContent: String, scriptName: String, compiledScript: CompiledScript) {
        val contentHash = hashContent(scriptContent)
        compilationCache[scriptName] = compiledScript
        contentHashes[scriptName] = contentHash
    }
    
    /**
     * Generates hash for script content to detect changes
     */
    private fun hashContent(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(content.toByteArray())
        return hash.fold("") { str, byte -> str + "%02x".format(byte) }
    }
    
    /**
     * Executes a compiled script using Kotlin Scripting API
     */
    public suspend fun execute(result: CompilationResult.Success): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val evaluationResult = scriptingHost.evaluator(
                    result.compiledScript,
                    ScriptEvaluationConfiguration {}
                )
                
                when (evaluationResult) {
                    is ResultWithDiagnostics.Success -> {
                        // Get the script evaluation result
                        val scriptResult = evaluationResult.value.returnValue
                        ExecutionResult.Success(result = scriptResult)
                    }
                    is ResultWithDiagnostics.Failure -> {
                        val errors = evaluationResult.reports.joinToString("; ") { it.message }
                        ExecutionResult.Failure(error = "Script execution failed: $errors")
                    }
                }
            } catch (e: Exception) {
                ExecutionResult.Failure(error = "Script execution failed: ${e.message}")
            }
        }
    }
    
    /**
     * Invalidates compilation cache (used when plugins are loaded/unloaded)
     */
    public fun invalidateCache() {
        compilationCache.clear()
        contentHashes.clear()
    }
}

/**
 * Configuration for the Script Compiler
 */
public data class ScriptConfig(
    /**
     * Default imports automatically added to all scripts
     */
    val defaultImports: Set<String> = setOf(
        "dev.rubentxu.hodei.core.dsl.*",
        "dev.rubentxu.hodei.core.dsl.builders.*",
        "dev.rubentxu.hodei.core.domain.model.*",
        "dev.rubentxu.hodei.core.dsl.pipeline"
    ),
    
    /**
     * Plugin registry for dynamic imports (optional)
     */
    val pluginRegistry: PluginRegistry? = null,
    
    /**
     * Enable compilation caching for improved performance
     */
    val enableCache: Boolean = true
) {
    
    public companion object {
        /**
         * Creates a default configuration for Pipeline DSL scripts
         */
        public fun defaultConfig(): ScriptConfig = ScriptConfig()
        
        /**
         * Creates configuration with plugin support enabled
         */
        public fun withPluginSupport(pluginRegistry: PluginRegistry): ScriptConfig {
            return ScriptConfig(
                pluginRegistry = pluginRegistry,
                enableCache = true
            )
        }
    }
}

/**
 * Result of script compilation
 */
public sealed class CompilationResult {
    
    public abstract val isSuccess: Boolean
    
    public data class Success(
        val scriptContent: String,
        val scriptName: String,
        val compiledScript: CompiledScript,
        val availablePlugins: List<String> = emptyList(),
        val activeImports: Set<String> = emptySet(),
        val fromCache: Boolean = false
    ) : CompilationResult() {
        override val isSuccess: Boolean = true
        public val errors: List<ScriptError> = emptyList()
    }
    
    public data class Failure(
        val errors: List<ScriptError>
    ) : CompilationResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Result of script execution
 */
public sealed class ExecutionResult {
    
    public abstract val isSuccess: Boolean
    
    public data class Success(val result: Any?) : ExecutionResult() {
        override val isSuccess: Boolean = true
    }
    
    public data class Failure(val error: String) : ExecutionResult() {
        override val isSuccess: Boolean = false
    }
}

/**
 * Script compilation error information
 */
public data class ScriptError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val pluginId: String? = null
)