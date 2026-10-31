package com.miguellobato84.recetas

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Opt-in smoke check; ordinary acceptance tests never need the network. */
class LiveSourceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `download and validate live public main without credentials`() {
        assumeTrue(System.getenv("VERIFY_LIVE_RECIPES") == "1")
        val repo = RecipeRepository(temporary.newFolder())
        val recipes = repo.refresh()
        assertTrue(recipes.isNotEmpty())
        assertTrue(recipes.any { it.path.startsWith("desayunos/") })
        assertTrue(recipes.any { it.path.startsWith("principales/carne/") })
        assertEquals(recipes, repo.load())
        println("Live source: ${recipes.size} recipes validated and cached")
    }
}
