package com.opencode.android.core.tool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject
    val category: String
    suspend fun execute(args: JsonObject, workspace: String): String
}

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    fun register(tool: Tool) { tools[tool.name] = tool }
    fun get(name: String): Tool? = tools[name]
    fun all(): List<Tool> = tools.values.toList()
    fun byCategory(category: String): List<Tool> = tools.values.filter { it.category == category }
}

// ─── File Tools ───────────────────────────────────────────────

class ReadFileTool : Tool {
    override val name = "read_file"
    override val description = "Read file contents with line numbers"
    override val category = "file"
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
            val total = lines.size
            val selected = lines.drop(offset).take(limit)
            val numbered = selected.mapIndexed { i, l -> "${offset + i + 1}: $l" }.joinToString("\n")
            "File: $path (${total} lines, showing ${offset + 1}-${minOf(offset + limit, total)})\n$numbered"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class WriteFileTool : Tool {
    override val name = "write_file"
    override val description = "Write content to a file"
    override val category = "file"
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
    override val category = "file"
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
    override val category = "file"
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
    override val category = "file"
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
    override val category = "file"
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
    override val category = "file"
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
    override val description = "Delete a folder and all contents"
    override val category = "file"
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
    override val description = "List folder contents with sizes"
    override val category = "file"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("path") { put("type", "string") } }
        putJsonArray("required") { add("path") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String {
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: workspace
        val dir = File(resolve(path, workspace))
        if (!dir.exists()) return "Error: Path not found: $path"
        if (!dir.isDirectory) return "Error: Not a directory: $path"
        val items = dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name }) ?: emptyList()
        if (items.isEmpty()) return "Empty directory"
        return items.joinToString("\n") { f ->
            val prefix = if (f.isDirectory) "d " else "f "
            val size = if (f.isFile) " (${formatSize(f.length())})" else ""
            "$prefix${f.name}$size"
        }
    }
    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1048576 -> "${bytes / 1024}KB"
        else -> "${bytes / 1048576}MB"
    }
}

// ─── Search Tools ─────────────────────────────────────────────

class GlobFilesTool : Tool {
    override val name = "glob_files"
    override val description = "Find files matching a pattern (e.g. **/*.kt, src/**/*.java)"
    override val category = "search"
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
            if (f.isFile && results.size < 500) {
                val rel = f.relativeTo(rootDir).path
                if (regex.matches(rel) || regex.matches(f.name)) {
                    results.add(f.absolutePath)
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
    override val category = "search"
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
            if (f.isFile && results.size < 200) {
                if (include != null && !f.name.matches(Regex(include.replace("*", ".*")))) return@forEach
                try {
                    f.readLines().forEachIndexed { idx, line ->
                        if (regex.containsMatchIn(line)) {
                            results.add("${f.absolutePath}:${idx + 1}: $line")
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (results.isEmpty()) return "No matches found"
        return "Found ${results.size} matches:\n${results.take(100).joinToString("\n")}"
    }
}

// ─── Shell Tools ──────────────────────────────────────────────

class ShellExecTool : Tool {
    override val name = "shell_exec"
    override val description = "Execute a shell command"
    override val category = "system"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") { put("type", "string") }
            putJsonObject("timeout") { put("type", "integer") }
        }
        putJsonArray("required") { add("command") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String = withContext(Dispatchers.IO) {
        val cmd = args["command"]?.jsonPrimitive?.content ?: return@withContext "Error: command required"
        val timeout = args["timeout"]?.jsonPrimitive?.intOrNull ?: 30
        try {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.directory(File(workspace))
            pb.redirectErrorStream(true)
            val p = pb.start()
            val output = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            when {
                !ok -> { p.destroyForcibly(); "Timeout after ${timeout}s" }
                p.exitValue() == 0 -> output.trimEnd().ifEmpty { "Done" }
                else -> "Exit code ${p.exitValue()}:\n$output"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

// ─── Git Tools ────────────────────────────────────────────────

class GitStatusTool : Tool {
    override val name = "git_status"
    override val description = "Get git status"
    override val category = "git"
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
            p.waitFor(10, TimeUnit.SECONDS)
            out.ifEmpty { "Clean working tree" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class GitDiffTool : Tool {
    override val name = "git_diff"
    override val description = "Show git diff"
    override val category = "git"
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
            p.waitFor(10, TimeUnit.SECONDS)
            out.ifEmpty { "No changes" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

class GitLogTool : Tool {
    override val name = "git_log"
    override val description = "Show recent git commits"
    override val category = "git"
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
            p.waitFor(10, TimeUnit.SECONDS)
            out.ifEmpty { "No commits" }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

// ─── Web Tools ────────────────────────────────────────────────

class WebSearchTool : Tool {
    override val name = "web_search"
    override val description = "Search the web for information"
    override val category = "web"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") { put("type", "string") }
            putJsonObject("num_results") { put("type", "integer") }
        }
        putJsonArray("required") { add("query") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String = withContext(Dispatchers.IO) {
        val query = args["query"]?.jsonPrimitive?.content ?: return@withContext "Error: query required"
        val numResults = args["num_results"]?.jsonPrimitive?.intOrNull ?: 5
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext "Error: Empty response"
            val results = parseSearchResults(html, numResults)
            if (results.isEmpty()) "No results found for: $query"
            else "Search results for: $query\n\n${results.joinToString("\n\n")}"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun parseSearchResults(html: String, maxResults: Int): List<String> {
        val results = mutableListOf<String>()
        val resultPattern = Regex("""class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = Regex("""class="result__snippet"[^>]*>(.*?)</(?:a|td|div|span)""", RegexOption.DOT_MATCHES_ALL)

        val matches = resultPattern.findAll(html).take(maxResults)
        val snippets = snippetPattern.findAll(html).take(maxResults).toList()

        matches.forEachIndexed { index, match ->
            val url = match.groupValues[1]
            val title = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            val snippet = snippets.getOrNull(index)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
            results.add("${index + 1}. $title\n   $url\n   $snippet")
        }
        return results
    }
}

class WebFetchTool : Tool {
    override val name = "web_fetch"
    override val description = "Fetch content from a URL"
    override val category = "web"
    override val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") { put("type", "string") }
            putJsonObject("max_length") { put("type", "integer") }
        }
        putJsonArray("required") { add("url") }
    }
    override suspend fun execute(args: JsonObject, workspace: String): String = withContext(Dispatchers.IO) {
        val url = args["url"]?.jsonPrimitive?.content ?: return@withContext "Error: url required"
        val maxLength = args["max_length"]?.jsonPrimitive?.intOrNull ?: 5000
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val contentType = response.header("Content-Type", "") ?: ""
            val body = response.body?.string() ?: return@withContext "Error: Empty response"

            val text = if (contentType.contains("html")) {
                body.replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } else body

            val truncated = if (text.length > maxLength) text.take(maxLength) + "\n... (truncated)" else text
            "URL: $url\nContent-Type: $contentType\nLength: ${text.length} chars\n\n$truncated"
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
