package dev.rubentxu.hodei.core.dsl.extensions

import dev.rubentxu.hodei.core.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.core.domain.model.Step
import dev.rubentxu.hodei.core.execution.PipelineExecutionException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private class FakeScope {
    var message: String = ""
}

private class FakeStepExtension : StepExtension {
    override val name: String = "fake"

    override fun createScope(): Any = FakeScope()

    override fun execute(scope: Any, stepsBuilder: StepsBuilder) {
        val s = scope as FakeScope
        stepsBuilder.echo(s.message)
    }
}

class StepExtensionRegistrySpec : BehaviorSpec({
    given("a dynamically registered step extension") {
        `when`("invoking it through StepsBuilder.invokeExtension") {
            then("it should execute and add the corresponding step") {
                val builder = StepsBuilder()
                val scope = FakeScope().apply { message = "hello" }

                val ext = FakeStepExtension()
                StepExtensionRegistry.register(ext)
                try {
                    builder.invokeExtension("fake", scope)
                } finally {
                    StepExtensionRegistry.unregister(ext.name)
                }

                val built = builder.build()
                built.size shouldBe 1
                // Ensure an Echo step with expected message exists
                built.shouldContain(Step.echo("hello"))
            }
        }
    }

    given("a missing extension") {
        `when`("invoking an unregistered extension") {
            then("it should throw a clear PipelineExecutionException") {
                val builder = StepsBuilder()
                val scope = FakeScope().apply { message = "ignored" }

                try {
                    builder.invokeExtension("does-not-exist", scope)
                    error("Expected exception was not thrown")
                } catch (e: PipelineExecutionException) {
                    e.message!!.contains("Does not exist", ignoreCase = true) ||
                        e.message!!.contains("not available", ignoreCase = true) shouldBe true
                }
            }
        }
    }
})
