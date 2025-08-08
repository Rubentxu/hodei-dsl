package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * BDD Specification for StepExecutor
 * 
 * Tests individual step execution including lifecycle management (validate, prepare, execute, cleanup),
 * different step types, workload-aware dispatcher selection, timeout handling, and integration
 * with CommandLauncher and ExecutionContext.
 */
class StepExecutorSpec : BehaviorSpec({
    
    given("a StepExecutor") {
        val executor = StepExecutor()
        val context = ExecutionContext.default()
        
        `when`("executing a shell step") {
            then("should execute command and return result") {
                val step = Step.Shell(
                    command = "echo 'Hello World'",
                    timeout = 5.seconds
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.output shouldContain "Hello World"
                result.duration.toMillis() shouldBeGreaterThan 0L
                result.stepName shouldBe "sh"
                result.exitCode shouldBe 0
            }
        }
        
        `when`("executing a shell step that fails") {
            then("should capture failure details") {
                val step = Step.Shell(
                    command = "exit 42",
                    timeout = 5.seconds
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.FAILURE
                result.exitCode shouldBe 42
                result.error shouldNotBe null
                result.duration.toMillis() shouldBeGreaterThan 0L
            }
        }
        
        `when`("executing an echo step") {
            then("should output message") {
                val step = Step.Echo(
                    message = "Pipeline step executed successfully"
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.output shouldContain "Pipeline step executed successfully"
                result.stepName shouldBe "echo"
                result.exitCode shouldBe 0
            }
        }
        
        `when`("executing a dir step") {
            then("should change working directory context") {
                val step = Step.Dir(
                    path = "/tmp",
                    steps = listOf(
                        Step.Shell("pwd"),
                        Step.Shell("echo 'Working in /tmp'")
                    )
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.output shouldContain "/tmp"
                result.stepName shouldBe "dir"
                result.nestedResults shouldHaveSize 2
                result.nestedResults.all { it.status == StepStatus.SUCCESS } shouldBe true
            }
        }
        
        `when`("executing a withEnv step") {
            then("should set environment variables for nested steps") {
                val step = Step.WithEnv(
                    variables = mapOf(
                        "TEST_VAR" to "test_value",
                        "CUSTOM_PATH" to "/custom/bin"
                    ),
                    steps = listOf(
                        Step.Shell("echo \"TEST_VAR=\$TEST_VAR\""),
                        Step.Shell("echo \"CUSTOM_PATH=\$CUSTOM_PATH\"")
                    )
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.output shouldContain "TEST_VAR=test_value"
                result.output shouldContain "CUSTOM_PATH=/custom/bin"
                result.nestedResults shouldHaveSize 2
            }
        }
        
        `when`("executing parallel step") {
            then("should execute branches concurrently") {
                val step = Step.Parallel(
                    branches = mapOf(
                        "Fast Branch" to listOf(
                            Step.Shell("echo 'Fast branch'"),
                            Step.Shell("sleep 0.1")
                        ),
                        "Medium Branch" to listOf(
                            Step.Shell("echo 'Medium branch'"),
                            Step.Shell("sleep 0.2")
                        ),
                        "Slow Branch" to listOf(
                            Step.Shell("echo 'Slow branch'"),
                            Step.Shell("sleep 0.3")
                        )
                    )
                )
                
                val startTime = System.currentTimeMillis()
                val result = executor.execute(step, context)
                val totalTime = System.currentTimeMillis() - startTime
                
                result.status shouldBe StepStatus.SUCCESS
                result.branchResults shouldHaveSize 3
                result.branchResults.values.all { it.status == StepStatus.SUCCESS } shouldBe true
                
                // Should complete in roughly 0.3s (longest branch) + overhead
                totalTime shouldBeGreaterThan 200L
                totalTime shouldBeLessThan 500L
            }
        }
        
        `when`("executing retry step") {
            then("should retry failed commands") {
                var attemptCount = 0
                val step = Step.Retry(
                    times = 3,
                    steps = listOf(
                        Step.Shell("test $attemptCount -eq 2 || (echo 'Attempt $attemptCount failed' && exit 1)")
                    )
                )
                
                // Mock a step that fails twice then succeeds
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.metadata["attemptCount"] shouldBe 3
                result.metadata["retriesUsed"] shouldBe 2
            }
        }
        
        `when`("executing timeout step") {
            then("should enforce timeout on nested steps") {
                val step = Step.Timeout(
                    duration = 200.milliseconds,
                    steps = listOf(
                        Step.Shell("sleep 1") // This will exceed timeout
                    )
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.TIMEOUT
                result.error.shouldBeInstanceOf<StepTimeoutException>()
                result.duration.toMillis() shouldBeLessThan 400L
            }
        }
        
        `when`("executing archiveArtifacts step") {
            then("should archive specified files") {
                val step = Step.ArchiveArtifacts(
                    artifacts = "build/libs/*.jar",
                    allowEmptyArchive = false,
                    fingerprint = true
                )
                
                // Create some dummy files first
                val setupContext = context.copy()
                executor.execute(Step.Shell("mkdir -p build/libs"), setupContext)
                executor.execute(Step.Shell("echo 'dummy' > build/libs/app.jar"), setupContext)
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.metadata["archivedFiles"] shouldNotBe null
                result.metadata["fingerprintEnabled"] shouldBe true
            }
        }
        
        `when`("executing publishTestResults step") {
            then("should publish test reports") {
                val step = Step.PublishTestResults(
                    testResultsPattern = "**/test-results/**/*.xml",
                    allowEmptyResults = true
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.metadata["testResultsPattern"] shouldBe "**/test-results/**/*.xml"
                result.metadata["allowEmptyResults"] shouldBe true
            }
        }
        
        `when`("executing stash step") {
            then("should stash files for later use") {
                val step = Step.Stash(
                    name = "build-artifacts",
                    includes = "build/**/*",
                    excludes = "**/*.tmp"
                )
                
                // Create some files to stash
                executor.execute(Step.Shell("mkdir -p build/output"), context)
                executor.execute(Step.Shell("echo 'artifact' > build/output/result.txt"), context)
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.metadata["stashName"] shouldBe "build-artifacts"
                result.metadata["stashedFiles"] shouldNotBe null
            }
        }
        
        `when`("executing unstash step") {
            then("should restore previously stashed files") {
                // First, stash some files
                val stashStep = Step.Stash(
                    name = "test-stash",
                    includes = "*.txt"
                )
                executor.execute(Step.Shell("echo 'stashed content' > test.txt"), context)
                executor.execute(stashStep, context)
                
                // Remove the original file
                executor.execute(Step.Shell("rm test.txt"), context)
                
                // Now unstash
                val unstashStep = Step.Unstash(
                    name = "test-stash"
                )
                
                val result = executor.execute(unstashStep, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.metadata["unstashName"] shouldBe "test-stash"
                
                // Verify file was restored
                val verifyResult = executor.execute(Step.Shell("cat test.txt"), context)
                verifyResult.output shouldContain "stashed content"
            }
        }
        
        `when`("step has validation errors") {
            then("should fail validation phase") {
                val invalidStep = Step.Shell(
                    command = "", // Invalid empty command
                    timeout = 5.seconds
                )
                
                val result = executor.execute(invalidStep, context)
                
                result.status shouldBe StepStatus.VALIDATION_FAILED
                result.error shouldNotBe null
                result.error?.message shouldContain "validation"
            }
        }
        
        `when`("step execution times out") {
            then("should cancel execution and return timeout result") {
                val step = Step.Shell(
                    command = "sleep 2",
                    timeout = 100.milliseconds
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.TIMEOUT
                result.error.shouldBeInstanceOf<StepTimeoutException>()
                result.duration.toMillis() shouldBeLessThan 300L
            }
        }
        
        `when`("executing step with custom workload type") {
            then("should use appropriate dispatcher") {
                val cpuIntensiveStep = Step.Shell(
                    command = "echo 'CPU intensive task'",
                    workloadType = WorkloadType.CPU_INTENSIVE
                )
                
                val ioIntensiveStep = Step.Shell(
                    command = "echo 'IO intensive task'",
                    workloadType = WorkloadType.IO_INTENSIVE
                )
                
                val networkStep = Step.Shell(
                    command = "echo 'Network task'",
                    workloadType = WorkloadType.NETWORK
                )
                
                val cpuResult = executor.execute(cpuIntensiveStep, context)
                val ioResult = executor.execute(ioIntensiveStep, context)
                val networkResult = executor.execute(networkStep, context)
                
                cpuResult.status shouldBe StepStatus.SUCCESS
                ioResult.status shouldBe StepStatus.SUCCESS
                networkResult.status shouldBe StepStatus.SUCCESS
                
                // Verify different dispatchers were used
                cpuResult.metadata["dispatcher"] shouldNotBe ioResult.metadata["dispatcher"]
                ioResult.metadata["dispatcher"] shouldNotBe networkResult.metadata["dispatcher"]
            }
        }
        
        `when`("step uses different command launchers") {
            then("should execute with appropriate launcher") {
                val localStep = Step.Shell("echo 'local execution'")
                val localContext = context.copy(
                    launcher = LocalCommandLauncher()
                )
                
                val dockerStep = Step.Shell("echo 'docker execution'")
                val dockerContext = context.copy(
                    launcher = DockerCommandLauncher("ubuntu:20.04")
                )
                
                val localResult = executor.execute(localStep, localContext)
                val dockerResult = executor.execute(dockerStep, dockerContext)
                
                localResult.status shouldBe StepStatus.SUCCESS
                dockerResult.status shouldBe StepStatus.SUCCESS
                
                localResult.metadata["launcher"] shouldBe "local"
                dockerResult.metadata["launcher"] shouldBe "docker"
            }
        }
        
        `when`("step execution is cancelled") {
            then("should handle cancellation gracefully") {
                val step = Step.Shell(
                    command = "sleep 10",
                    timeout = 30.seconds
                )
                
                runBlocking {
                    val job = kotlinx.coroutines.async {
                        executor.execute(step, context)
                    }
                    
                    delay(100) // Let execution start
                    job.cancel()
                    
                    shouldThrow<kotlinx.coroutines.CancellationException> {
                        job.await()
                    }
                }
            }
        }
        
        `when`("step requires preparation") {
            then("should execute preparation phase before main execution") {
                val step = Step.Shell(
                    command = "cat prepared_file.txt",
                    preparation = listOf(
                        Step.Shell("echo 'prepared content' > prepared_file.txt")
                    )
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.output shouldContain "prepared content"
                result.metadata["preparationExecuted"] shouldBe true
            }
        }
        
        `when`("step requires cleanup") {
            then("should execute cleanup phase after main execution") {
                val step = Step.Shell(
                    command = "echo 'main execution' > output.txt",
                    cleanup = listOf(
                        Step.Shell("rm -f output.txt"),
                        Step.Shell("echo 'cleanup completed'")
                    )
                )
                
                val result = executor.execute(step, context)
                
                result.status shouldBe StepStatus.SUCCESS
                result.metadata["cleanupExecuted"] shouldBe true
                
                // Verify cleanup was executed (file should be removed)
                val verifyResult = executor.execute(
                    Step.Shell("test -f output.txt && echo 'exists' || echo 'removed'"),
                    context
                )
                verifyResult.output shouldContain "removed"
            }
        }
    }
    
    given("a StepExecutor with metrics collection") {
        `when`("executing steps with metrics enabled") {
            then("should collect step execution metrics") {
                val metricsCollector = TestStepMetricsCollector()
                val executor = StepExecutor(
                    config = StepExecutorConfig.builder()
                        .enableMetrics(true)
                        .metricsCollector(metricsCollector)
                        .build()
                )
                
                val step = Step.Shell("echo 'metrics test'")
                
                executor.execute(step, context)
                
                metricsCollector.stepExecutionEvents shouldBe 1
                metricsCollector.lastStepName shouldBe "sh"
                metricsCollector.lastDuration shouldNotBe null
                metricsCollector.lastStatus shouldBe StepStatus.SUCCESS
            }
        }
    }
})

/**
 * Test implementation of step metrics collector
 */
private class TestStepMetricsCollector {
    var stepExecutionEvents = 0
    var lastStepName: String? = null
    var lastDuration: Duration? = null
    var lastStatus: StepStatus? = null
    
    fun recordStepExecution(stepName: String, stepType: String, duration: Duration, status: StepStatus) {
        stepExecutionEvents++
        lastStepName = stepType
        lastDuration = duration
        lastStatus = status
    }
}