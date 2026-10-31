package com.miguellobato84.recetas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserState(
    val recipes: List<Recipe> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    // Stable across rotation; a fresh ViewModel must not restore a previous session's tree.
    val sessionId = java.util.UUID.randomUUID().toString()
    private val repository = RecipeRepository(File(application.filesDir, "recipe-cache"))
    var state by mutableStateOf(BrowserState())
        private set

    fun refresh() {
        if (state.loading || state.refreshing) return
        viewModelScope.launch {
            state = state.copy(refreshing = true, message = null, error = false)
            try {
                val recipes = withContext(Dispatchers.IO) { repository.refresh() }
                state = state.copy(recipes = recipes, refreshing = false,
                    message = "Recetas actualizadas.", error = false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(refreshing = false, error = true,
                    message = "No se pudieron actualizar las recetas. Se conservan las guardadas. Desliza hacia abajo para reintentar.")
            }
        }
    }

    init {
        viewModelScope.launch {
            startRecipeBrowser(
                loadCache = { withContext(Dispatchers.IO) { repository.load() } },
                refresh = { withContext(Dispatchers.IO) { repository.refresh() } },
                publish = { state = it },
            )
        }
    }
}
