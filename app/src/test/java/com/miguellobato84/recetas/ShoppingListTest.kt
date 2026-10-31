package com.miguellobato84.recetas

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShoppingListTest {
    @get:Rule val temporary = TemporaryFolder()

    private val ingredients = """
        - **200 g** de lentejas
        - 1 cebolla
        - Sal al gusto (cantidad no especificada)

        ### Opcional
        - Un poco de aceite; [nota](https://example.com)
    """.trimIndent()

    private fun recipe(path: String = "principales/lentejas.md", title: String = "Lentejas estofadas",
        items: String = ingredients) = Recipe(path, title, "4", emptyList(),
        "# $title\n\n## Ingredientes\n$items\n\n## Utensilios\n- Olla\n\n## Pasos\n1. Cocinar.\n\n## Notas\nTexto.")

    private fun storeFile() = File(temporary.newFolder(), "shopping-list.bin")

    @Test fun `complete ingredients and uncertainty preserved without other sections`() {
        assertEquals(ingredients, ingredientSectionMarkdown(recipe().markdown))
        val code = "- Ingrediente\n\n```markdown\n## Utensilios\n```\n\n    ## Utensilios\n\n- Otro ingrediente"
        assertEquals(code, ingredientSectionMarkdown(recipe(items = code).markdown))
        val misleading = "# Título\n\n```\n## Ingredientes\n- Falso\n```\n\n"
        assertEquals(ingredients, ingredientSectionMarkdown(misleading + recipe().markdown))
        // F1 accepts both ATX and setext headings, with optional closing hashes.
        val setext = recipe().markdown.replace("## Ingredientes", "Ingredientes\n---")
            .replace("## Utensilios", "Utensilios\n---")
        assertEquals(ingredients, ingredientSectionMarkdown(setext))
        assertEquals(ingredients, ingredientSectionMarkdown(recipe().markdown
            .replace("## Ingredientes", "  ## Ingredientes ##")
            .replace("## Utensilios", " ## Utensilios ###")))
    }

    @Test fun `all 27 source fixture ingredient sections survive additions and reopen`() = runBlocking {
        val fixture = temporary.newFile()
        javaClass.getResourceAsStream("/source-494130c.zip")!!.use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val recipes = SnapshotReader.read(fixture)
        assertEquals(27, recipes.size)
        val file = storeFile()
        val controller = ShoppingListController(ShoppingListStore(file))
        controller.load()
        recipes.forEach { recipe ->
            // Independent exact source slice for this pinned fixture's ATX format.
            val expected = recipe.markdown.substringAfter("## Ingredientes\n")
                .substringBefore("## Utensilios").trim('\r', '\n')
            assertEquals(recipe.path, expected, ingredientSectionMarkdown(recipe.markdown))
            controller.add(recipe)
            controller.add(recipe)
        }
        assertFalse(controller.state.error)
        val saved = ShoppingListStore(file).load()
        assertEquals(54, saved.size)
        val groups = shoppingRecipeGroups(saved)
        assertEquals(27, groups.size)
        assertTrue(groups.all { it.count == 2 && it.additions[0] == it.additions[1] })
    }

    @Test fun `repeated additions count additions not servings and retain every row`() = runBlocking {
        val file = storeFile()
        val controller = ShoppingListController(ShoppingListStore(file))
        controller.load()
        controller.add(recipe())
        controller.add(recipe())
        controller.add(recipe("principales/otra.md", "Otra"))
        val groups = shoppingRecipeGroups(controller.state.additions)
        assertEquals(listOf(2, 1), groups.map { it.count })
        assertEquals(listOf(ingredients, ingredients), groups.first().additions.map { it.ingredientsMarkdown })
        assertEquals(3, ShoppingListStore(file).load().size)
        assertFalse(controller.state.error)
    }

    @Test fun `equal titles at different paths never merge`() = runBlocking {
        val controller = ShoppingListController(ShoppingListStore(storeFile()))
        controller.load()
        controller.add(recipe("a/receta.md"))
        controller.add(recipe("b/receta.md"))
        val groups = shoppingRecipeGroups(controller.state.additions)
        assertEquals(listOf("a/receta.md", "b/receta.md"), groups.map { it.sourcePath })
        assertEquals(listOf(1, 1), groups.map { it.count })
    }

    @Test fun `offline reopen retains snapshots after recipe edits deletion and path rename`() = runBlocking {
        val file = storeFile()
        val controller = ShoppingListController(ShoppingListStore(file))
        controller.load()
        controller.add(recipe())
        controller.add(recipe(title = "Nuevo título", items = "- Ingrediente nuevo"))
        controller.add(recipe(path = "otra/ruta.md"))
        // Reopen without a recipe repository: removed source recipes cannot remove snapshots.
        val reopened = ShoppingListController(ShoppingListStore(file))
        reopened.load()
        val groups = shoppingRecipeGroups(reopened.state.additions)
        assertEquals(listOf(2, 1), groups.map { it.count })
        assertEquals("Nuevo título", groups.first().title)
        assertEquals(listOf(ingredients, "- Ingrediente nuevo"), groups.first().additions.map { it.ingredientsMarkdown })
        assertEquals("Lentejas estofadas", groups.first().additions.first().title)
        assertEquals(controller.state.additions, reopened.state.additions)
    }

    @Test fun `clear removes ingredients and counts persists empty and permits starting again`() = runBlocking {
        val file = storeFile()
        val controller = ShoppingListController(ShoppingListStore(file))
        controller.load()
        controller.add(recipe())
        controller.add(recipe())
        controller.clear()
        assertTrue(controller.state.additions.isEmpty())
        assertTrue(shoppingRecipeGroups(controller.state.additions).isEmpty())
        val reopened = ShoppingListController(ShoppingListStore(file))
        reopened.load()
        assertTrue(reopened.state.additions.isEmpty())
        reopened.clear()
        reopened.add(recipe())
        assertEquals(1, shoppingRecipeGroups(reopened.state.additions).single().count)
    }

    @Test fun `failed add or clear preserves complete memory and byte identical stored list`() = runBlocking {
        val file = storeFile()
        ShoppingListStore(file).save(listOf(IngredientAddition("a.md", "A", ingredients)))
        val before = file.readBytes()
        val controller = ShoppingListController(ShoppingListStore(file) { _, _ -> throw IOException("full") })
        controller.load()
        val saved = controller.state.additions
        controller.add(recipe())
        assertTrue(controller.state.error)
        assertEquals(saved, controller.state.additions)
        assertArrayEquals(before, file.readBytes())
        controller.clear()
        assertTrue(controller.state.error)
        assertEquals(saved, controller.state.additions)
        assertArrayEquals(before, file.readBytes())
        assertFalse(File(file.parentFile, "${file.name}.pending").exists())
    }

    @Test fun `tap during save queues a second full addition and clear follows queued adds`() = runBlocking {
        val file = storeFile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var replacements = 0
        val store = ShoppingListStore(file) { staged, active ->
            if (replacements++ == 0) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            java.nio.file.Files.move(staged.toPath(), active.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val controller = ShoppingListController(store)
        controller.load()
        val first = async { controller.add(recipe()) }
        yield()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(controller.state.saving)
            val second = async { controller.add(recipe()) }
            yield()
            release.countDown()
            first.await()
            second.await()
            assertEquals(2, shoppingRecipeGroups(controller.state.additions).single().count)
            val third = launch { controller.add(recipe()) }
            val clear = launch { controller.clear() }
            third.join()
            clear.join()
            assertTrue(store.load().isEmpty())
        } finally { release.countDown() }
    }

    @Test fun `interrupted uncommitted stage is ignored while corrupt active data blocks mutations`() = runBlocking {
        val file = storeFile()
        val addition = IngredientAddition("a.md", "A", ingredients)
        ShoppingListStore(file).save(listOf(addition))
        File(file.parentFile, "${file.name}.pending").writeText("partial")
        assertEquals(listOf(addition), ShoppingListStore(file).load())
        file.writeText("damaged")
        val controller = ShoppingListController(ShoppingListStore(file))
        controller.load()
        assertTrue(controller.state.loadFailed)
        assertTrue(controller.state.error)
        controller.add(recipe())
        controller.clear()
        assertEquals("damaged", file.readText())
    }

    @Test fun `empty ingredients and oversized fields fail without a partial count`() = runBlocking {
        val file = storeFile()
        val controller = ShoppingListController(ShoppingListStore(file))
        controller.load()
        controller.add(recipe(items = ""))
        assertTrue(controller.state.error)
        assertTrue(controller.state.additions.isEmpty())
        controller.add(recipe(items = "- " + "x".repeat(256 * 1024)))
        assertTrue(controller.state.error)
        assertTrue(controller.state.additions.isEmpty())
        assertFalse(file.exists())
        controller.add(recipe())
        assertFalse(controller.state.error)
        assertEquals(1, controller.state.additions.size)
    }
}
