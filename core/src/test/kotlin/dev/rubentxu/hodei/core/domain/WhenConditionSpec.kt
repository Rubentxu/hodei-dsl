package dev.rubentxu.hodei.core.domain

import dev.rubentxu.hodei.core.domain.model.WhenCondition
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * BDD Specification for WhenCondition with Functional Predicates
 * 
 * Tests the complete WhenCondition system including functional predicates,
 * DSL builders, and complex condition combinations.
 */
class WhenConditionSpec : BehaviorSpec({
    
    given("WhenCondition with functional predicates") {
        
        `when`("using simple predicate conditions") {
            then("should evaluate build number correctly") {
                val condition = WhenCondition.predicate { ctx -> 
                    (ctx["buildNumber"] as Int) > 100 
                }
                
                condition.evaluate(mapOf("buildNumber" to 150)) shouldBe true
                condition.evaluate(mapOf("buildNumber" to 50)) shouldBe false
            }
            
            then("should evaluate string conditions correctly") {
                val condition = WhenCondition.predicate { ctx -> 
                    ctx["branch"] == "main" && ctx["env"] == "prod"
                }
                
                val prodContext = mapOf("branch" to "main", "env" to "prod")
                val devContext = mapOf("branch" to "develop", "env" to "dev")
                
                condition.evaluate(prodContext) shouldBe true
                condition.evaluate(devContext) shouldBe false
            }
            
            then("should handle null values safely") {
                val condition = WhenCondition.predicate { ctx -> 
                    ctx["status"]?.toString()?.startsWith("success") == true
                }
                
                condition.evaluate(mapOf("status" to "success-deployed")) shouldBe true
                condition.evaluate(mapOf("status" to "failed-deploy")) shouldBe false
                condition.evaluate(mapOf("other" to "value")) shouldBe false
            }
        }
        
        `when`("combining predicates with logical operators") {
            then("should evaluate AND conditions correctly") {
                val branchCondition = WhenCondition.predicate { it["branch"] == "main" }
                val envCondition = WhenCondition.predicate { it["env"] == "prod" }
                val andCondition = WhenCondition.and(branchCondition, envCondition)
                
                val validContext = mapOf("branch" to "main", "env" to "prod")
                val invalidContext = mapOf("branch" to "main", "env" to "dev")
                
                andCondition.evaluate(validContext) shouldBe true
                andCondition.evaluate(invalidContext) shouldBe false
            }
            
            then("should evaluate OR conditions correctly") {
                val mainBranch = WhenCondition.predicate { it["branch"] == "main" }
                val developBranch = WhenCondition.predicate { it["branch"] == "develop" }
                val orCondition = WhenCondition.or(mainBranch, developBranch)
                
                orCondition.evaluate(mapOf("branch" to "main")) shouldBe true
                orCondition.evaluate(mapOf("branch" to "develop")) shouldBe true
                orCondition.evaluate(mapOf("branch" to "feature")) shouldBe false
            }
            
            then("should evaluate NOT conditions correctly") {
                val skipCondition = WhenCondition.predicate { it["SKIP_DEPLOY"] == "true" }
                val notSkipCondition = WhenCondition.not(skipCondition)
                
                notSkipCondition.evaluate(mapOf("SKIP_DEPLOY" to "false")) shouldBe true
                notSkipCondition.evaluate(mapOf("SKIP_DEPLOY" to "true")) shouldBe false
                notSkipCondition.evaluate(mapOf()) shouldBe true // null != "true"
            }
        }
        
        `when`("using complex nested conditions") {
            then("should evaluate complex deployment logic") {
                // (branch == "main" OR branch == "develop") AND env == "prod" AND buildNumber > 100
                val mainOrDevelop = WhenCondition.or(
                    WhenCondition.predicate { it["branch"] == "main" },
                    WhenCondition.predicate { it["branch"] == "develop" }
                )
                val prodEnv = WhenCondition.predicate { it["env"] == "prod" }
                val highBuildNumber = WhenCondition.predicate { (it["buildNumber"] as Int) > 100 }
                
                val complexCondition = WhenCondition.and(
                    listOf(mainOrDevelop, prodEnv, highBuildNumber)
                )
                
                val validContext = mapOf(
                    "branch" to "main", 
                    "env" to "prod", 
                    "buildNumber" to 150
                )
                val invalidBranch = mapOf(
                    "branch" to "feature", 
                    "env" to "prod", 
                    "buildNumber" to 150
                )
                val invalidBuildNumber = mapOf(
                    "branch" to "main", 
                    "env" to "prod", 
                    "buildNumber" to 50
                )
                
                complexCondition.evaluate(validContext) shouldBe true
                complexCondition.evaluate(invalidBranch) shouldBe false
                complexCondition.evaluate(invalidBuildNumber) shouldBe false
            }
        }
    }
    
    given("existing WhenCondition implementations") {
        `when`("using branch conditions") {
            then("should match branch patterns correctly") {
                val condition = WhenCondition.branch("main")
                
                condition.evaluate(mapOf("BRANCH_NAME" to "main")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "develop")) shouldBe false
                condition.evaluate(mapOf()) shouldBe false
            }
            
            then("should support regex patterns") {
                val condition = WhenCondition.branch("feature/.*")
                
                condition.evaluate(mapOf("BRANCH_NAME" to "feature/new-login")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "feature/bug-fix")) shouldBe true
                condition.evaluate(mapOf("BRANCH_NAME" to "hotfix/critical")) shouldBe false
            }
        }
        
        `when`("using environment conditions") {
            then("should match environment variables") {
                val condition = WhenCondition.environment("DEPLOY_ENV", "production")
                
                condition.evaluate(mapOf("DEPLOY_ENV" to "production")) shouldBe true
                condition.evaluate(mapOf("DEPLOY_ENV" to "staging")) shouldBe false
                condition.evaluate(mapOf()) shouldBe false
            }
        }
        
        `when`("using changeset conditions") {
            then("should match changed files patterns") {
                val condition = WhenCondition.changeSet(".*\\.java")
                
                val javaChanges = mapOf("CHANGED_FILES" to listOf("src/Main.java", "test/Test.java"))
                val kotlinChanges = mapOf("CHANGED_FILES" to listOf("src/Main.kt", "test/Test.kt"))
                val mixedChanges = mapOf("CHANGED_FILES" to listOf("src/Main.java", "src/Other.kt"))
                
                condition.evaluate(javaChanges) shouldBe true
                condition.evaluate(kotlinChanges) shouldBe false
                condition.evaluate(mixedChanges) shouldBe true
            }
        }
    }
})