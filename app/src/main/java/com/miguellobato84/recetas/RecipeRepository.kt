package com.miguellobato84.recetas

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.CRC32
import java.util.zip.ZipFile

private const val MIB = 1024 * 1024
internal const val MAX_ZIP_BYTES = 32 * MIB
internal const val MAX_RECIPE_BYTES = 256 * 1024

object SnapshotReader {
    fun read(file: File): List<Recipe> {
        if (file.length() > MAX_ZIP_BYTES) throw InvalidSnapshot("La descarga supera el tamaño permitido.")
        ZipFile(file).use { zip ->
            if (zip.size() == 0 || zip.size() > 5000) throw InvalidSnapshot("Archivo de recetas no válido.")
            val entries = zip.entries().asSequence()
            val paths = mutableSetOf<String>()
            var root: String? = null
            var declaredSize = 0L
            var recipeBytes = 0
            val recipes = mutableListOf<Recipe>()
            for (entry in entries) {
                val parts = entry.name.trimEnd('/').split('/')
                if (parts.any { it.isEmpty() || it == "." || it == ".." || '\\' in it } ||
                    !paths.add(entry.name.trimEnd('/'))) throw InvalidSnapshot("Ruta de archivo no válida.")
                if (root == null) root = parts.first()
                if (root != parts.first()) throw InvalidSnapshot("El archivo mezcla varias carpetas raíz.")
                if (entry.size < 0) throw InvalidSnapshot("Tamaño de archivo desconocido.")
                declaredSize += entry.size
                if (declaredSize > 64L * MIB) throw InvalidSnapshot("El archivo supera el tamaño permitido.")
                if (entry.isDirectory) continue
                val path = parts.drop(1).joinToString("/")
                if (parts.size < 3 || parts.drop(1).any { it.startsWith('.') } ||
                    !path.endsWith(".md", ignoreCase = true) ||
                    parts.last().lowercase() in setOf("readme.md", "agents.md")) continue
                val bytes = zip.getInputStream(entry).use { readBounded(it, MAX_RECIPE_BYTES) }
                recipeBytes += bytes.size
                if (recipeBytes > 8 * MIB) throw InvalidSnapshot("Hay demasiadas recetas.")
                if (entry.size != bytes.size.toLong() || CRC32().apply { update(bytes) }.value != entry.crc) {
                    throw InvalidSnapshot("La descarga está dañada: $path")
                }
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                recipes += RecipeParser.parse(path, text)
            }
            return recipes.toList()
        }
    }
}

internal fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        if (output.size() + count > limit) throw InvalidSnapshot("La receta supera el tamaño permitido.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

fun interface SnapshotDownloader { fun download(destination: File) }

class GithubSnapshotDownloader : SnapshotDownloader {
    override fun download(destination: File) {
        val connection = URL("https://codeload.github.com/miguellobato84/recipes/zip/refs/heads/main")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Recetas-Android")
        connection.setRequestProperty("Accept", "application/zip")
        val started = System.nanoTime()
        try {
            if (connection.responseCode != 200) throw java.io.IOException("HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > MAX_ZIP_BYTES) throw InvalidSnapshot("La descarga es demasiado grande.")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (System.nanoTime() - started > 90_000_000_000L) throw java.io.IOException("Timeout")
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > MAX_ZIP_BYTES) throw InvalidSnapshot("La descarga es demasiado grande.")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (connection.contentLengthLong >= 0 && total != connection.contentLengthLong) {
                throw java.io.IOException("Incomplete download")
            }
        } finally { connection.disconnect() }
    }
}

/** Only this class owns cache paths. No downloaded archive entry is extracted. */
class RecipeRepository(
    private val directory: File,
    private val downloader: SnapshotDownloader = GithubSnapshotDownloader(),
    private val commit: (File, File) -> Unit = { staged, active ->
        Files.move(staged.toPath(), active.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    },
) {
    private val active get() = File(directory, "recipes.zip")
    private val staged get() = File(directory, "recipes.pending")

    @Synchronized fun load(): List<Recipe> {
        Files.deleteIfExists(staged.toPath())
        return if (active.exists()) SnapshotReader.read(active) else emptyList()
    }

    @Synchronized fun refresh(): List<Recipe> {
        if (!directory.isDirectory && !directory.mkdirs()) throw java.io.IOException("Cannot create cache")
        try {
            downloader.download(staged)
            val recipes = SnapshotReader.read(staged)
            FileOutputStream(staged, true).use { it.fd.sync() }
            commit(staged, active)
            // No failing work after commit: callers can now publish the complete new list.
            return recipes
        } finally {
            // Cleanup must never turn a committed refresh into a reported failure.
            staged.delete()
        }
    }
}
