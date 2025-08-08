package dev.rubentxu.hodei.core.execution

import dev.rubentxu.hodei.core.domain.model.Step
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * BDD Specification for Real Stash/Unstash System
 * 
 * Tests the complete stash system with real file operations,
 * glob pattern matching, and workspace integration.
 */
class StashSystemSpec : BehaviorSpec({
    
    given("a real stash system with file operations") {
        val tempDir = Files.createTempDirectory("hodei-stash-test")
        val workspace = WorkspaceInfo(
            rootDir = tempDir,
            tempDir = tempDir.resolve("temp"),
            cacheDir = tempDir.resolve("cache"),
            isCleanWorkspace = false
        )
        val context = ExecutionContext.default()
        val executor = StepExecutor()
        
        beforeContainer {
            // Create test file structure
            workspace.tempDir.createDirectories()
            workspace.cacheDir.createDirectories()
            
            // Create source files for testing
            tempDir.resolve("src").createDirectories()
            tempDir.resolve("src/main").createDirectories()
            tempDir.resolve("src/test").createDirectories()
            tempDir.resolve("docs").createDirectories()
            
            tempDir.resolve("src/main/Main.java").createFile().writeText("public class Main {}")
            tempDir.resolve("src/main/Utils.java").createFile().writeText("public class Utils {}")
            tempDir.resolve("src/test/MainTest.java").createFile().writeText("public class MainTest {}")
            tempDir.resolve("docs/README.md").createFile().writeText("# Project")
            tempDir.resolve("config.properties").createFile().writeText("env=test")
        }
        
        afterContainer {
            // Cleanup
            tempDir.toFile().deleteRecursively()
        }
        
        `when`("stashing files with glob patterns") {
            then("should stash Java files with *.java pattern") {
                val stashStep = Step.Stash(
                    name = "java-sources",
                    includes = "**/*.java",
                    excludes = ""
                )
                
                val result = runBlocking { executor.execute(stashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                result.output.contains("Stashed files") shouldBe true
                
                // Check stashed files metadata
                val metadata = result.metadata
                metadata["stashName"] shouldBe "java-sources"
                metadata["includes"] shouldBe "**/*.java"
                
                val stashedFiles = metadata["stashedFiles"] as List<String>
                stashedFiles shouldHaveSize 3
                stashedFiles shouldContain "src/main/Main.java"
                stashedFiles shouldContain "src/main/Utils.java"  
                stashedFiles shouldContain "src/test/MainTest.java"
            }
            
            then("should stash files with excludes pattern") {
                val stashStep = Step.Stash(
                    name = "sources-no-tests",
                    includes = "**/*.java",
                    excludes = "**/test/**"
                )
                
                val result = runBlocking { executor.execute(stashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                val stashedFiles = result.metadata["stashedFiles"] as List<String>
                stashedFiles shouldHaveSize 2
                stashedFiles shouldContain "src/main/Main.java"
                stashedFiles shouldContain "src/main/Utils.java"
                // Should NOT contain test files
                stashedFiles.none { it.contains("test") } shouldBe true
            }
            
            then("should handle multiple include patterns") {
                val stashStep = Step.Stash(
                    name = "java-and-docs",
                    includes = "**/*.java,**/*.md",
                    excludes = ""
                )
                
                val result = runBlocking { executor.execute(stashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                val stashedFiles = result.metadata["stashedFiles"] as List<String>
                stashedFiles shouldHaveSize 4
                stashedFiles shouldContain "docs/README.md"
            }
            
            then("should preserve directory structure in stash") {
                val stashStep = Step.Stash(
                    name = "structured-stash",
                    includes = "src/**/*.java",
                    excludes = ""
                )
                
                val result = runBlocking { executor.execute(stashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                val metadata = result.metadata
                
                // Check that stash location preserves structure
                val stashLocation = metadata["stashLocation"] as Path
                stashLocation.exists() shouldBe true
                stashLocation.resolve("src/main/Main.java").exists() shouldBe true
                stashLocation.resolve("src/main/Utils.java").exists() shouldBe true
            }
        }
        
        `when`("unstashing files") {
            then("should restore previously stashed files") {
                // First stash some files
                val stashStep = Step.Stash(
                    name = "test-stash",
                    includes = "**/*.java",
                    excludes = ""
                )
                runBlocking { executor.execute(stashStep, context) }
                
                // Clear the workspace
                tempDir.resolve("src").toFile().deleteRecursively()
                
                // Now unstash
                val unstashStep = Step.Unstash(name = "test-stash")
                val result = runBlocking { executor.execute(unstashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                result.output shouldBe "Unstashed: test-stash"
                
                // Verify files are restored
                tempDir.resolve("src/main/Main.java").exists() shouldBe true
                tempDir.resolve("src/main/Utils.java").exists() shouldBe true
                tempDir.resolve("src/test/MainTest.java").exists() shouldBe true
            }
            
            then("should fail when unstashing non-existent stash") {
                val unstashStep = Step.Unstash(name = "non-existent-stash")
                val result = runBlocking { executor.execute(unstashStep, context) }
                
                result.status shouldBe StepStatus.FAILURE
                result.error shouldNotBe null
                result.error!! shouldNotBe null
            }
        }
        
        `when`("managing stash lifecycle") {
            then("should track stash metadata correctly") {
                val stashStep = Step.Stash(
                    name = "metadata-test",
                    includes = "config.properties",
                    excludes = ""
                )
                
                val result = runBlocking { executor.execute(stashStep, context) }
                
                val metadata = result.metadata
                metadata["stashName"] shouldBe "metadata-test"
                metadata["timestamp"] shouldNotBe null
                metadata["fileCount"] shouldBe 1
                metadata["totalSize"] shouldNotBe null
                
                val checksums = metadata["checksums"] as Map<String, String>
                checksums["config.properties"] shouldNotBe null
            }
            
            then("should handle concurrent stash operations") {
                val stashStep1 = Step.Stash(name = "concurrent-1", includes = "**/*.java", excludes = "")
                val stashStep2 = Step.Stash(name = "concurrent-2", includes = "**/*.md", excludes = "")
                
                val results = runBlocking {
                    listOf(
                        executor.execute(stashStep1, context),
                        executor.execute(stashStep2, context)
                    )
                }
                
                results.forEach { result ->
                    result.status shouldBe StepStatus.SUCCESS
                }
                
                // Both stashes should exist independently  
                val unstash1 = runBlocking { executor.execute(Step.Unstash("concurrent-1"), context) }
                val unstash2 = runBlocking { executor.execute(Step.Unstash("concurrent-2"), context) }
                
                unstash1.status shouldBe StepStatus.SUCCESS
                unstash2.status shouldBe StepStatus.SUCCESS
            }
        }
        
        `when`("handling edge cases") {
            then("should handle empty include patterns gracefully") {
                val stashStep = Step.Stash(name = "empty-stash", includes = "", excludes = "")
                val result = runBlocking { executor.execute(stashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                val stashedFiles = result.metadata["stashedFiles"] as List<String>
                stashedFiles shouldHaveSize 0
            }
            
            then("should handle non-matching patterns") {
                val stashStep = Step.Stash(name = "no-match", includes = "**/*.nonexistent", excludes = "")
                val result = runBlocking { executor.execute(stashStep, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                val stashedFiles = result.metadata["stashedFiles"] as List<String>
                stashedFiles shouldHaveSize 0
            }
            
            then("should overwrite existing stash with same name") {
                // Create initial stash
                val initialStash = Step.Stash(name = "overwrite-test", includes = "**/*.java", excludes = "")
                runBlocking { executor.execute(initialStash, context) }
                
                // Create new files
                tempDir.resolve("NewFile.java").createFile().writeText("public class NewFile {}")
                
                // Stash again with same name
                val newStash = Step.Stash(name = "overwrite-test", includes = "**/*.java", excludes = "")
                val result = runBlocking { executor.execute(newStash, context) }
                
                result.status shouldBe StepStatus.SUCCESS
                val stashedFiles = result.metadata["stashedFiles"] as List<String>
                stashedFiles shouldContain "NewFile.java"
            }
        }
    }
})