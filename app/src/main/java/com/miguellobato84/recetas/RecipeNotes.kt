package com.miguellobato84.recetas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RecipeNotesState(
    val notes: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/** Drafts update immediately on the main thread; only complete snapshots commit on IO. */
class RecipeNotesController(private val store: RecipeNotesStore) {
    private val mutex = Mutex()
    private var persisted: Map<String, String> = emptyMap()
    var state by mutableStateOf(RecipeNotesState())
        private set

    suspend fun load() = mutex.withLock {
        state = state.copy(loading = true, error = null)
        try {
            persisted = withContext(Dispatchers.IO) { store.load() }
            state = RecipeNotesState(notes = persisted, loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = state.copy(loading = false, loadFailed = true,
                error = "No se pudieron cargar las notas. Reintenta para poder editarlas.")
        }
    }

    fun edit(path: String, text: String) {
        if (state.loading || state.loadFailed) return
        val next = if (text.isEmpty()) state.notes - path else state.notes + (path to text)
        state = state.copy(notes = next, dirty = next != persisted, error = null)
    }

    suspend fun savePending() = mutex.withLock {
        if (state.loading || state.loadFailed) return@withLock
        try {
            while (state.notes != persisted) {
                val snapshot = state.notes
                state = state.copy(saving = true, error = null)
                withContext(Dispatchers.IO) { store.save(snapshot) }
                persisted = snapshot
                // Edits may have arrived while IO was suspended. Never replace the draft.
                state = state.copy(dirty = state.notes != persisted)
            }
            state = state.copy(saving = false, error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = state.copy(saving = false, dirty = state.notes != persisted,
                error = "No se pudieron guardar las notas. El texto sigue aquí; reintenta antes de cerrar la aplicación.")
        }
    }
}

class RecipeNotesViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = RecipeNotesController(
        RecipeNotesStore(File(application.filesDir, "recipe-notes.json")))
    val state: RecipeNotesState get() = controller.state

    init { viewModelScope.launch { controller.load() } }

    fun edit(path: String, text: String) {
        controller.edit(path, text)
        viewModelScope.launch { controller.savePending() }
    }

    fun retry() {
        viewModelScope.launch {
            if (state.loadFailed) controller.load() else controller.savePending()
        }
    }
}

/** Separate from recipe downloads and Android's evictable cache directory. */
class RecipeNotesStore(
    private val file: File,
    private val replace: (File, File) -> Unit = { staged, active ->
        Files.move(staged.toPath(), active.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    },
) {
    fun load(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.get("version") == 1) { "Unsupported notes format" }
        val notes = root.getJSONObject("notes")
        return buildMap {
            notes.keys().forEach { path ->
                val text = notes.get(path)
                require(path.isNotEmpty() && text is String && text.isNotEmpty()) { "Invalid note" }
                put(path, text)
            }
        }
    }

    fun save(notes: Map<String, String>) {
        val entries = JSONObject()
        notes.forEach { (path, text) ->
            require(path.isNotEmpty() && text.isNotEmpty()) { "Invalid note" }
            entries.put(path, text)
        }
        val bytes = JSONObject().put("version", 1).put("notes", entries)
            .toString().toByteArray(Charsets.UTF_8)
        val parent = file.absoluteFile.parentFile
        if (!parent.isDirectory && !parent.mkdirs()) error("Cannot create notes storage")
        val pending = File(parent, "${file.name}.pending")
        try {
            FileOutputStream(pending).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            replace(pending, file)
        } finally {
            pending.delete()
        }
    }
}
