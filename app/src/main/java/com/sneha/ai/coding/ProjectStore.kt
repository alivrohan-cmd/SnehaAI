package com.sneha.ai.coding

import android.content.Context
import java.io.File

class ProjectStore(ctx: Context) {
    val root = File(ctx.filesDir, "projects").apply { mkdirs() }

    private fun safe(s: String): String {
        val t = s.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('.').take(60)
        return t.ifBlank { "untitled" }
    }

    fun save(project: String, file: String, content: String): File {
        val d = File(root, safe(project)).apply { mkdirs() }
        return File(d, safe(file)).also { it.writeText(content) }
    }

    fun list(): List<String> = root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
    fun files(project: String): List<File> = File(root, safe(project)).listFiles()?.toList() ?: emptyList()
    fun delete(project: String) { File(root, safe(project)).deleteRecursively() }
}
