package dev.rubentxu.hodei.core.dsl.context

import dev.rubentxu.hodei.core.domain.model.*
import dev.rubentxu.hodei.core.dsl.builders.*
import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.dsl.pipeline

/**
 * Enhanced DSL syntax implementations
 * 
 * This file contains enhanced DSL syntax implementations that provide
 * more fluent and concise DSL syntax by enabling direct method calls
 * within nested scopes. This simulates context receivers functionality.
 */

/**
 * Context class for pipeline-level operations
 */
@PipelineDSL
class PipelineContext

/**
 * Context class for step-level operations
 */
@PipelineDSL
class StepsContext

/**
 * Enhanced steps context that maintains a steps list
 */
@PipelineDSL
class CollectingStepsContext {
    private val collectedSteps = mutableListOf<Step>()
    
    fun sh(script: String, returnStdout: Boolean = false, returnStatus: Boolean = false, encoding: String = "UTF-8") {
        collectedSteps.add(Step.Shell(script, returnStdout, returnStatus, encoding))
    }
    
    fun echo(message: String) {
        collectedSteps.add(Step.echo(message))
    }
    
    fun getSteps(): List<Step> = collectedSteps.toList()
}

/**
 * Enhanced pipeline builder function using simulated context receivers
 * 
 * Enables direct method calls on stages without explicit `steps {}` blocks
 */
fun pipelineWithContextReceivers(block: PipelineBuilderWithContext.() -> Unit): Pipeline {
    val enhancedBuilder = PipelineBuilderWithContext()
    enhancedBuilder.block()
    return enhancedBuilder.build()
}

/**
 * Enhanced steps builder function using simulated context receivers
 * 
 * Enables direct step method calls without explicit builder nesting
 */
fun buildStepsWithContextReceivers(block: StepsBuilderWithContext.() -> Unit): List<Step> {
    val enhancedBuilder = StepsBuilderWithContext()
    enhancedBuilder.block()
    return enhancedBuilder.build()
}

/**
 * Enhanced PipelineBuilder with simplified stage syntax
 */
@PipelineDSL
class PipelineBuilderWithContext {
    private val stages = mutableListOf<Stage>()
    
    /**
     * Enhanced stage method that allows direct step calls
     */
    fun stage(name: String, block: CollectingStepsContext.() -> Unit) {
        val collectingContext = CollectingStepsContext()
        collectingContext.block()
        
        // Create stage directly from collected steps
        val stage = Stage(
            name = name,
            steps = collectingContext.getSteps(),
            agent = null,
            environment = emptyMap(),
            whenCondition = null,
            post = emptyList()
        )
        stages.add(stage)
    }
    
    fun build(): Pipeline = pipeline {
        this@PipelineBuilderWithContext.stages.forEach { stage ->
            stage(stage.name) {
                steps {
                    stage.steps.forEach { step ->
                        when (step) {
                            is Step.Echo -> echo(step.message)
                            is Step.Shell -> sh(step.script, step.returnStdout, step.returnStatus, step.encoding)
                            else -> echo("Complex step: ${step::class.simpleName}")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enhanced StepsBuilder with direct method access
 */
@PipelineDSL
class StepsBuilderWithContext {
    private val stepsBuilder = StepsBuilder()
    
    fun sh(script: String, returnStdout: Boolean = false, returnStatus: Boolean = false, encoding: String = "UTF-8") {
        stepsBuilder.sh(script, returnStdout, returnStatus, encoding)
    }
    
    fun echo(message: String) {
        stepsBuilder.echo(message)
    }
    
    fun build(): List<Step> = stepsBuilder.build()
}