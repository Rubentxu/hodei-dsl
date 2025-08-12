package dev.rubentxu.hodei.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Hodei Pipeline DSL – CLI entry point
 */
internal class HodeiCli : CliktCommand(name = "hodei", help = "Hodei Pipeline DSL CLI") {
    override fun run() {
        // No-op: uses subcommands
    }
}

/**
 * Shows the saved session context for the Compiler & Runtime system.
 * By default it reads docs/session-context-compiler-runtime.md from the repository.
 */
internal class SessionContextCommand : CliktCommand(
    name = "context",
    help = "Muestra el contexto de sesión previo (Compiler & Runtime)"
) {
    private val file: Path by option(
        "--path",
        help = "Ruta al archivo de contexto (por defecto: docs/session-context-compiler-runtime.md)"
    ).path().default(Paths.get("docs/session-context-compiler-runtime.md"))

    override fun run() {
        val resolved: Path = resolvePath(file)
        if (!Files.exists(resolved)) {
            echo(
                "No se encontró el archivo de contexto en: ${resolved.toAbsolutePath()}\n" +
                        "Puedes especificar una ruta con --path."
            )
            throw ProgramResult(1)
        }
        val content = Files.readString(resolved)
        echo(content)
    }

    private fun resolvePath(candidate: Path): Path {
        // Allow overriding the repo root via env var for flexibility when running from other dirs
        val envRoot = System.getenv("HODEI_REPO_ROOT")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        return when {
            candidate.isAbsolute -> candidate
            envRoot != null -> envRoot.resolve(candidate)
            else -> Paths.get("").toAbsolutePath().resolve(candidate)
        }
    }
}

fun main(args: Array<String>) {
    HodeiCli()
        .subcommands(SessionContextCommand())
        .main(args)
}
