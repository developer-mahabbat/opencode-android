package com.opencode.android.core.tool

import kotlinx.serialization.json.*
import java.io.File

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject
    suspend fun execute(args: JsonObject, workspace: String): String
}

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    fun register(tool: Tool) { tools[tool.name] = tool }
    fun get(name: String): Tool? = tools[name]
    fun all(): List<Tool> = tools.values.toList()

    fun buildSchema(): JsonElement {
        val arr = buildJsonArray {
            tools.values.forEach { t ->
                add(buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", t.parameters)
                    }
                })
            }
        }
        return arr
    }
}

class ReadFileTool : Tool {
    override val name = "read_file"
    override val description = "Read the contents of a file"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("offset") { put("type", "integer") }
            putJsonObject("limit") { put("type", "integer") }
        }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val file = File(resolve(path, workspace))
        if (!file.exists()) return "Error: File not found: $path"
        if (file.isDirectory) return "Error: Is a directory: $path"
        val offset = args["offset"]?.jsonPrimitive?.intOrNull ?: 0
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 500
        return try {
            val lines = file.readLines()
            lines.drop(offset).take(limit).mapIndexed { i, l -> "${offset + i + 1}: $l" }.joinToString("\n")
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class WriteFileTool : Tool {
    override val name = "write_file"
    override val description = "Write content to a file, creating it if needed"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("content") { put("type", "string") }
        }
        putJsonArray("required") { add("path"); add("content") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val content = args["content"]?.jsonPrimitive?.content ?: return "Error: content required"
        return try {
            val file = File(resolve(path, workspace))
            file.parentFile?.mkdirs()
            file.writeText(content)
            "OK: Written ${content.length} bytes to $path"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class EditFileTool : Tool {
    override val name = "edit_file"
    override val description = "Edit a file by replacing old text with new text"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("old_text") { put("type", "string") }
            putJsonObject("new_text") { put("type", "string") }
        }
        putJsonArray("required") { add("path"); add("old_text"); add("new_text") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val old = args["old_text"]?.jsonPrimitive?.content ?: return "Error: old_text required"
        val new = args["new_text"]?.jsonPrimitive?.content ?: return "Error: new_text required"
        val file = File(resolve(path, workspace))
        if (!file.exists()) return "Error: File not found: $path"
        return try {
            val content = file.readText()
            if (!content.contains(old)) return "Error: old_text not found in file"
            file.writeText(content.replaceFirst(old, new))
            "OK: File edited: $path"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class CreateFileTool : Tool {
    override val name = "create_file"
    override val description = "Create a new file"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("content") { put("type", "string") }
        }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val content = args["content"]?.jsonPrimitive?.content ?: ""
        val file = File(resolve(path, workspace))
        if (file.exists()) return "Error: File already exists: $path"
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            "OK: Created $path"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class DeleteFileTool : Tool {
    override val name = "delete_file"
    override val description = "Delete a file"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val file = File(resolve(path, workspace))
        if (!file.exists()) return "Error: File not found: $path"
        return try { file.delete(); "OK: Deleted $path" } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class RenameFileTool : Tool {
    override val name = "rename_file"
    override val description = "Rename or move a file"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("old_path") { put("type", "string") }
            putJsonObject("new_path") { put("type", "string") }
        }
        putJsonArray("required") { add("old_path"); add("new_path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val old = args["old_path"]?.jsonPrimitive?.content ?: return "Error: old_path required"
        val new = args["new_path"]?.jsonPrimitive?.content ?: return "Error: new_path required"
        val oldFile = File(resolve(old, workspace))
        val newFile = File(resolve(new, workspace))
        if (!oldFile.exists()) return "Error: File not found: $old"
        return try { oldFile.renameTo(newFile); "OK: Renamed $old -> $new" } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class CreateFolderTool : Tool {
    override val name = "create_folder"
    override val description = "Create a new folder"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val dir = File(resolve(path, workspace))
        if (dir.exists()) return "Error: Folder already exists: $path"
        return try { dir.mkdirs(); "OK: Created folder $path" } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class DeleteFolderTool : Tool {
    override val name = "delete_folder"
    override val description = "Delete a folder and all its contents"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
        val dir = File(resolve(path, workspace))
        if (!dir.exists()) return "Error: Folder not found: $path"
        return try { dir.deleteRecursively(); "OK: Deleted folder $path" } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class ListFolderTool : Tool {
    override val name = "list_folder"
    override val description = "List contents of a folder"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.content ?: workspace
        val dir = File(resolve(path, workspace))
        if (!dir.exists()) return "Error: Path not found: $path"
        if (!dir.isDirectory) return "Error: Not a directory: $path"
        val items = dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name }) ?: emptyList()
        if (items.isEmpty()) return "Empty directory"
        return items.joinToString("\n") { f ->
            val prefix = if (f.isDirectory) "[DIR] " else "     "
            val size = if (f.isFile) " (${f.length()} bytes)" else ""
            "$prefix${f.name}$size"
        }
    }
}

class GlobFilesTool : Tool {
    override val name = "glob_files"
    override val description = "Find files matching a pattern (e.g. **/*.kt, src/**/*.java)"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") { put("type", "string") }
            putJsonObject("root") { put("type", "string") }
        }
        putJsonArray("required") { add("pattern") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val pattern = args["pattern"]?.jsonPrimitive?.content ?: return "Error: pattern required"
        val root = args["root"]?.jsonPrimitive?.contentOrNull ?: workspace
        val rootDir = File(resolve(root, workspace))
        if (!rootDir.exists()) return "Error: Root not found"
        val regex = Regex(globToRegex(pattern))
        val results = mutableListOf<String>()
        rootDir.walkTopDown().maxDepth(15).forEach { f ->
            if (f.isFile) {
                val rel = f.relativeTo(rootDir).path
                if (regex.matches(rel) || regex.matches(f.name)) {
                    results.add(f.absolutePath)
                    if (results.size >= 500) return@forEach
                }
            }
        }
        if (results.isEmpty()) return "No files found"
        return "Found ${results.size} files:\n${results.take(200).joinToString("\n")}"
    }

    private fun globToRegex(glob: String): String {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (glob[i]) {
                '*' -> { sb.append(".*"); if (i + 1 < glob.length && glob[i + 1] == '*') { sb.append(".*"); i++ } }
                '?' -> sb.append("[^/]")
                '.' -> sb.append("\\.")
                '/' -> sb.append("(?:\\\\|/)")
                '{' -> { sb.append("(?:"); i++; while (i < glob.length && glob[i] != '}') { sb.append(glob[i]); i++ }; sb.append(")") }
                else -> sb.append(glob[i])
            }
            i++
        }
        sb.append("$")
        return sb.toString()
    }
}

class GrepSearchTool : Tool {
    override val name = "grep_search"
    override val description = "Search file contents using regex"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") { put("type", "string") }
            putJsonObject("path") { put("type", "string") }
            putJsonObject("include") { put("type", "string") }
        }
        putJsonArray("required") { add("pattern") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val pattern = args["pattern"]?.jsonPrimitive?.content ?: return "Error: pattern required"
        val searchPath = args["path"]?.jsonPrimitive?.contentOrNull ?: workspace
        val include = args["include"]?.jsonPrimitive?.contentOrNull
        val dir = File(resolve(searchPath, workspace))
        if (!dir.exists()) return "Error: Path not found"
        val regex = Regex(pattern)
        val results = mutableListOf<String>()
        dir.walkTopDown().maxDepth(10).forEach { f ->
            if (f.isFile) {
                if (include != null && !f.name.matches(Regex(include.replace("*", ".*")))) return@forEach
                try {
                    f.readLines().forEachIndexed { idx, line ->
                        if (regex.containsMatchIn(line)) {
                            results.add("${f.absolutePath}:${idx + 1}: $line")
                            if (results.size >= 200) return@forEach
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (results.isEmpty()) return "No matches found"
        return "Found ${results.size} matches:\n${results.take(100).joinToString("\n")}"
    }
}

class ShellExecTool : Tool {
    override val name = "shell_exec"
    override val description = "Execute a shell command"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") { put("type", "string") }
            putJsonObject("timeout") { put("type", "integer") }
        }
        putJsonArray("required") { add("command") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val cmd = args["command"]?.jsonPrimitive?.content ?: return "Error: command required"
        val timeout = args["timeout"]?.jsonPrimitive?.intOrNull ?: 30
        return try {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.directory(File(workspace))
            pb.redirectErrorStream(true)
            val p = pb.start()
            val output = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!ok) { p.destroyForcibly(); "Timeout after ${timeout}s" }
            else if (p.exitValue() == 0) output.ifEmpty { "Done" }
            else "Exit code ${p.exitValue()}:\n$output"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class GitStatusTool : Tool {
    override val name = "git_status"
    override val description = "Get git status"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: workspace
        return try {
            val p = ProcessBuilder("git", "status", "--porcelain").directory(File(path)).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            out.ifEmpty { "Clean working tree" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class GitDiffTool : Tool {
    override val name = "git_diff"
    override val description = "Show git diff"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: workspace
        return try {
            val p = ProcessBuilder("git", "diff").directory(File(path)).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            out.ifEmpty { "No changes" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class GitLogTool : Tool {
    override val name = "git_log"
    override val description = "Show recent git commits"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") { put("type", "string") }
            putJsonObject("count") { put("type", "integer") }
        }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: workspace
        val count = args["count"]?.jsonPrimitive?.intOrNull ?: 10
        return try {
            val p = ProcessBuilder("git", "log", "--oneline", "-n", count.toString()).directory(File(path)).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            out.ifEmpty { "No commits" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

private fun resolve(path: String, workspace: String): String {
    return when {
        path.startsWith("/") -> path
        path.startsWith("~") -> "/data/data/com.opencode.android${path.removePrefix("~")}"
        else -> "$workspace/$path"
    }
}
