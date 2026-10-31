package com.miguellobato84.recetas

import kotlinx.coroutines.CancellationException

/** One browsing session: make the cache visible before attempting a single refresh. */
internal suspend fun startRecipeBrowser(
    loadCache: suspend () -> List<Recipe>,
    refresh: suspend () -> List<Recipe>,
    publish: (BrowserState) -> Unit,
) {
    val cached = try {
        BrowserState(recipes = loadCache(), loading = false, refreshing = true)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        BrowserState(loading = false, refreshing = true, error = true,
            message = "No se pudieron leer las recetas guardadas. Intentando descargarlas…")
    }
    publish(cached)
    val result = try {
        cached.copy(recipes = refresh(), refreshing = false, error = false,
            message = "Recetas actualizadas.")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        val retained = if (cached.recipes.isNotEmpty()) " Las recetas guardadas se conservan." else ""
        cached.copy(refreshing = false, error = true,
            message = "No se pudo actualizar al abrir la app. Comprueba la conexión, cierra la app y vuelve a abrirla para reintentar.$retained")
    }
    publish(result)
}
