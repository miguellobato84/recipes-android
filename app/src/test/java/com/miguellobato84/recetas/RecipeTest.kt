package com.miguellobato84.recetas

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecipeTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun source(title: String = "Tortitas", category: String = "desayunos", servings: String = "no especificadas") = """
        ---
        version_esquema: 1
        titulo: $title
        categoria: $category
        etiquetas: [avena, vegetariana]
        raciones: $servings
        ---

        # $title

        ## Ingredientes
        - **700 g** de patatas

        ## Utensilios
        - Sartén

        ## Pasos
        1. Usa `fuego bajo`.

        ## Notas
        - [Fuente](https://example.com/receta)
    """.trimIndent()

    private fun archive(vararg files: Pair<String, String>): File {
        val file = temporary.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("recipes-main/")); zip.closeEntry()
            for ((path, text) in files) {
                zip.putNextEntry(ZipEntry("recipes-main/$path"))
                zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        return file
    }

    private fun fails(block: () -> Unit) {
        try { block(); fail("Expected failure") } catch (_: InvalidSnapshot) { }
    }

    @Test fun `parser hides YAML and preserves text servings formatting and links`() {
        for (servings in listOf("2.5", "desconocido", "no especificadas", "02", "'4 personas'")) {
            val recipe = RecipeParser.parse("desayunos/tortitas.md", source(servings = servings))
            assertEquals(servings.trim('\''), recipe.servings)
            assertEquals(listOf("avena", "vegetariana"), recipe.tags)
            assertFalse(recipe.markdown.contains("version_esquema"))
            assertTrue(recipe.markdown.contains("**700 g**"))
            assertTrue(recipe.markdown.contains("`fuego bajo`"))
            assertTrue(recipe.markdown.contains("https://example.com/receta"))
        }
    }

    @Test fun `reject invalid schema metadata title sections and duplicate keys`() {
        val valid = source()
        val invalid = listOf(
            valid.replace("version_esquema: 1", "version_esquema: 2"),
            valid.replace("version_esquema: 1", "version_esquema: '1'"),
            valid.replace("categoria: desayunos", "categoria: dulces"),
            valid.replace("titulo: Tortitas", "titulo: []"),
            valid.replace("etiquetas: [avena, vegetariana]", "etiquetas: avena"),
            valid.replace("raciones: no especificadas", "raciones: []"),
            valid.replace("raciones: no especificadas", "raciones: null"),
            valid.replace("# Tortitas", "# Otra receta"),
            valid.replace("## Utensilios", "## Notas"),
            valid.replace("version_esquema: 1", "version_esquema: 1\nversion_esquema: 1"),
            valid.replace("titulo: Tortitas", "titulo: !!java.net.URL ['https://example.com']"),
            valid.replace("## Pasos", "## Otro"),
            valid.replace("etiquetas: [avena, vegetariana]", "etiquetas: &a [*a]"),
            valid + "\n# Tortitas",
            valid.replace("---\nversion", "version"),
        )
        invalid.forEach { fails { RecipeParser.parse("desayunos/tortitas.md", it) } }
    }

    @Test fun `headings inside code do not change section validation`() {
        val recipe = RecipeParser.parse("desayunos/tortitas.md", source() + "\n\n```\n# Code\n## Otra\n```\n")
        assertTrue(recipe.markdown.contains("# Code"))
    }

    @Test fun `excessively nested Markdown fails before rendering`() {
        fails { RecipeParser.parse("desayunos/tortitas.md", source() + "\n\n" + "> ".repeat(70) + "nested") }
    }

    @Test fun `folders precede recipes and Spanish collation orders titles`() {
        fun recipe(path: String, title: String) = Recipe(path, title, "4", emptyList(), "")
        val recipes = listOf(recipe("principales/carne/z.md", "Z"), recipe("desayunos/t.md", "T"),
            recipe("dulces/z.md", "Zeta"), recipe("dulces/a.md", "Árbol"), recipe("dulces/n.md", "Ñoquis"),
            recipe("dulces/nested/a.md", "A"))
        assertEquals(listOf("desayunos", "dulces", "principales"), folderEntries(recipes, "").map { it.label })
        assertEquals(listOf("nested", "Árbol", "Ñoquis", "Zeta"), folderEntries(recipes, "dulces").map { it.label })
        assertEquals("carne", folderEntries(recipes, "principales").single().label)
        assertEquals("principales/carne", survivingLocation(recipes, "principales/carne/deleted.md"))
        assertEquals("", survivingLocation(recipes, "removed/folder/recipe.md"))
    }

    @Test fun `snapshot imports every candidate and ignores repository assets`() {
        val file = archive("README.md" to "docs", "AGENTS.md" to "rules", "manuales/file.pdf" to "PDF",
            ".github/test.md" to "hidden", "desayunos/README.md" to "docs",
            "desayunos/tortitas.md" to source(), "principales/carne/otra.md" to source("Otra", "principales"))
        assertEquals(2, SnapshotReader.read(file).size)
        fails { SnapshotReader.read(archive("desayunos/invalid.md" to "bad")) }
        fails { SnapshotReader.read(archive("../escape.md" to source())) }
        fails { SnapshotReader.read(archive("desayunos/large.md" to "x".repeat(MAX_RECIPE_BYTES + 1))) }
        val truncated = temporary.newFile().apply { writeBytes(file.readBytes().dropLast(22).toByteArray()) }
        try { SnapshotReader.read(truncated); fail("Expected truncated ZIP rejection") } catch (_: IOException) { }
    }

    @Test fun `first launch and offline restart do not download`() {
        var requests = 0
        val dir = temporary.newFolder()
        val zip = archive("desayunos/t.md" to source())
        val repository = RecipeRepository(dir, { requests++; zip.copyTo(it, overwrite = true) })
        assertTrue(repository.load().isEmpty()); assertEquals(0, requests)
        assertEquals(1, repository.refresh().size); assertEquals(1, requests)
        val restarted = RecipeRepository(dir, { error("Must remain offline") })
        assertEquals("Tortitas", restarted.load().single().title)
        assertEquals(1, requests)
    }

    @Test fun `reject corrupt CRC invalid UTF8 duplicate paths and excess archive entries`() {
        fun binaryArchive(bytes: ByteArray): File {
            val file = temporary.newFile()
            ZipOutputStream(file.outputStream()).use { zip ->
                val entry = ZipEntry("recipes-main/desayunos/a.md").apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
            }
            return file
        }
        val corrupt = binaryArchive(source().toByteArray())
        val bytes = corrupt.readBytes()
        val position = bytes.toString(Charsets.ISO_8859_1).indexOf("version_esquema")
        bytes[position] = 'x'.code.toByte()
        corrupt.writeBytes(bytes)
        fails { SnapshotReader.read(corrupt) }
        try {
            SnapshotReader.read(binaryArchive(byteArrayOf(0xc3.toByte(), 0x28)))
            fail("Expected UTF-8 rejection")
        } catch (_: java.nio.charset.CharacterCodingException) { }
        val duplicate = archive("desayunos/a.md" to source(), "desayunos/b.md" to source())
        duplicate.writeBytes(duplicate.readBytes().toString(Charsets.ISO_8859_1)
            .replace("desayunos/b.md", "desayunos/a.md").toByteArray(Charsets.ISO_8859_1))
        fails { SnapshotReader.read(duplicate) }
        val many = temporary.newFile()
        ZipOutputStream(many.outputStream()).use { zip ->
            repeat(5001) { zip.putNextEntry(ZipEntry("recipes-main/assets/$it.txt")); zip.closeEntry() }
        }
        fails { SnapshotReader.read(many) }
    }

    @Test fun `successful snapshot mirrors additions changes deletions and empty source`() {
        val dir = temporary.newFolder()
        var zip = archive("desayunos/a.md" to source("Antes"), "desayunos/b.md" to source("Borrar"))
        val repo = RecipeRepository(dir, { zip.copyTo(it, overwrite = true) })
        repo.refresh()
        zip = archive("desayunos/a.md" to source("Después"), "dulces/c.md" to source("Nuevo", "dulces"))
        assertEquals(setOf("Después", "Nuevo"), repo.refresh().map { it.title }.toSet())
        assertEquals(setOf("Después", "Nuevo"), repo.load().map { it.title }.toSet())
        zip = archive("README.md" to "Empty collection")
        assertTrue(repo.refresh().isEmpty()); assertTrue(repo.load().isEmpty())
    }

    @Test fun `network invalid recipe and storage failure retain exact old cache`() {
        val dir = temporary.newFolder()
        val old = archive("desayunos/old.md" to source())
        RecipeRepository(dir, { old.copyTo(it, overwrite = true) }).refresh()
        val bytes = File(dir, "recipes.zip").readBytes()
        val invalid = archive("desayunos/new.md" to source("Nuevo"), "dulces/bad.md" to "bad")
        val failing = listOf(
            RecipeRepository(dir, { it.writeText("partial"); throw IOException("Offline") }),
            RecipeRepository(dir, { invalid.copyTo(it, overwrite = true) }),
            RecipeRepository(dir, { old.copyTo(it, overwrite = true) }, { _, _ -> throw IOException("Full disk") }),
        )
        for (repo in failing) {
            try { repo.refresh(); fail("Expected failure") } catch (_: Exception) { }
            assertArrayEquals(bytes, File(dir, "recipes.zip").readBytes())
            assertEquals("Tortitas", repo.load().single().title)
        }
        val empty = RecipeRepository(temporary.newFolder(), { throw IOException("Offline") })
        try { empty.refresh(); fail("Expected failure") } catch (_: IOException) { }
        assertTrue(empty.load().isEmpty())
    }

    @Test fun `interrupted staging never exposes partial cache`() {
        val dir = temporary.newFolder()
        val old = archive("desayunos/old.md" to source())
        RecipeRepository(dir, { old.copyTo(it, overwrite = true) }).refresh()
        File(dir, "recipes.pending").writeText("killed during download")
        assertEquals("Tortitas", RecipeRepository(dir, { error("No network") }).load().single().title)
        assertFalse(File(dir, "recipes.pending").exists())
    }

    @Test fun `readers see old snapshot while refresh stages and only full new snapshot after commit`() {
        val dir = temporary.newFolder()
        val old = archive("desayunos/old.md" to source())
        RecipeRepository(dir, { old.copyTo(it, overwrite = true) }).refresh()
        val next = archive("desayunos/new.md" to source("Nuevo"))
        val staged = CountDownLatch(1); val resume = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val pending = pool.submit<List<Recipe>> {
                RecipeRepository(dir, { dest ->
                    dest.writeText("partial"); staged.countDown()
                    check(resume.await(5, TimeUnit.SECONDS)); next.copyTo(dest, overwrite = true)
                }).refresh()
            }
            assertTrue(staged.await(5, TimeUnit.SECONDS))
            assertEquals("Tortitas", SnapshotReader.read(File(dir, "recipes.zip")).single().title)
            resume.countDown()
            assertEquals("Nuevo", pending.get(5, TimeUnit.SECONDS).single().title)
            assertEquals("Nuevo", RecipeRepository(dir).load().single().title)
        } finally { resume.countDown(); pool.shutdownNow() }
    }

    @Test fun `external links resolve relative paths and block unsafe schemes`() {
        assertEquals("https://example.com/x", externalLink("dulces/a.md", "https://example.com/x").toString())
        assertEquals("https://github.com/miguellobato84/recipes/blob/main/manuales/test.pdf",
            externalLink("dulces/a.md", "../manuales/test.pdf").toString())
        for (link in listOf("javascript:alert(1)", "file:///etc/passwd", "intent://app", "https:///bad", "bad uri")) {
            assertNull(externalLink("dulces/a.md", link))
        }
    }

    @Test fun `verified source fixture imports all 27 recipes including decimal and text servings`() {
        val fixture = temporary.newFile()
        javaClass.getResourceAsStream("/source-494130c.zip")!!.use { input -> fixture.outputStream().use { input.copyTo(it) } }
        val recipes = SnapshotReader.read(fixture)
        assertEquals(27, recipes.size)
        assertTrue(recipes.any { it.path.startsWith("desayunos/") })
        assertEquals("2.5", recipes.first { it.path.endsWith("paella-de-pollo-garbanzos.md") }.servings)
        assertTrue(recipes.any { it.servings == "desconocido" })
    }
}
