package com.example.myapplication.ui.viewer
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File

class IdeWorkspaceTools(
    private val workspaceDir: File,
    private val onFileModified: (File) -> Unit
) : ToolSet {

    private fun resolveSafe(relativePath: String): File {
        val target = File(workspaceDir, relativePath).canonicalFile
        require(target.path.startsWith(workspaceDir.canonicalPath)) {
            "Access denied: path escapes project working directory"
        }
        return target
    }

    @Tool
    @LLMDescription("List files and folders inside the project working directory")
    fun listFiles(
        @LLMDescription("Relative subfolder path, or empty string for project root")
        subPath: String = ""
    ): String {
        val dir = resolveSafe(subPath)
        return dir.listFiles()
            ?.joinToString("\n") { if (it.isDirectory) "${it.name}/" else it.name }
            ?: "Directory not found"
    }

    @Tool
    @LLMDescription("Read the text contents of a file in the project")
    fun readFile(
        @LLMDescription("Relative path from project root, e.g. src/main/Main.kt")
        path: String
    ): String {
        val file = resolveSafe(path)
        return if (file.exists() && file.isFile) file.readText() else "File not found: $path"
    }

    @Tool
    @LLMDescription("Write or overwrite a file in the project working directory")
    fun writeFile(
        @LLMDescription("Relative path from project root")
        path: String,
        @LLMDescription("Complete file content to write")
        content: String
    ): String {
        val file = resolveSafe(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        onFileModified(file)
        return "Saved ${file.relativeTo(workspaceDir).path}"
    }
}