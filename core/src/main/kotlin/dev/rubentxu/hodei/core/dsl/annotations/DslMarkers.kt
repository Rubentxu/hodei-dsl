package dev.rubentxu.hodei.core.dsl.annotations

/**
 * DSL Marker for Pipeline DSL scope safety
 * 
 * Prevents accidental mixing of different DSL scopes by making implicit receivers
 * from enclosing scopes unavailable in nested scopes.
 * 
 * Example of what this prevents:
 * ```
 * pipeline {
 *     stage("Build") {
 *         steps {
 *             sh("./gradlew build")
 *             // This would be prevented by @PipelineDSL:
 *             stage("Invalid") { } // Cannot access outer pipeline scope
 *         }
 *     }
 * }
 * ```
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
public annotation class PipelineDSL