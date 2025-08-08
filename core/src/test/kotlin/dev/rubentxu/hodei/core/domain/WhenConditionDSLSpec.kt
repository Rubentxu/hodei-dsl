package dev.rubentxu.hodei.core.domain

import dev.rubentxu.hodei.core.domain.model.WhenCondition
import dev.rubentxu.hodei.core.domain.model.whenCondition
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * BDD Specification for WhenCondition DSL Builder
 * 
 * Tests the fluent DSL for building complex when conditions
 * with functional predicates and logical operations.
 */
class WhenConditionDSLSpec : BehaviorSpec({
    
    given("WhenCondition DSL builder") {
        
        `when`("building simple conditions with DSL") {
            then("should create branch condition with DSL") {
                val condition = whenCondition { 
                    branch("main") 
                }
                
                condition.evaluate(mapOf("BRANCH_NAME" to "main")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "develop")) shouldBe false
            }
            
            then("should create environment condition with DSL") {
                val condition = whenCondition {
                    environment("DEPLOY_ENV", "production")
                }
                
                condition.evaluate(mapOf("DEPLOY_ENV" to "production")) shouldBe true
                condition.evaluate(mapOf("DEPLOY_ENV" to "staging")) shouldBe false
            }
            
            then("should create predicate condition with DSL") {
                val condition = whenCondition {
                    predicate { ctx -> (ctx["buildNumber"] as Int) > 100 }
                }
                
                condition.evaluate(mapOf("buildNumber" to 150)) shouldBe true
                condition.evaluate(mapOf("buildNumber" to 50)) shouldBe false
            }
        }
        
        `when`("combining conditions with logical operators") {
            then("should combine conditions with AND") {
                val condition = whenCondition {
                    branch("main") and environment("DEPLOY_ENV", "prod")
                }
                
                val validContext = mapOf(
                    "BRANCH_NAME" to "main", 
                    "DEPLOY_ENV" to "prod"
                )
                val invalidContext = mapOf(
                    "BRANCH_NAME" to "main", 
                    "DEPLOY_ENV" to "staging"
                )
                
                condition.evaluate(validContext) shouldBe true
                condition.evaluate(invalidContext) shouldBe false
            }
            
            then("should combine conditions with OR") {
                val condition = whenCondition {
                    branch("main") or branch("develop")
                }
                
                condition.evaluate(mapOf("BRANCH_NAME" to "main")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "develop")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "feature")) shouldBe false
            }
            
            then("should negate conditions with NOT") {
                val condition = whenCondition {
                    not(environment("SKIP_DEPLOY", "true"))
                }
                
                condition.evaluate(mapOf("SKIP_DEPLOY" to "false")) shouldBe true
                condition.evaluate(mapOf("SKIP_DEPLOY" to "true")) shouldBe false
                condition.evaluate(mapOf()) shouldBe true
            }
        }
        
        `when`("building complex nested conditions") {
            then("should handle production deployment logic") {
                val condition = whenCondition {
                    (branch("main") or branch("release/.*")) and 
                    environment("DEPLOY_ENV", "prod") and 
                    not(environment("SKIP_DEPLOY", "true")) and
                    predicate { ctx -> (ctx["buildNumber"] as Int) > 100 }
                }
                
                val validProdContext = mapOf(
                    "BRANCH_NAME" to "main",
                    "DEPLOY_ENV" to "prod",
                    "SKIP_DEPLOY" to "false", 
                    "buildNumber" to 150
                )
                
                val skipDeployContext = mapOf(
                    "BRANCH_NAME" to "main",
                    "DEPLOY_ENV" to "prod",
                    "SKIP_DEPLOY" to "true",
                    "buildNumber" to 150
                )
                
                val lowBuildContext = mapOf(
                    "BRANCH_NAME" to "main",
                    "DEPLOY_ENV" to "prod",
                    "SKIP_DEPLOY" to "false",
                    "buildNumber" to 50
                )
                
                condition.evaluate(validProdContext) shouldBe true
                condition.evaluate(skipDeployContext) shouldBe false
                condition.evaluate(lowBuildContext) shouldBe false
            }
            
            then("should handle feature branch testing logic") {
                val condition = whenCondition {
                    branch("feature/.*") and
                    (environment("RUN_TESTS", "true") or 
                     changeSet(".*\\.(java|kt)")) and
                    predicate { ctx ->
                        val changedFiles = ctx["CHANGED_FILES"] as? List<String> ?: emptyList()
                        changedFiles.isNotEmpty()
                    }
                }
                
                val featureWithTests = mapOf(
                    "BRANCH_NAME" to "feature/new-login",
                    "RUN_TESTS" to "true",
                    "CHANGED_FILES" to listOf("src/Login.kt")
                )
                
                val featureWithJavaChanges = mapOf(
                    "BRANCH_NAME" to "feature/auth-fix",
                    "RUN_TESTS" to "false",
                    "CHANGED_FILES" to listOf("src/Auth.java", "docs/README.md")
                )
                
                val featureNoChanges = mapOf(
                    "BRANCH_NAME" to "feature/docs-only",
                    "RUN_TESTS" to "false", 
                    "CHANGED_FILES" to listOf<String>()
                )
                
                condition.evaluate(featureWithTests) shouldBe true
                condition.evaluate(featureWithJavaChanges) shouldBe true
                condition.evaluate(featureNoChanges) shouldBe false
            }
        }
        
        `when`("using fluent infix operators") {
            then("should chain multiple AND operations") {
                val condition = whenCondition {
                    branch("main") and 
                    environment("ENV", "prod") and 
                    predicate { it["ready"] == true } and
                    not(environment("MAINTENANCE", "true"))
                }
                
                val readyContext = mapOf(
                    "BRANCH_NAME" to "main",
                    "ENV" to "prod",
                    "ready" to true,
                    "MAINTENANCE" to "false"
                )
                
                condition.evaluate(readyContext) shouldBe true
            }
            
            then("should chain multiple OR operations") {
                val condition = whenCondition {
                    branch("main") or 
                    branch("develop") or
                    branch("hotfix/.*") or
                    predicate { ctx -> 
                        val priority = ctx["PRIORITY"] as? String
                        priority == "critical"
                    }
                }
                
                condition.evaluate(mapOf("BRANCH_NAME" to "main")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "hotfix/urgent")) shouldBe true
                condition.evaluate(mapOf("PRIORITY" to "critical")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "feature/test")) shouldBe false
            }
        }
    }
})