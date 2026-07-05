package com.opencode.android.core.diff

import java.io.File

data class DiffHunk(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int, val lines: List<String>)
data class DiffResult(val file: String, val hunks: List<DiffHunk>, val additions: Int, val deletions: Int)

class DiffEngine {
    fun diff(oldContent: String, newContent: String, fileName: String = ""): DiffResult {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        val lcs = lcs(oldLines, newLines)
        val hunks = buildHunks(oldLines, newLines, lcs)
        val adds = hunks.sumOf { h -> h.lines.count { it.startsWith("+") } }
        val dels = hunks.sumOf { h -> h.lines.count { it.startsWith("-") } }
        return DiffResult(fileName, hunks, adds, dels)
    }

    fun toUnifiedDiff(diff: DiffResult): String {
        val sb = StringBuilder()
        sb.appendLine("--- a/${diff.file}")
        sb.appendLine("+++ b/${diff.file}")
        diff.hunks.forEach { h ->
            sb.appendLine("@@ -${h.oldStart},${h.oldCount} +${h.newStart},${h.newCount} @@")
            h.lines.forEach { sb.appendLine(it) }
        }
        return sb.toString()
    }

    fun applyPatch(original: String, patch: String): Result<String> {
        return try {
            val lines = original.lines().toMutableList()
            var cursor = 0
            patch.lines().forEach { line ->
                when {
                    line.startsWith("@@") -> {
                        val m = Regex("""@@ -(\d+),?\d* \+(\d+),?\d* @@""").find(line)
                        if (m != null) cursor = m.groupValues[1].toIntOrNull()?.minus(1) ?: cursor
                    }
                    line.startsWith("+") -> { lines.add(cursor, line.substring(1)); cursor++ }
                    line.startsWith("-") -> { if (cursor < lines.size) lines.removeAt(cursor) }
                    line.startsWith(" ") -> cursor++
                }
            }
            Result.success(lines.joinToString("\n"))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun buildHunks(old: List<String>, new: List<String>, lcs: List<String>): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        var oi = 0; var ni = 0; var li = 0
        val buf = mutableListOf<String>(); var os = 0; var ns = 0; var oc = 0; var nc = 0; var dirty = false

        while (oi < old.size || ni < new.size) {
            if (li < lcs.size && oi < old.size && ni < new.size && old[oi] == lcs[li] && new[ni] == lcs[li]) {
                if (dirty) { hunks.add(DiffHunk(os + 1, oc, ns + 1, nc, buf.toList())); buf.clear(); dirty = false }
                oi++; ni++; li++
            } else {
                if (!dirty) { os = oi; ns = ni }
                if (ni >= new.size || (oi < old.size && li < lcs.size && old[oi] != lcs[li])) {
                    buf.add("-${old[oi]}"); oc++; oi++
                } else {
                    buf.add("+${new[ni]}"); nc++; ni++
                }
                dirty = true
            }
        }
        if (dirty) hunks.add(DiffHunk(os + 1, oc, ns + 1, nc, buf.toList()))
        return hunks
    }

    private fun lcs(a: List<String>, b: List<String>): List<String> {
        val m = a.size; val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n) dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1] + 1 else maxOf(dp[i-1][j], dp[i][j-1])
        val r = mutableListOf<String>(); var i = m; var j = n
        while (i > 0 && j > 0) {
            if (a[i-1] == b[j-1]) { r.add(a[i-1]); i--; j-- }
            else if (dp[i-1][j] > dp[i][j-1]) i-- else j--
        }
        return r.reversed()
    }
}
