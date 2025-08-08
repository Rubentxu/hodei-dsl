package dev.rubentxu.hodei.compiler

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking

/**
 * Real integration tests for Script Compiler
 * Tests actual compilation and execution using Kotlin Scripting API
 */
public class RealScriptCompilationSpec : BehaviorSpec({
    
    given("a real script compiler") {
        val compiler = ScriptCompiler(
            ScriptConfig(
                defaultImports = setOf(
                    // These are the imports that would make pipeline DSL functions available
                    "kotlin.Unit",
                    "kotlin.String",
                    "kotlin.Int"
                ),
                enableCache = true
            )
        )
        
        `when`("compiling a simple Kotlin script") {
            then("should compile successfully") {
                val scriptContent = """
                    val result = "Hello from compiled script!"
                    result
                """.trimIndent()
                
                val compilationResult = runBlocking {
                    compiler.compile(scriptContent, "simple-test.kts")
                }
                
                compilationResult.isSuccess shouldBe true
                val success = compilationResult as CompilationResult.Success
                success.scriptName shouldBe "simple-test.kts"
                success.compiledScript shouldNotBe null
                success.fromCache shouldBe false
            }
        }
        
        `when`("executing a compiled script") {
            then("should execute and return result") {
                val scriptContent = """
                    val message = "Script executed successfully"
                    message
                """.trimIndent()
                
                val compilationResult = runBlocking {
                    compiler.compile(scriptContent, "execution-test.kts")
                }
                
                compilationResult.isSuccess shouldBe true
                val success = compilationResult as CompilationResult.Success
                
                val executionResult = runBlocking {
                    compiler.execute(success)
                }
                
                executionResult.isSuccess shouldBe true
                val execSuccess = executionResult as ExecutionResult.Success
                // The result should contain our message
                execSuccess.result.toString() shouldContain "Script executed successfully"
            }
        }
        
        `when`("compiling with cache enabled") {
            then("should use cache on second compilation") {
                val scriptContent = """
                    val cached = "This result is cached"
                    cached
                """.trimIndent()
                
                // First compilation
                val result1 = runBlocking {
                    compiler.compile(scriptContent, "cache-test.kts")
                }
                
                result1.isSuccess shouldBe true
                (result1 as CompilationResult.Success).fromCache shouldBe false
                
                // Second compilation (should be cached)
                val result2 = runBlocking {
                    compiler.compile(scriptContent, "cache-test.kts")
                }
                
                result2.isSuccess shouldBe true
                (result2 as CompilationResult.Success).fromCache shouldBe true
            }
        }
        
        `when`("compiling invalid Kotlin script") {
            then("should report compilation errors") {
                val scriptContent = """
                    val broken = undefinedVariable + "this will fail"
                    broken
                """.trimIndent()
                
                val result = runBlocking {
                    compiler.compile(scriptContent, "error-test.kts")
                }
                
                result.isSuccess shouldBe false
                val failure = result as CompilationResult.Failure
                failure.errors.isNotEmpty() shouldBe true
                failure.errors.first().message.lowercase() shouldContain "unresolved reference"
            }
        }
    }
    
    given("a script compiler with imports") {
        val compiler = ScriptCompiler(
            ScriptConfig(
                defaultImports = setOf(
                    "java.time.LocalDateTime",
                    "kotlin.math.*"
                ),
                enableCache = false
            )
        )
        
        `when`("using imported functions") {
            then("should have access to imported classes and functions") {
                val scriptContent = """
                    val now = LocalDateTime.now()
                    val result = max(5, 10)
                    "Time: ${'$'}now, Max: ${'$'}result"
                """.trimIndent()
                
                val compilationResult = runBlocking {
                    compiler.compile(scriptContent, "imports-test.kts")
                }
                
                compilationResult.isSuccess shouldBe true
                val success = compilationResult as CompilationResult.Success
                success.activeImports shouldContain "java.time.LocalDateTime"
                success.activeImports shouldContain "kotlin.math.*"
                
                val executionResult = runBlocking {
                    compiler.execute(success)
                }
                
                executionResult.isSuccess shouldBe true
                val execSuccess = executionResult as ExecutionResult.Success
                execSuccess.result.toString() shouldContain "Time:"
                execSuccess.result.toString() shouldContain "Max: 10"
            }
        }
    }
})