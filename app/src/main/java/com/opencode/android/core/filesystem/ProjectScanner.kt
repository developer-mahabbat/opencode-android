package com.opencode.android.core.filesystem

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val children: List<FileNode> = emptyList()
)

data class ProjectInfo(
    val rootPath: String,
    val name: String,
    val totalFiles: Int,
    val totalDirs: Int,
    val languages: Map<String, Int>,
    val structure: List<FileNode>
)

class ProjectScanner {
    private val ignoredDirs = setOf(
        ".git", "node_modules", ".gradle", "build", "dist",
        ".idea", ".vscode", "__pycache__", ".cache", "target"
    )

    fun scan(rootPath: String): ProjectInfo {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) {
            return ProjectInfo(rootPath, root.name, 0, 0, emptyMap(), emptyList())
        }

        var fileCount = 0
        var dirCount = 0
        val languages = mutableMapOf<String, Int>()

        root.walkTopDown().maxDepth(15).forEach { f ->
            if (f.isDirectory && f.name !in ignoredDirs) dirCount++
            if (f.isFile && shouldInclude(f)) {
                fileCount++
                val lang = detectLanguage(f.name)
                languages[lang] = (languages[lang] ?: 0) + 1
            }
        }

        val structure = buildTree(root, 0, 3)

        return ProjectInfo(rootPath, root.name, fileCount, dirCount, languages, structure)
    }

    fun readFile(path: String): String? {
        return try { File(path).readText() } catch (_: Exception) { null }
    }

    fun writeFile(path: String, content: String): Boolean {
        return try {
            val f = File(path)
            f.parentFile?.mkdirs()
            f.writeText(content)
            true
        } catch (_: Exception) { false }
    }

    fun searchFiles(root: String, pattern: String, maxResults: Int = 100): List<String> {
        val results = mutableListOf<String>()
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        File(root).walkTopDown().maxDepth(10).forEach { f ->
            if (f.isFile && shouldInclude(f)) {
                try {
                    f.readLines().forEachIndexed { idx, line ->
                        if (regex.containsMatchIn(line)) {
                            results.add("${f.absolutePath}:${idx + 1}: $line")
                            if (results.size >= maxResults) return@forEach
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return results
    }

    fun findFiles(root: String, pattern: String, maxResults: Int = 100): List<String> {
        val results = mutableListOf<String>()
        val regex = Regex(globToRegex(pattern))
        File(root).walkTopDown().maxDepth(10).forEach { f ->
            if (f.isFile && shouldInclude(f)) {
                val rel = f.relativeTo(File(root)).path
                if (regex.matches(rel) || regex.matches(f.name)) {
                    results.add(f.absolutePath)
                    if (results.size >= maxResults) return@forEach
                }
            }
        }
        return results
    }

    private fun buildTree(dir: File, depth: Int, maxDepth: Int): List<FileNode> {
        if (depth >= maxDepth) return emptyList()
        return dir.listFiles()
            ?.filter { it.name !in ignoredDirs && !it.name.startsWith(".") }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
            ?.take(100)
            ?.map { f ->
                FileNode(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    size = if (f.isFile) f.length() else 0,
                    children = if (f.isDirectory) buildTree(f, depth + 1, maxDepth) else emptyList()
                )
            } ?: emptyList()
    }

    private fun shouldInclude(f: File): Boolean {
        val skip = f.name.startsWith(".") || f.name.endsWith(".class") ||
                f.name.endsWith(".jar") || f.name.endsWith(".png") ||
                f.name.endsWith(".jpg") || f.name.endsWith(".mp4")
        return !skip
    }

    private fun detectLanguage(name: String): String {
        return when {
            name.endsWith(".kt") || name.endsWith(".kts") -> "Kotlin"
            name.endsWith(".java") -> "Java"
            name.endsWith(".py") -> "Python"
            name.endsWith(".js") || name.endsWith(".mjs") -> "JavaScript"
            name.endsWith(".ts") -> "TypeScript"
            name.endsWith(".tsx") || name.endsWith(".jsx") -> "React"
            name.endsWith(".go") -> "Go"
            name.endsWith(".rs") -> "Rust"
            name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h") -> "C/C++"
            name.endsWith(".cs") -> "C#"
            name.endsWith(".swift") -> "Swift"
            name.endsWith(".dart") -> "Dart"
            name.endsWith(".rb") -> "Ruby"
            name.endsWith(".php") -> "PHP"
            name.endsWith(".html") || name.endsWith(".htm") -> "HTML"
            name.endsWith(".css") || name.endsWith(".scss") -> "CSS"
            name.endsWith(".json") -> "JSON"
            name.endsWith(".xml") -> "XML"
            name.endsWith(".yaml") || name.endsWith(".yml") -> "YAML"
            name.endsWith(".md") -> "Markdown"
            name.endsWith(".sh") || name.endsWith(".bash") -> "Shell"
            name.endsWith(".sql") -> "SQL"
            else -> "Other"
        }
    }

    private fun globToRegex(glob: String): String {
        return "^" + glob.replace(".", "\\.").replace("**", ".*").replace("*", "[^/]*").replace("?", ".") + "$"
    }
}
