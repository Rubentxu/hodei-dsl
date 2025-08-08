package dev.rubentxu.hodei.compiler

/**
 * Simplified plugin system for dynamic imports and extension points
 * 
 * This system allows plugins to generate extension functions for Pipeline DSL builders
 * without receiver conflicts, maintaining compatibility with plugin extension patterns.
 */

/**
 * Registry interface for managing loaded plugins
 */
public interface PluginRegistry {
    
    /**
     * Loads a plugin and returns the result
     */
    public fun loadPlugin(plugin: Plugin): PluginLoadResult
    
    /**
     * Unloads a plugin by ID
     */
    public fun unloadPlugin(pluginId: String): Boolean
    
    /**
     * Reloads a plugin with a new version
     */
    public fun reloadPlugin(pluginId: String, newPlugin: Plugin): PluginLoadResult
    
    /**
     * Gets list of loaded plugin IDs
     */
    public fun getLoadedPlugins(): List<String>
    
    /**
     * Gets plugin instance by ID
     */
    public fun getPlugin(pluginId: String): Plugin?
}

/**
 * Plugin interface for extending Pipeline DSL functionality
 */
public interface Plugin {
    
    /**
     * Unique plugin identifier (e.g., "docker.core", "notifications.slack")
     */
    public val id: String
    
    /**
     * Plugin version (semantic versioning)
     */
    public val version: String
    
    /**
     * Minimum required core version
     */
    public val minCoreVersion: String
    
    /**
     * Generates import statements that this plugin provides
     * 
     * These imports make plugin extension functions available in Pipeline scripts.
     * For example, a Docker plugin might return:
     * - "plugins.docker.generated.dockerBuild"
     * - "plugins.docker.generated.dockerPush"
     */
    public fun generateImports(): Set<String>
}

/**
 * Result of plugin loading operation
 */
public sealed class PluginLoadResult {
    
    public data class Success(
        val pluginId: String, 
        val version: String
    ) : PluginLoadResult()
    
    public data class Failure(
        val pluginId: String, 
        val error: String
    ) : PluginLoadResult()
}

/**
 * Exception thrown when plugin dependencies cannot be resolved
 */
public class PluginDependencyException(message: String) : Exception(message)

/**
 * Exception thrown when plugin fails to load
 */
public class PluginLoadException(message: String) : Exception(message)