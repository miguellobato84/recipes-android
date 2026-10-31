package com.miguellobato84.recetas

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeSharingTest {
    @Test fun `share URL targets the nested recipe file on main`() {
        assertEquals(
            "https://github.com/miguellobato84/recipes/blob/main/principales/carne/pollo.md",
            recipeSourceUrl("principales/carne/pollo.md").toASCIIString(),
        )
    }

    @Test fun `share URL preserves unicode spaces and reserved path characters`() {
        val path = "dulces/limón 100% #1?.md"
        val uri = recipeSourceUrl(path)
        assertEquals(
            "https://github.com/miguellobato84/recipes/blob/main/dulces/lim%C3%B3n%20100%25%20%231%3F.md",
            uri.toASCIIString(),
        )
        assertEquals("/miguellobato84/recipes/blob/main/$path", uri.path)
        assertEquals(null, uri.query)
        assertEquals(null, uri.fragment)
        assertEquals(uri.path, externalLink(path, "#pasos")!!.path)
    }
}
