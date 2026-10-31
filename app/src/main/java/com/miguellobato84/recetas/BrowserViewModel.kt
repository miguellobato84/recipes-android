package com.miguellobato84.recetas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
    private val repository = RecipeRepository(File(application.filesDir, "recipe-cache"))
    var state by mutableStateOf(BrowserState())
        private set

    init {
        viewModelScope.launch {
            state = try {
                BrowserState(recipes = withContext(Dispatchers.IO) { repository.load() }, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BrowserState(loading = false, message = "No se pudieron leer las recetas guardadas. Pulsa Actualizar para recuperarlas.", error = true)
            }
        }
    }

    fun refresh() {
        if (state.loading || state.refreshing) return
        state = state.copy(refreshing = true, message = null, error = false)
        viewModelScope.launch {
            state = try {
                val recipes = withContext(Dispatchers.IO) { repository.refresh() }
                state.copy(recipes = recipes, refreshing = false, message = "Recetas actualizadas.", error = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.copy(refreshing = false, error = true,
                    message = "No se pudo actualizar. Comprueba la conexión e inténtalo de nuevo. Las recetas guardadas se conservan.")
            }
        }
    }
}
