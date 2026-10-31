package com.miguellobato84.recetas

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecipeNotesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file() = File(temporary.newFolder(), "recipe-notes.json")

    @Test fun `plain text round trips offline and stays isolated by source path`() = runBlocking {
        val file = file()
        val controller = RecipeNotesController(RecipeNotesStore(file))
        controller.load()
        val text = "  Menos sal 🥘\n\nAñadir limón. **texto sin formato**\t\n"
        controller.edit("a/receta.md", text)
        controller.edit("b/receta.md", "Otro texto")
        assertEquals(text, controller.state.notes["a/receta.md"])
        assertTrue(controller.state.dirty)
        controller.savePending()
        val reopened = RecipeNotesController(RecipeNotesStore(file))
        reopened.load()
        assertEquals(mapOf("a/receta.md" to text, "b/receta.md" to "Otro texto"), reopened.state.notes)
        assertFalse(reopened.state.dirty)
        assertNull(reopened.state.error)
    }

    @Test fun `clearing text removes only its recipe and whitespace remains valid`() = runBlocking {
        val file = file()
        val controller = RecipeNotesController(RecipeNotesStore(file))
        controller.load()
        controller.edit("a.md", "Una nota")
        controller.edit("b.md", " \n ")
        controller.savePending()
        controller.edit("a.md", "")
        controller.savePending()
        assertEquals(mapOf("b.md" to " \n "), RecipeNotesStore(file).load())
        controller.edit("b.md", "")
        controller.savePending()
        assertTrue(RecipeNotesStore(file).load().isEmpty())
    }

    @Test fun `recipe refresh and shopping clear cannot rewrite notes`() = runBlocking {
        val directory = temporary.newFolder()
        val notesFile = File(directory, "recipe-notes.json")
        val notes = RecipeNotesController(RecipeNotesStore(notesFile))
        notes.load()
        notes.edit("principales/lentejas.md", "Menos sal")
        notes.savePending()
        val before = notesFile.readBytes()
        val snapshot = temporary.newFile()
        javaClass.getResourceAsStream("/source-494130c.zip")!!.use { input ->
            snapshot.outputStream().use { input.copyTo(it) }
        }
        val repository = RecipeRepository(File(directory, "recipe-cache"), downloader = { target ->
            snapshot.copyTo(target, overwrite = true)
        })
        repository.refresh()
        ShoppingListStore(File(directory, "shopping-list.bin")).save(emptyList())
        // Android cache clearing targets cacheDir, not the sibling private notes file.
        val cache = File(directory, "cache").apply { mkdirs() }
        File(cache, "temporary").writeText("temporary")
        cache.deleteRecursively()
        assertArrayEquals(before, notesFile.readBytes())
        assertEquals("Menos sal", RecipeNotesStore(notesFile).load()["principales/lentejas.md"])
        assertNull(notes.state.notes["otra/ruta.md"])
    }

    @Test fun `failed save retains disk and latest draft then retry succeeds`() = runBlocking {
        val file = file()
        RecipeNotesStore(file).save(mapOf("a.md" to "Original"))
        val original = file.readBytes()
        var fail = true
        val controller = RecipeNotesController(RecipeNotesStore(file) { staged, active ->
            if (fail) throw IOException("Disk failure")
            Files.move(staged.toPath(), active.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        })
        controller.load()
        controller.edit("a.md", "Borrador")
        controller.savePending()
        assertArrayEquals(original, file.readBytes())
        assertEquals("Borrador", controller.state.notes["a.md"])
        assertNotNull(controller.state.error)
        assertTrue(controller.state.dirty)
        assertFalse(controller.state.saving)
        fail = false
        controller.savePending()
        assertEquals("Borrador", RecipeNotesStore(file).load()["a.md"])
        assertFalse(controller.state.dirty)
        assertNull(controller.state.error)
    }

    @Test fun `edits during pending IO keep visible draft and persist latest snapshot`() = runBlocking {
        val file = file()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var commits = 0
        val controller = RecipeNotesController(RecipeNotesStore(file) { staged, active ->
            if (commits++ == 0) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            Files.move(staged.toPath(), active.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        })
        controller.load()
        controller.edit("a.md", "Primero")
        val save = launch { controller.savePending() }
        try {
            yield()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            controller.edit("a.md", "Segundo")
            controller.edit("a.md", "Último")
            controller.edit("b.md", "Otra receta")
            assertEquals("Último", controller.state.notes["a.md"])
        } finally {
            release.countDown()
        }
        save.join()
        assertEquals(mapOf("a.md" to "Último", "b.md" to "Otra receta"), RecipeNotesStore(file).load())
        assertEquals(2, commits)
        assertFalse(controller.state.dirty)
        assertFalse(controller.state.saving)
    }

    @Test fun `malformed load blocks edits and retry preserves file until it is readable`() = runBlocking {
        val file = file().apply { writeText("{broken") }
        val controller = RecipeNotesController(RecipeNotesStore(file))
        controller.edit("a.md", "Before load")
        assertTrue(controller.state.notes.isEmpty())
        controller.load()
        assertTrue(controller.state.loadFailed)
        controller.edit("a.md", "Must not overwrite")
        controller.savePending()
        assertEquals("{broken", file.readText())
        assertTrue(controller.state.notes.isEmpty())
        RecipeNotesStore(file).save(mapOf("a.md" to "Recovered"))
        controller.load()
        assertFalse(controller.state.loadFailed)
        assertEquals("Recovered", controller.state.notes["a.md"])
    }

    @Test fun `interrupted stage never replaces committed notes on reopen`() {
        val file = file()
        val store = RecipeNotesStore(file)
        store.save(mapOf("a.md" to "Committed"))
        val pending = File(file.parentFile, "${file.name}.pending")
        pending.writeText("partial write")
        assertEquals(mapOf("a.md" to "Committed"), store.load())
        store.save(mapOf("a.md" to "Next"))
        assertFalse(pending.exists())
        assertEquals(mapOf("a.md" to "Next"), store.load())
    }

    @Test fun `unsupported format and invalid entries are rejected`() {
        val file = file()
        listOf("{\"version\":2,\"notes\":{}}", "{\"version\":1,\"notes\":{\"a\":42}}",
            "{\"version\":1,\"notes\":{\"a\":\"\"}}", "{\"version\":1,\"notes\":{\"\":\"text\"}}")
            .forEach { invalid ->
                file.writeText(invalid)
                assertThrows(Exception::class.java) { RecipeNotesStore(file).load() }
                assertEquals(invalid, file.readText())
            }
    }
}
