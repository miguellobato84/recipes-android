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
    private val repository = RecipeRepository(File(application.filesDir, "recipe-cache"))
    var state by mutableStateOf(BrowserState())
        private set

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
