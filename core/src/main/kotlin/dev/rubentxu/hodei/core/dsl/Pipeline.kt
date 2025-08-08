package dev.rubentxu.hodei.core.dsl

import dev.rubentxu.hodei.core.dsl.annotations.PipelineDSL
import dev.rubentxu.hodei.core.dsl.builders.*
import dev.rubentxu.hodei.core.domain.model.Pipeline

/**
 * Entry point for the Pipeline DSL
 * 
 * Creates a new pipeline using a type-safe DSL builder.
 * This function provides the main entry point for defining CI/CD pipelines
 * with Jenkins-compatible syntax and modern Kotlin features.
 * 
 * Example:
 * ```kotlin
 * val pipeline = pipeline {
 *     agent {
 *         docker {
 *             image = "gradle:7.5-jdk17"
 *         }
 *     }
 *     
 *     stage("Build") {
 *         steps {
 *             sh("./gradlew build")
 *             echo("Build completed")
 *         }
 *     }
 * }
 * ```
 * 
 * @param block Configuration block for the pipeline
 * @return Configured Pipeline instance
 */
public fun pipeline(block: PipelineBuilder.() -> Unit): Pipeline {
    val builder = PipelineBuilder()
    builder.apply(block)
    return builder.build()
}