package com.miguellobato84.recetas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BrowserStartupTest {
    private val old = Recipe("desayunos/a.md", "Antes", "2", emptyList(), "# Antes")
    private val updated = old.copy(title = "Después", markdown = "# Después")

    @Test fun `cache is browsable before startup download completes`() = runBlocking {
        val download = CompletableDeferred<List<Recipe>>()
        val states = mutableListOf<BrowserState>()
        val calls = mutableListOf<String>()
        val startup = launch(start = CoroutineStart.UNDISPATCHED) {
            startRecipeBrowser(
                loadCache = { calls += "load"; listOf(old) },
                refresh = { calls += "refresh"; download.await() },
                publish = states::add,
            )
        }
        assertEquals(listOf("load", "refresh"), calls)
        assertEquals(listOf(old), states.single().recipes)
        assertFalse(states.single().loading)
        assertTrue(states.single().refreshing)
        download.complete(listOf(updated))
        startup.join()
        assertEquals(listOf(updated), states.last().recipes)
        assertFalse(states.last().refreshing)
        assertFalse(states.last().error)
        assertEquals(2, states.size)
    }

    @Test fun `fresh install automatically downloads once per new session`() = runBlocking {
        var loads = 0
        var refreshes = 0
        repeat(2) {
            val states = mutableListOf<BrowserState>()
            startRecipeBrowser(
                loadCache = { loads++; emptyList() },
                refresh = { refreshes++; listOf(updated) },
                publish = states::add,
            )
            assertTrue(states.first().recipes.isEmpty())
            assertTrue(states.first().refreshing)
            assertEquals(listOf(updated), states.last().recipes)
        }
        assertEquals(2, loads)
        assertEquals(2, refreshes)
    }

    @Test fun `offline startup retains old cache and gives Spanish reopen guidance`() = runBlocking {
        val states = mutableListOf<BrowserState>()
        startRecipeBrowser({ listOf(old) }, { throw IOException("Offline") }, states::add)
        val failed = states.last()
        assertEquals(listOf(old), failed.recipes)
        assertTrue(failed.error)
        assertFalse(failed.loading)
        assertFalse(failed.refreshing)
        val message = requireNotNull(failed.message)
        assertTrue(message.contains("vuelve a abrirla"))
        assertTrue(message.contains("se conservan"))
        assertFalse(message.contains("Actualizar"))
    }

    @Test fun `first startup failure remains empty and invalid snapshot preserves old cache`() = runBlocking {
        for (cache in listOf(emptyList(), listOf(old))) {
            val states = mutableListOf<BrowserState>()
            startRecipeBrowser({ cache }, { throw InvalidSnapshot("Invalid recipe") }, states::add)
            assertEquals(cache, states.last().recipes)
            assertTrue(states.last().error)
            assertFalse(states.last().refreshing)
        }
    }

    @Test fun `unreadable cache still attempts startup recovery`() = runBlocking {
        val states = mutableListOf<BrowserState>()
        startRecipeBrowser({ throw IOException("Corrupt cache") }, { listOf(updated) }, states::add)
        assertTrue(states.first().error)
        assertTrue(states.first().refreshing)
        assertEquals(listOf(updated), states.last().recipes)
        assertFalse(states.last().error)
    }

    @Test fun `cancelled session is not reported as download failure or retried`() = runBlocking {
        for (cancelLoad in listOf(true, false)) {
            val states = mutableListOf<BrowserState>()
            var refreshes = 0
            try {
                startRecipeBrowser(
                    loadCache = { if (cancelLoad) throw CancellationException(); listOf(old) },
                    refresh = { refreshes++; throw CancellationException() },
                    publish = states::add,
                )
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertEquals(if (cancelLoad) 0 else 1, refreshes)
            assertTrue(states.none { it.error })
        }
    }
}
