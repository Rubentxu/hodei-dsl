package dev.rubentxu.hodei.core.integration

import dev.rubentxu.hodei.core.integration.container.ContainerCommandLauncher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.GenericContainer

/**
 * Basic container test to verify Testcontainers infrastructure
 */
class BasicContainerTestSpec : BehaviorSpec({
    
    val alpineContainer = GenericContainer("alpine:latest")
        .withCommand("tail", "-f", "/dev/null")
    
    listener(alpineContainer.perSpec())
    
    given("a running Alpine container") {
        
        `when`("executing basic commands") {
            then("should execute echo command") {
                val launcher = ContainerCommandLauncher(alpineContainer)
                val result = launcher.execute("echo 'Hello from container!'")
                
                result.success shouldBe true
                result.exitCode shouldBe 0
                result.stdout shouldContain "Hello from container!"
            }
            
            then("should execute ls command") {
                val launcher = ContainerCommandLauncher(alpineContainer)
                val result = launcher.execute("ls /")
                
                result.success shouldBe true
                result.exitCode shouldBe 0
                result.stdout shouldContain "bin"
                result.stdout shouldContain "etc"
            }
        }
        
        `when`("executing commands with environment variables") {
            then("should pass environment correctly") {
                val launcher = ContainerCommandLauncher(alpineContainer)
                val result = launcher.execute(
                    "echo \$TEST_VAR", 
                    environment = mapOf("TEST_VAR" to "test_value")
                )
                
                result.success shouldBe true
                result.stdout shouldContain "test_value"
            }
        }
        
        `when`("executing failing commands") {
            then("should handle failures gracefully") {
                val launcher = ContainerCommandLauncher(alpineContainer)
                val result = launcher.execute("exit 1")
                
                result.success shouldBe false
                result.exitCode shouldBe 1
            }
        }
    }
})