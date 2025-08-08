package dev.rubentxu.hodei.core.execution

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * BDD Specification for ExecutionContext
 * 
 * Tests the complete execution context system including immutability,
 * thread safety, logging, workspace management, and component integration.
 * Ensures Jenkins API compatibility and clean architecture compliance.
 */
class ExecutionContextSpec : BehaviorSpec({
    
    given("a new ExecutionContext") {
        `when`("created with default factory") {
            then("should have all required properties initialized") {
                val context = ExecutionContext.default()
                
                context.workDir shouldNotBe null
                context.environment shouldNotBe null
                context.executionId shouldNotBe null
                context.buildId shouldNotBe null
                context.workspace shouldNotBe null
                context.jobInfo shouldNotBe null
                context.logger shouldNotBe null
                context.launcher shouldNotBe null
                context.metrics shouldNotBe null
                context.artifactDir shouldNotBe null
            }
        }
        
        `when`("created with builder pattern") {
            then("should allow customization of all properties") {
                val customWorkDir = Path.of("/custom/workspace")
                val customEnv = mapOf("CUSTOM_VAR" to "custom_value")
                val customExecutionId = "custom-execution-123"
                
                val context = ExecutionContext.builder()
                    .workDir(customWorkDir)
                    .environment(customEnv)
                    .executionId(customExecutionId)
                    .buildId("custom-build-456")
                    .build()
                
                context.workDir shouldBe customWorkDir
                context.environment shouldBe customEnv
                context.executionId shouldBe customExecutionId
                context.buildId shouldBe "custom-build-456"
            }
        }
        
        `when`("validating required fields") {
            then("should reject blank execution IDs") {
                shouldThrow<IllegalArgumentException> {
                    ExecutionContext.builder()
                        .executionId("")
                        .build()
                }.message shouldBe "Execution ID cannot be blank"
            }
            
            then("should reject blank build IDs") {
                shouldThrow<IllegalArgumentException> {
                    ExecutionContext.builder()
                        .buildId("")
                        .build()
                }.message shouldBe "Build ID cannot be blank"
            }
        }
    }
    
    given("an ExecutionContext for immutability") {
        `when`("using copy method for modifications") {
            then("should create new instance with changed properties") {
                val original = ExecutionContext.default()
                val newWorkDir = Path.of("/new/workspace")
                val newEnvironment = mapOf("NEW_VAR" to "new_value")
                
                val modified = original.copy(
                    workDir = newWorkDir,
                    environment = newEnvironment
                )
                
                // Original should be unchanged
                original.workDir shouldNotBe newWorkDir
                original.environment shouldNotBe newEnvironment
                
                // Modified should have new values
                modified.workDir shouldBe newWorkDir
                modified.environment shouldBe newEnvironment
                
                // Other properties should remain same
                modified.executionId shouldBe original.executionId
                modified.buildId shouldBe original.buildId
            }
        }
        
        `when`("using copy with partial updates") {
            then("should only change specified properties") {
                val original = ExecutionContext.default()
                val newWorkDir = Path.of("/partial/update")
                
                val modified = original.copy(workDir = newWorkDir)
                
                modified.workDir shouldBe newWorkDir
                modified.environment shouldBe original.environment
                modified.executionId shouldBe original.executionId
                modified.buildId shouldBe original.buildId
            }
        }
        
        `when`("accessing environment map") {
            then("should return immutable view") {
                val context = ExecutionContext.builder()
                    .environment(mutableMapOf("TEST" to "value"))
                    .build()
                
                val env = context.environment
                
                // Should not be able to modify returned environment
                shouldThrow<UnsupportedOperationException> {
                    (env as MutableMap<String, String>)["NEW_KEY"] = "new_value"
                }
            }
        }
    }
    
    given("an ExecutionContext for thread safety") {
        `when`("accessed concurrently from multiple threads") {
            then("should maintain consistency across threads") {
                val context = ExecutionContext.default()
                val iterations = 100
                
                runBlocking {
                    val jobs = (1..iterations).map { i ->
                        async {
                            val modified = context.copy(
                                environment = mapOf("THREAD_VAR_$i" to "value_$i")
                            )
                            Triple(i, modified.executionId, modified.environment)
                        }
                    }
                    
                    val results = jobs.awaitAll()
                    
                    // All should have same original executionId
                    results.forEach { (_, executionId, _) ->
                        executionId shouldBe context.executionId
                    }
                    
                    // Each should have unique environment
                    results.forEachIndexed { index, (i, _, env) ->
                        env shouldBe mapOf("THREAD_VAR_$i" to "value_$i")
                    }
                }
            }
        }
        
        `when`("creating multiple contexts simultaneously") {
            then("should generate unique execution IDs") {
                runBlocking {
                    val contexts = (1..50).map {
                        async {
                            ExecutionContext.default()
                        }
                    }.awaitAll()
                    
                    val executionIds = contexts.map { it.executionId }.toSet()
                    executionIds shouldHaveSize 50 // All should be unique
                }
            }
        }
    }
    
    given("an ExecutionContext with PipelineLogger") {
        `when`("using logger for different levels") {
            then("should support all log levels with metadata") {
                val context = ExecutionContext.default()
                val logger = context.logger
                
                // These should not throw exceptions
                logger.info("Info message", mapOf("key" to "value"))
                logger.warn("Warning message")
                logger.error("Error message", RuntimeException("test"), mapOf("error" to true))
                logger.debug("Debug message")
            }
        }
        
        `when`("using structured logging") {
            then("should support stdout/stderr and sections") {
                val context = ExecutionContext.default()
                val logger = context.logger
                
                logger.startSection("Build Process")
                logger.stdout("Building application...")
                logger.stderr("Warning: deprecated API used")
                logger.endSection()
                
                logger.logWithTimestamp(LogLevel.INFO, "Custom timestamp log", Instant.now())
            }
        }
    }
    
    given("an ExecutionContext with WorkspaceInfo") {
        `when`("accessing workspace information") {
            then("should provide workspace details") {
                val context = ExecutionContext.default()
                val workspace = context.workspace
                
                workspace.rootDir shouldNotBe null
                workspace.tempDir shouldNotBe null
                workspace.cacheDir shouldNotBe null
                workspace.isCleanWorkspace.shouldBeInstanceOf<Boolean>()
            }
        }
        
        `when`("configuring custom workspace") {
            then("should allow workspace customization") {
                val customRoot = Path.of("/custom/workspace")
                val customTemp = Path.of("/custom/temp")
                
                val workspace = WorkspaceInfo(
                    rootDir = customRoot,
                    tempDir = customTemp,
                    isCleanWorkspace = true
                )
                
                val context = ExecutionContext.builder()
                    .workspace(workspace)
                    .build()
                
                context.workspace.rootDir shouldBe customRoot
                context.workspace.tempDir shouldBe customTemp
                context.workspace.isCleanWorkspace shouldBe true
            }
        }
    }
    
    given("an ExecutionContext with JobInfo") {
        `when`("accessing job metadata") {
            then("should provide comprehensive job information") {
                val context = ExecutionContext.default()
                val jobInfo = context.jobInfo
                
                jobInfo.jobName shouldNotBe null
                jobInfo.buildNumber shouldNotBe null
                jobInfo.buildUrl shouldNotBe null
                jobInfo.gitCommit shouldNotBe null
                jobInfo.gitBranch shouldNotBe null
                jobInfo.gitUrl shouldNotBe null
                jobInfo.parameters shouldNotBe null
            }
        }
        
        `when`("creating custom job info") {
            then("should support all job metadata fields") {
                val jobInfo = JobInfo(
                    jobName = "custom-pipeline",
                    buildNumber = "123",
                    buildUrl = "https://ci.company.com/job/custom-pipeline/123/",
                    gitCommit = "abc123def456",
                    gitBranch = "feature/custom-feature",
                    gitUrl = "https://github.com/company/repo.git",
                    parameters = mapOf("DEPLOY_ENV" to "staging")
                )
                
                val context = ExecutionContext.builder()
                    .jobInfo(jobInfo)
                    .build()
                
                context.jobInfo shouldBe jobInfo
            }
        }
    }
    
    given("an ExecutionContext with PipelineMetrics") {
        `when`("tracking pipeline metrics") {
            then("should support metrics collection") {
                val context = ExecutionContext.default()
                val metrics = context.metrics
                
                metrics.startTime shouldNotBe null
                metrics.stepCount shouldBe 0
                metrics.stageCount shouldBe 0
                
                // Should support recording events
                val updatedMetrics = metrics.recordStepStart("test-step")
                updatedMetrics.stepCount shouldBe 1
            }
        }
        
        `when`("measuring execution time") {
            then("should track duration accurately") {
                val metrics = PipelineMetrics()
                Thread.sleep(100) // Small delay
                
                val duration = metrics.getElapsedTime()
                duration.inWholeMilliseconds.shouldBeGreaterThan(50L) // More lenient timing for CI
            }
        }
    }
    
    given("an ExecutionContext with CommandLauncher") {
        `when`("accessing command launcher") {
            then("should provide launcher interface") {
                val context = ExecutionContext.default()
                val launcher = context.launcher
                
                launcher shouldNotBe null
                launcher.shouldBeInstanceOf<CommandLauncher>()
            }
        }
        
        `when`("using different launcher implementations") {
            then("should support local and docker launchers") {
                val localLauncher = LocalCommandLauncher()
                val dockerLauncher = DockerCommandLauncher("ubuntu:20.04")
                
                localLauncher.shouldBeInstanceOf<CommandLauncher>()
                dockerLauncher.shouldBeInstanceOf<CommandLauncher>()
            }
        }
    }
    
    given("an ExecutionContext for validation") {
        `when`("validating context state") {
            then("should provide validation results") {
                val context = ExecutionContext.default()
                val errors = context.validate()
                
                errors shouldNotBe null
                errors.shouldBeInstanceOf<List<ValidationError>>()
            }
        }
        
        `when`("context has validation errors") {
            then("should report specific validation issues") {
                val invalidContext = ExecutionContext.builder()
                    .workDir(Path.of("/nonexistent/directory"))
                    .build()
                
                val errors = invalidContext.validate()
                errors shouldNotBe emptyList<ValidationError>()
                
                errors.any { it.field == "workDir" } shouldBe true
            }
        }
    }
    
    given("an ExecutionContext for environment integration") {
        `when`("merging with system environment") {
            then("should combine custom and system environment variables") {
                val customEnv = mapOf("CUSTOM_VAR" to "custom_value")
                val context = ExecutionContext.builder()
                    .environment(customEnv)
                    .mergeSystemEnvironment(true)
                    .build()
                
                context.environment.keys shouldContain "CUSTOM_VAR"
                context.environment["CUSTOM_VAR"] shouldBe "custom_value"
                // Should also contain some system variables
                context.environment.keys.size.shouldBeGreaterThan(1)
            }
        }
        
        `when`("using environment-specific context") {
            then("should create environment-aware contexts") {
                val devContext = ExecutionContext.forEnvironment("development")
                val prodContext = ExecutionContext.forEnvironment("production")
                
                devContext.environment["ENVIRONMENT"] shouldBe "development"
                prodContext.environment["ENVIRONMENT"] shouldBe "production"
                
                devContext.executionId shouldStartWith "dev-"
                prodContext.executionId shouldStartWith "pro-"
            }
        }
    }
})

/**
 * Test data classes and enums that should be implemented
 */
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}