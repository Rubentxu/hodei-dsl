package dev.rubentxu.hodei.core.execution.handlers

import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking

/**
 * Integration test for all simple step handlers (FASE 2)
 * 
 * Tests that the new handler system works correctly for all simple step types.
 */
class SimpleStepHandlersIntegrationSpec : BehaviorSpec({
    
    given("the StepExecutor with all simple handlers registered") {
        val mockLauncher = mockk<CommandLauncher>()
        val context = ExecutionContext.builder()
            .launcher(mockLauncher)
            .build()
        val stepExecutor = StepExecutor()
        
        `when`("executing an Echo step") {
            then("should use EchoStepHandler") {
                runBlocking {
                    val step = Step.Echo("Hello from handler!")
                    val result = stepExecutor.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "echo"
                    result.output shouldBe "Hello from handler!"
                    result.metadata shouldNotBe null
                }
            }
        }
        
        `when`("executing a Shell step") {
            then("should use ShellStepHandler") {
                runBlocking {
                    // Mock the launcher response
                    coEvery {
                        mockLauncher.execute(any(), any(), any())
                    } returns CommandResult(
                        success = true,
                        exitCode = 0,
                        stdout = "Shell output",
                        stderr = "",
                        durationMs = 100L
                    )
                    
                    val step = Step.Shell("echo test")
                    val result = stepExecutor.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "sh"
                    result.output shouldBe "Shell output"
                    result.exitCode shouldBe 0
                }
            }
        }
        
        `when`("executing an ArchiveArtifacts step") {
            then("should use ArchiveArtifactsStepHandler") {
                runBlocking {
                    val step = Step.ArchiveArtifacts("*.jar", allowEmptyArchive = true, fingerprint = false)
                    val result = stepExecutor.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "archiveArtifacts"
                    result.output shouldBe "Archived artifacts: *.jar"
                    result.metadata["archivedFiles"] shouldBe "*.jar"
                    result.metadata["allowEmptyArchive"] shouldBe true
                    result.metadata["fingerprintEnabled"] shouldBe false
                }
            }
        }
        
        `when`("executing a PublishTestResults step") {
            then("should use PublishTestResultsStepHandler") {
                runBlocking {
                    val step = Step.PublishTestResults("**/test-results/*.xml", allowEmptyResults = false)
                    val result = stepExecutor.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "publishTestResults"
                    result.output shouldBe "Published test results: **/test-results/*.xml"
                    result.metadata["testResultsPattern"] shouldBe "**/test-results/*.xml"
                    result.metadata["allowEmptyResults"] shouldBe false
                }
            }
        }
        
        `when`("executing a Stash step") {
            then("should use StashStepHandler") {
                runBlocking {
                    val step = Step.Stash("build-artifacts", "build/**", "")
                    val result = stepExecutor.execute(step, context)
                    
                    result.status shouldBe StepStatus.SUCCESS
                    result.stepName shouldBe "stash"
                    result.output shouldBe "Stashed files: build/**"
                    result.metadata["stashName"] shouldNotBe null
                    result.metadata["includes"] shouldBe "build/**"
                }
            }
        }
        
        `when`("executing an Unstash step") {
            then("should use UnstashStepHandler") {
                runBlocking {
                    val step = Step.Unstash("build-artifacts")
                    val result = stepExecutor.execute(step, context)
                    
                    // Note: This might fail if no stash exists, but that's expected behavior
                    result.stepName shouldBe "unstash"
                    result.metadata["unstashName"] shouldNotBe null
                }
            }
        }
        
        `when`("validating steps with missing required fields") {
            then("should provide proper validation errors") {
                runBlocking {
                    val blankEcho = Step.Echo("")
                    val blankShell = Step.Shell("")
                    val blankArchive = Step.ArchiveArtifacts("", false, false)
                    val blankTestResults = Step.PublishTestResults("", false)
                    val blankStash = Step.Stash("", "", "")
                    val blankUnstash = Step.Unstash("")
                    
                    // Test that validation errors are caught
                    val echoResult = stepExecutor.execute(blankEcho, context)
                    val shellResult = stepExecutor.execute(blankShell, context)
                    val archiveResult = stepExecutor.execute(blankArchive, context)
                    val testResultsResult = stepExecutor.execute(blankTestResults, context)
                    val stashResult = stepExecutor.execute(blankStash, context)
                    val unstashResult = stepExecutor.execute(blankUnstash, context)
                    
                    echoResult.status shouldBe StepStatus.VALIDATION_FAILED
                    shellResult.status shouldBe StepStatus.VALIDATION_FAILED
                    archiveResult.status shouldBe StepStatus.VALIDATION_FAILED
                    testResultsResult.status shouldBe StepStatus.VALIDATION_FAILED
                    stashResult.status shouldBe StepStatus.VALIDATION_FAILED
                    unstashResult.status shouldBe StepStatus.VALIDATION_FAILED
                }
            }
        }
    }
})