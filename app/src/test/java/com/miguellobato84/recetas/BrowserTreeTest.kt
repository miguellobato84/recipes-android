package com.miguellobato84.recetas

import org.junit.Assert.*
import org.junit.Test

class BrowserTreeTest {
    private fun recipe(path: String, title: String = path) = Recipe(path, title, "4", emptyList(), "")
    private val recipes = listOf(
        recipe("principales/carne/pollo.md", "Pollo"),
        recipe("principales/carne/guisos/estofado.md", "Estofado"),
        recipe("principales/carne-asada/otra.md", "Otra"),
        recipe("principales/arroz.md", "Arroz"),
        recipe("desayunos/tortitas.md", "Tortitas"),
        recipe("dulces/z.md", "Zeta"),
        recipe("dulces/a.md", "Árbol"),
        recipe("dulces/a2.md", "arbol"),
        recipe("dulces/n.md", "Ñoquis"),
    )

    @Test fun `new tree starts with collapsed top-level folders and empty source stays empty`() {
        val rows = RecipeTree(recipes).visibleEntries(emptySet())
        assertEquals(listOf("desayunos", "dulces", "principales"), rows.map { it.entry.path })
        assertTrue(rows.all { it.entry.isFolder && it.depth == 0 })
        assertTrue(RecipeTree(emptyList()).visibleEntries(emptySet()).isEmpty())
    }

    @Test fun `expansion shows immediate children with nested folder-first Spanish ordering`() {
        val tree = RecipeTree(recipes)
        val expanded = tree.toggle(tree.toggle(emptySet(), "principales"), "dulces")
        val rows = tree.visibleEntries(expanded)
        assertEquals(listOf("desayunos", "dulces", "dulces/a.md", "dulces/a2.md", "dulces/n.md", "dulces/z.md",
            "principales", "principales/carne", "principales/carne-asada", "principales/arroz.md"),
            rows.map { it.entry.path })
        assertEquals(listOf(0, 0, 1, 1, 1, 1, 0, 1, 1, 1), rows.map { it.depth })
        assertFalse(rows.any { it.entry.path.endsWith("pollo.md") })
    }

    @Test fun `closing branch resets descendants without touching sibling branches or prefix matches`() {
        val tree = RecipeTree(recipes)
        val expanded = setOf("desayunos", "principales", "principales/carne", "principales/carne/guisos", "principales/carne-asada")
        val closed = tree.toggle(expanded, "principales/carne")
        assertEquals(setOf("desayunos", "principales", "principales/carne-asada"), closed)
        val reopened = tree.toggle(closed, "principales/carne")
        val paths = tree.visibleEntries(reopened).map { it.entry.path }
        assertTrue("principales/carne/pollo.md" in paths)
        assertTrue("principales/carne/guisos" in paths)
        assertFalse("principales/carne/guisos/estofado.md" in paths)
        assertTrue("desayunos/tortitas.md" in paths)
    }

    @Test fun `refresh retains surviving expansion and new or reintroduced folders start collapsed`() {
        val old = setOf("principales", "principales/carne", "desayunos")
        val refreshed = RecipeTree(listOf(recipe("principales/carne/pollo.md"),
            recipe("principales/pescado/atun.md"), recipe("nuevas/pan.md")))
        val surviving = old.intersect(refreshed.folders)
        assertEquals(setOf("principales", "principales/carne"), surviving)
        val paths = refreshed.visibleEntries(surviving).map { it.entry.path }
        assertTrue("principales/carne/pollo.md" in paths)
        assertFalse("principales/pescado/atun.md" in paths)
        assertFalse("nuevas/pan.md" in paths)
        val restored = RecipeTree(recipes)
        assertFalse(restored.visibleEntries(surviving).any { it.entry.path == "desayunos/tortitas.md" })
        assertTrue(RecipeTree(emptyList()).toggle(old, "principales").isEmpty())
    }

    @Test fun `fully expanded source fixture exposes every recipe once with title and depth`() {
        val file = kotlin.io.path.createTempFile(suffix = ".zip").toFile()
        try {
            javaClass.getResourceAsStream("/source-494130c.zip")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val source = SnapshotReader.read(file)
            val tree = RecipeTree(source)
            val leaves = tree.visibleEntries(tree.folders).filterNot { it.entry.isFolder }
            assertEquals(27, leaves.size)
            assertEquals(source.map { it.path }.toSet(), leaves.map { it.entry.path }.toSet())
            for (row in leaves) {
                assertEquals(source.single { it.path == row.entry.path }.title, row.entry.label)
                assertEquals(row.entry.path.count { it == '/' }, row.depth)
            }
        } finally { file.delete() }
    }
}
