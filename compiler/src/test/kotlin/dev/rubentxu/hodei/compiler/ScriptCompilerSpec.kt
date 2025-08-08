package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import kotlinx.coroutines.runBlocking
import kotlin.script.experimental.api.*

/**
 * BDD Specification for Template-Based Script Compiler
 * 
 * Tests the simplified compiler implementation with auto-imports
 * that is compatible with the plugin system without receiver conflicts.
 */
public class ScriptCompilerSpec : BehaviorSpec({
    
    given("a Script Compiler with template-based auto-imports") {
        val compiler = ScriptCompiler(
            ScriptConfig(
                defaultImports = setOf(
                    "dev.rubentxu.hodei.core.dsl.*",
                    "dev.rubentxu.hodei.core.dsl.builders.*",
                    "dev.rubentxu.hodei.core.domain.model.*"
                ),
                enableCache = true
            )
        )
        
        `when`("compiling a simple .pipeline.kts script without imports") {
            then("should compile successfully with auto-imports") {
                val scriptContent = """
                    val myPipeline = pipeline {
                        stage("Build") {
                            steps {
                                sh("./gradlew build")
                                echo("Build completed")
                            }
                        }
                    }
                """.trimIndent()
                
                val result = runBlocking { compiler.compile(scriptContent, "simple-test.pipeline.kts") }
                
                result.isSuccess shouldBe true
                val successResult = result as CompilationResult.Success
                successResult.scriptName shouldBe "simple-test.pipeline.kts"
                successResult.errors shouldBe emptyList()
                successResult.activeImports shouldContain "dev.rubentxu.hodei.core.dsl.*"
            }
        }
        
        `when`("executing a compiled script") {
            then("should execute successfully") {
                val scriptContent = """
                    val result = pipeline {
                        stage("Test") {
                            steps {
                                sh("echo 'testing'")
                            }
                        }
                    }
                    result
                """.trimIndent()
                
                val compilationResult = runBlocking { compiler.compile(scriptContent, "execution-test.pipeline.kts") }
                
                compilationResult.isSuccess shouldBe true
                
                val successResult = compilationResult as CompilationResult.Success
                val executionResult = runBlocking { compiler.execute(successResult) }
                executionResult.isSuccess shouldBe true
            }
        }
    }
    
    given("a script compiler with plugin integration") {
        val pluginRegistry = MockPluginRegistry()
        val compiler = ScriptCompiler(
            ScriptConfig.withPluginSupport(pluginRegistry)
        )
        
        `when`("no plugins are loaded") {
            then("should compile with only core DSL functions") {
                val scriptContent = """
                    val pipeline = pipeline {
                        stage("Core Only") {
                            steps {
                                sh("echo core")
                                echo("message")
                            }
                        }
                    }
                """.trimIndent()
                
                val result = runBlocking { compiler.compile(scriptContent, "core-only.pipeline.kts") }
                
                result.isSuccess shouldBe true
                val successResult = result as CompilationResult.Success
                successResult.availablePlugins shouldBe emptyList()
            }
        }
        
        `when`("docker plugin is loaded") {
            then("should add docker imports automatically").config(enabled = false) {
                // TODO: Update this test to work with real compilation
                // This test needs Pipeline DSL functions to be available
                // Currently disabled until Pipeline DSL integration
            }
        }
    }
    
    given("script compiler error handling") {
        val compiler = ScriptCompiler(ScriptConfig.defaultConfig())
        
        `when`("script compilation encounters an error") {
            then("should report compilation errors") {
                val scriptContent = "invalid kotlin syntax {"
                
                // With our real implementation, this should properly fail
                val result = runBlocking { compiler.compile(scriptContent, "error-test.pipeline.kts") }
                
                // Real implementation should detect syntax errors
                result.isSuccess shouldBe false
            }
        }
    }
})

// Mock implementations for testing

public class MockPluginRegistry : PluginRegistry {
    private val plugins = mutableMapOf<String, Plugin>()
    
    override fun loadPlugin(plugin: Plugin): PluginLoadResult {
        plugins[plugin.id] = plugin
        return PluginLoadResult.Success(plugin.id, plugin.version)
    }
    
    override fun unloadPlugin(pluginId: String): Boolean {
        return plugins.remove(pluginId) != null
    }
    
    override fun reloadPlugin(pluginId: String, newPlugin: Plugin): PluginLoadResult {
        plugins[pluginId] = newPlugin
        return PluginLoadResult.Success(pluginId, newPlugin.version)
    }
    
    override fun getLoadedPlugins(): List<String> = plugins.keys.toList()
    
    override fun getPlugin(pluginId: String): Plugin? = plugins[pluginId]
}

public class MockDockerPlugin(
    override val version: String = "1.0.0",
    override val minCoreVersion: String = "1.0.0"
) : Plugin {
    
    override val id: String = "docker.core"
    
    override fun generateImports(): Set<String> = setOf(
        "plugins.docker.generated.dockerBuild",
        "plugins.docker.generated.dockerPush"
    )
}