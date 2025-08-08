package dev.rubentxu.hodei.execution

import dev.rubentxu.hodei.core.domain.model.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.runBlocking

/**
 * Basic BDD Specification for PipelineExecutor
 * 
 * Tests essential pipeline execution functionality with simple sequential execution
 * to validate core Pipeline DSL integration.
 */
public class BasicPipelineExecutorSpec : BehaviorSpec({
    
    given("a PipelineExecutor") {
        val executor = PipelineExecutor()
        val context = ExecutionContext.default()
        
        `when`("executing a simple sequential pipeline") {
            then("should execute all stages in order") {
                val pipeline = Pipeline.builder()
                    .stage("Build") {
                        steps {
                            sh("echo 'Building...'")
                            sh("gradle build")
                        }
                    }
                    .stage("Test") {
                        steps {
                            sh("echo 'Testing...'")
                            sh("gradle test")
                        }
                    }
                    .build()
                
                val result = runBlocking { executor.execute(pipeline, context) }
                
                result.status shouldBe PipelineStatus.SUCCESS
                result.stages shouldHaveSize 2
                result.stages[0].stageName shouldBe "Build"
                result.stages[1].stageName shouldBe "Test"
                result.stages.all { it.status == StageStatus.SUCCESS } shouldBe true
                result.duration.toMillis() shouldBeGreaterThan 0L
                result.executionId shouldNotBe null
            }
        }
    }
})