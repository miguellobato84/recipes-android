package com.miguellobato84.recetas

import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class FolderAppearanceTest {
    private val categories = linkedMapOf(
        "desayunos" to FolderIcon.SUNRISE, "dulces" to FolderIcon.CAKE,
        "entrantes" to FolderIcon.PLATE, "guarniciones" to FolderIcon.BOWL,
        "masas" to FolderIcon.ROLLING_PIN, "panificadora" to FolderIcon.BREAD,
        "principales" to FolderIcon.DISH, "principales/carne" to FolderIcon.MEAT,
        "principales/pescado" to FolderIcon.FISH, "principales/vegetarianos" to FolderIcon.LEAF,
        "salsas" to FolderIcon.SAUCE,
    )

    @Test fun `category mappings apply at every level without parent inheritance`() {
        val appearances = FolderAppearances.forFolders(categories.keys)
        categories.forEach { (path, icon) -> assertEquals(path, icon, appearances.getValue(path).icon) }
        assertEquals(11, appearances.values.toSet().size)
        assertEquals(appearances.getValue("dulces"),
            FolderAppearances.forFolders(setOf("otra/DÚLCES")).getValue("otra/DÚLCES"))
        assertEquals(FolderIcon.FOLDER,
            FolderAppearances.forFolders(setOf("principales/nueva")).getValue("principales/nueva").icon)
    }

    @Test fun `identities survive source order and recipe-only refreshes including nested fallbacks`() {
        val paths = categories.keys + setOf("otras", "principales/otras", "nuevas")
        assertEquals(FolderAppearances.forFolders(paths), FolderAppearances.forFolders(paths.reversed().toSet()))
        fun recipe(path: String, title: String) = Recipe(path, title, "4", emptyList(), "")
        val original = RecipeTree(listOf(recipe("otras/uno.md", "Uno"), recipe("principales/carne/a.md", "A")))
        val refreshed = RecipeTree(listOf(recipe("principales/carne/b.md", "B"), recipe("otras/dos.md", "Dos")))
        assertEquals(FolderAppearances.forFolders(original.folders), FolderAppearances.forFolders(refreshed.folders))
        assertTrue(FolderAppearances.forFolders(emptySet()).isEmpty())
    }

    @Test fun `sibling collisions resolve for normalized names and thousands of unknown categories`() {
        // String hash collisions exercise the fallback collision-resolution branch.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val unknown = (0 until 5000).map { "nueva-$it" }.toSet() + setOf("Aa", "BB")
        val paths = unknown + setOf("dulces", "DULCES", "dúlces")
        val appearances = FolderAppearances.forFolders(paths)
        assertEquals(paths.size, appearances.values.toSet().size)
        assertTrue(unknown.all { appearances.getValue(it).icon == FolderIcon.FOLDER })
        assertEquals(appearances, FolderAppearances.forFolders(paths.reversed().toSet()))
        assertEquals(FolderIcon.CAKE, appearances.getValue("dúlces").icon)
        assertNotEquals(appearances.getValue("Aa"), appearances.getValue("BB"))
    }

    @Test fun `all semantic and fallback shades meet icon contrast in light and dark modes`() {
        val appearances = FolderAppearances.forFolders(categories.keys + "unknown").values
        appearances.forEach { appearance ->
            // Relative luminance is monotonic per channel: these are the worst
            // endpoints of every possible generated background, not just fixtures.
            for (dark in listOf(false, true)) {
                val colors = appearance.colors(dark)
                assertTrue("${appearance.icon}, dark=$dark", contrast(colors.foreground, colors.background) >= 3.0)
            }
        }
        val bases = FolderAppearances.forFolders(categories.keys).values
        bases.forEach { appearance ->
            assertTrue("${appearance.icon} light variants",
                contrast(appearance.light.foreground, appearance.light.background - 0x1F1F1F) >= 3.0)
            assertTrue("${appearance.icon} dark variants",
                contrast(appearance.dark.foreground, appearance.dark.background + 0x1F1F1F) >= 3.0)
        }
        // Neutral extremes, including all collision-resolved shade combinations.
        assertTrue(contrast(0xFF3D444A, 0xFFD1D1D1) >= 3.0)
        assertTrue(contrast(0xFFD5DADF, 0xFF434343) >= 3.0)
    }

    @Test fun `pinned source gives every folder a badge and unique sibling identities`() {
        val file = kotlin.io.path.createTempFile(suffix = ".zip").toFile()
        try {
            javaClass.getResourceAsStream("/source-494130c.zip")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val tree = RecipeTree(SnapshotReader.read(file))
            val appearances = FolderAppearances.forFolders(tree.folders)
            assertEquals(tree.folders, appearances.keys)
            tree.folders.groupBy { it.substringBeforeLast('/', "") }.values.forEach { siblings ->
                assertEquals(siblings.size, siblings.map { appearances.getValue(it) }.toSet().size)
            }
        } finally { file.delete() }
    }

    private fun contrast(first: Long, second: Long): Double {
        fun luminance(rgb: Long): Double {
            fun channel(shift: Int): Double {
                val value = ((rgb shr shift) and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
