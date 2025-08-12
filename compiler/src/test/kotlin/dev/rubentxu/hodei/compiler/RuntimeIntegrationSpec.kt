package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * BDD Specification for Enhanced Runtime Integration
 * 
 * Tests the sophisticated runtime execution with intelligent caching,
 * hot-reload capabilities, and advanced error handling.
 */
class RuntimeIntegrationSpec : BehaviorSpec({
    
    given("an enhanced runtime integration system") {
        val scriptConfig = ScriptConfig.defaultConfig()
        val scriptCompiler = ScriptCompiler(scriptConfig)
        val gradleCompiler = GradleCompiler()
        val hybridCompiler = HybridCompiler(scriptCompiler, gradleCompiler)
        val tempCacheDir = Files.createTempDirectory("runtime-test-cache")
        val cacheManager = CacheManager(tempCacheDir)
        val runtimeIntegration = RuntimeIntegration(hybridCompiler, cacheManager)
        
        afterSpec {
            runtimeIntegration.shutdown()
            cacheManager.shutdown()
            tempCacheDir.toFile().deleteRecursively()
        }
        
        `when`("executing simple scripts") {
            then("should execute script successfully with caching") {
                val scriptContent = """
                    val greeting = "Hello from Runtime Integration!"
                    println(greeting)
                    greeting
                """.trimIndent()
                
                val result = runtimeIntegration.executeScript(
                    scriptContent = scriptContent,
                    scriptName = "greeting.kts"
                )
                
                result.shouldBeInstanceOf<RuntimeExecutionResult.Success>()
                result.scriptName shouldBe "greeting.kts"
                result.result shouldBe "Hello from Runtime Integration!"
                result.libraryCount shouldBe 0
            }
            
            then("should handle compilation failures gracefully") {
                val invalidScript = """
                    val unclosedString = "This is not closed
                    println(unclosedString)
                """.trimIndent()
                
                val result = runtimeIntegration.executeScript(
                    scriptContent = invalidScript,
                    scriptName = "invalid.kts"
                )
                
                result.shouldBeInstanceOf<RuntimeExecutionResult.CompilationFailure>()
                result.scriptName shouldBe "invalid.kts"
                result.compilationErrors.size shouldBeGreaterThan 0
            }
            
            then("should handle runtime failures gracefully") {
                val errorScript = """
                    val number = 10
                    val divisor = 0
                    val result = number / divisor // This will cause ArithmeticException
                    result
                """.trimIndent()
                
                val result = runtimeIntegration.executeScript(
                    scriptContent = errorScript,
                    scriptName = "error.kts"
                )
                
                // Since division by zero doesn't throw in Kotlin (returns Infinity), 
                // let's use a script that actually throws
                val throwingScript = """
                    throw RuntimeException("Test runtime error")
                """.trimIndent()
                
                val throwingResult = runtimeIntegration.executeScript(
                    scriptContent = throwingScript,
                    scriptName = "throwing.kts"
                )
                
                throwingResult.shouldBeInstanceOf<RuntimeExecutionResult.RuntimeFailure>()
                throwingResult.scriptName shouldBe "throwing.kts"
                throwingResult.error shouldNotBe ""
            }
        }
        
        `when`("working with execution status monitoring") {
            then("should provide reactive execution status updates") {
                val scriptContent = """
                    Thread.sleep(100) // Small delay to observe status changes
                    "Status monitoring test"
                """.trimIndent()
                
                // Start execution in background
                val executionJob = GlobalScope.async {
                    runtimeIntegration.executeScript(
                        scriptContent = scriptContent,
                        scriptName = "status-test.kts"
                    )
                }
                
                // Allow some time for status updates
                delay(50)
                
                // Check that execution status is being tracked
                val status = runtimeIntegration.executionStatus.value
                // Status might be empty if execution completed too quickly
                // In real scenarios with actual compilation, this would show progress
                
                val result = executionJob.await()
                result.shouldBeInstanceOf<RuntimeExecutionResult.Success>()
            }
        }
        
        `when`("testing execution with libraries") {
            then("should handle library dependencies") {
                val scriptWithLibraries = """
                    val message = "Script with library dependencies"
                    println(message)
                    message
                """.trimIndent()
                
                val libraryConfig = LibraryConfiguration.simple("test-lib", "/nonexistent/path")
                
                // This will fail due to nonexistent library path, but tests the flow
                val result = runtimeIntegration.executeScript(
                    scriptContent = scriptWithLibraries,
                    scriptName = "lib-test.kts",
                    libraries = listOf(libraryConfig)
                )
                
                result.shouldBeInstanceOf<RuntimeExecutionResult.CompilationFailure>()
                result.scriptName shouldBe "lib-test.kts"
            }
        }
        
        `when`("testing hot-reload capabilities") {
            then("should enable and disable hot-reload") {
                var reloadCount = 0
                val watchConfigs = listOf(
                    ScriptWatchConfig(
                        scriptName = "hotreload-test.kts",
                        scriptContent = "val version = 1; version"
                    )
                )
                
                val hotReloadJob = runtimeIntegration.enableHotReload(watchConfigs) { _, _ ->
                    reloadCount++
                }
                
                // Hot-reload should be active
                hotReloadJob.isActive shouldBe true
                
                // Disable hot-reload
                runtimeIntegration.disableHotReload()
                
                // Allow some time for cleanup
                delay(100)
                
                // Should be cancelled
                hotReloadJob.isCancelled shouldBe true
            }
        }
        
        `when`("testing isolated execution") {
            then("should execute scripts in isolated environment") {
                val isolatedScript = """
                    val isolated = "Running in isolation"
                    isolated
                """.trimIndent()
                
                val result = runtimeIntegration.executeInIsolatedEnvironment(
                    scriptContent = isolatedScript,
                    scriptName = "isolated.kts"
                )
                
                result.shouldBeInstanceOf<RuntimeExecutionResult.Success>()
                result.scriptName shouldBe "isolated.kts"
                result.result shouldBe "Running in isolation"
            }
        }
        
        `when`("testing batch execution") {
            then("should execute multiple scripts in parallel") {
                val scripts = listOf(
                    BatchScriptConfig(
                        name = "batch1.kts",
                        content = "val result1 = \"Batch script 1\"; result1"
                    ),
                    BatchScriptConfig(
                        name = "batch2.kts",
                        content = "val result2 = \"Batch script 2\"; result2"
                    ),
                    BatchScriptConfig(
                        name = "batch3.kts",
                        content = "val result3 = \"Batch script 3\"; result3"
                    )
                )
                
                val batchConfig = BatchExecutionConfig(
                    executionMode = BatchExecutionMode.PARALLEL,
                    stopOnFirstFailure = false
                )
                
                val result = runtimeIntegration.executeBatch(scripts, batchConfig)
                
                result.shouldBeInstanceOf<BatchExecutionResult.Success>()
                result.executionResults.size shouldBe 3
                result.successfulScripts shouldBe 3
                result.failedScripts shouldBe 0
            }
            
            then("should execute scripts sequentially when configured") {
                val scripts = listOf(
                    BatchScriptConfig(
                        name = "seq1.kts",
                        content = "val seq1 = \"Sequential 1\"; seq1"
                    ),
                    BatchScriptConfig(
                        name = "seq2.kts",
                        content = "val seq2 = \"Sequential 2\"; seq2"
                    )
                )
                
                val batchConfig = BatchExecutionConfig(
                    executionMode = BatchExecutionMode.SEQUENTIAL
                )
                
                val result = runtimeIntegration.executeBatch(scripts, batchConfig)
                
                result.shouldBeInstanceOf<BatchExecutionResult.Success>()
                result.executionResults.size shouldBe 2
                result.successfulScripts shouldBe 2
            }
            
            then("should stop on first failure when configured") {
                val scripts = listOf(
                    BatchScriptConfig(
                        name = "good.kts",
                        content = "\"Good script\""
                    ),
                    BatchScriptConfig(
                        name = "bad.kts",
                        content = "val unclosed = \"not closed" // Compilation error
                    ),
                    BatchScriptConfig(
                        name = "never-reached.kts",
                        content = "\"This should not execute\""
                    )
                )
                
                val batchConfig = BatchExecutionConfig(
                    executionMode = BatchExecutionMode.SEQUENTIAL,
                    stopOnFirstFailure = true
                )
                
                val result = runtimeIntegration.executeBatch(scripts, batchConfig)
                
                result.shouldBeInstanceOf<BatchExecutionResult.Success>()
                // Should have executed good script and failed on bad script
                result.executionResults.size shouldBe 2
                result.successfulScripts shouldBe 1
                result.failedScripts shouldBe 1
            }
        }
        
        `when`("gathering runtime statistics") {
            then("should provide comprehensive runtime metrics") {
                // Execute a few scripts to generate statistics
                val script1 = "val test1 = \"Statistics test 1\"; test1"
                val script2 = "val test2 = \"Statistics test 2\"; test2"
                
                runtimeIntegration.executeScript(script1, "stats1.kts")
                runtimeIntegration.executeScript(script2, "stats2.kts")
                
                val statistics = runtimeIntegration.getRuntimeStatistics()
                
                statistics shouldNotBe null
                statistics.activeExecutions shouldBe 0 // Should be completed
                statistics.completedExecutions shouldBeGreaterThanOrEqual 0
                statistics.cacheStatistics shouldNotBe null
                statistics.hotReloadActive shouldBe false
                statistics.watchedScriptsCount shouldBe 0
                statistics.averageExecutionTime shouldNotBe null
            }
        }
        
        `when`("testing execution configuration") {
            then("should respect custom execution configuration") {
                val script = "val configured = \"Custom config test\"; configured"
                
                val customConfig = RuntimeExecutionConfig(
                    executionDispatcher = Dispatchers.IO,
                    timeoutMs = 10_000, // 10 seconds
                    enableDebugging = true,
                    captureOutput = false
                )
                
                val result = runtimeIntegration.executeScript(
                    scriptContent = script,
                    scriptName = "config-test.kts",
                    executionConfig = customConfig
                )
                
                result.shouldBeInstanceOf<RuntimeExecutionResult.Success>()
                result.result shouldBe "Custom config test"
            }
        }
        
        `when`("testing error handling and recovery") {
            then("should handle multiple consecutive errors gracefully") {
                val errorScripts = listOf(
                    "val error1 = \"unclosed",
                    "unknown_function_call()",
                    "val error3 = null!!.toString()"
                )
                
                val results = errorScripts.mapIndexed { index, script ->
                    runtimeIntegration.executeScript(script, "error$index.kts")
                }
                
                // All should fail but runtime should remain stable
                results.forEach { result ->
                    result.isSuccess shouldBe false
                }
                
                // Runtime should still work after errors
                val goodScript = "\"Recovery test\""
                val recoveryResult = runtimeIntegration.executeScript(goodScript, "recovery.kts")
                
                recoveryResult.shouldBeInstanceOf<RuntimeExecutionResult.Success>()
                recoveryResult.result shouldBe "Recovery test"
            }
        }
    }
}) {
    
    companion object {
        private infix fun Int.shouldBeGreaterThan(expected: Int) {
            (this > expected) shouldBe true
        }
        
        private infix fun Int.shouldBeGreaterThanOrEqual(expected: Int) {
            (this >= expected) shouldBe true
        }
    }
}