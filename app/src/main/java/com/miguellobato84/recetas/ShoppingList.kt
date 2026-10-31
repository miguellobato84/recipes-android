package com.miguellobato84.recetas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import org.commonmark.node.Heading
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class IngredientAddition(
    val sourcePath: String,
    val title: String,
    val ingredientsMarkdown: String,
)

data class ShoppingRecipeGroup(
    val sourcePath: String,
    val title: String,
    val additions: List<IngredientAddition>,
) {
    val count: Int get() = additions.size
}

fun shoppingRecipeGroups(additions: List<IngredientAddition>): List<ShoppingRecipeGroup> =
    additions.groupBy { it.sourcePath }.map { (path, recipeAdditions) ->
        ShoppingRecipeGroup(path, recipeAdditions.last().title, recipeAdditions)
    }

fun ingredientSectionMarkdown(markdown: String): String {
    // F1 validates the Markdown structure. Parse candidate prefixes as well so
    // headings inside fenced/indented code cannot delimit the saved section.
    val parser = Parser.builder().build()
    fun heading(name: String): MatchResult {
        val escaped = Regex.escape(name)
        val candidates = Regex(
            "(?m)^ {0,3}##[ \t]+$escaped(?:[ \t]+#+)?[ \t]*\r?$|" +
                "^ {0,3}$escaped[ \t]*\r?\n {0,3}-+[ \t]*\r?$"
        )
        return candidates.findAll(markdown).firstOrNull { candidate ->
            val last = parser.parse(markdown.substring(0, candidate.range.last + 1)).lastChild
            last is Heading && last.level == 2 &&
                (last.firstChild as? Text)?.literal == name && last.firstChild.next == null
        } ?: error("No se pudo leer la sección $name.")
    }
    val start = heading("Ingredientes").range.last + 1
    val end = heading("Utensilios").range.first
    require(end > start) { "Secciones fuera de orden." }
    val section = markdown.substring(start, end).trim('\r', '\n')
    require(section.isNotBlank()) { "La receta no tiene ingredientes." }
    return section
}

data class ShoppingListState(
    val additions: List<IngredientAddition> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadFailed: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

/** Serializes complete mutations. State changes only after the disk commit. */
class ShoppingListController(private val store: ShoppingListStore) {
    private val mutex = Mutex()
    var state by mutableStateOf(ShoppingListState())
        private set

    suspend fun load() = mutex.withLock {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            state = state.copy(additions = saved, loading = false, loadFailed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = state.copy(loading = false, loadFailed = true,
                message = "No se pudo cargar la lista de la compra. Cierra y vuelve a abrir la aplicación para reintentar.", error = true)
        }
    }

    suspend fun add(recipe: Recipe) = mutate("Receta añadida a la lista de la compra.") { current ->
        current + IngredientAddition(recipe.path, recipe.title, ingredientSectionMarkdown(recipe.markdown))
    }

    suspend fun clear() = mutate("Lista de la compra vaciada.") { emptyList() }

    private suspend fun mutate(
        success: String,
        transform: (List<IngredientAddition>) -> List<IngredientAddition>,
    ) = mutex.withLock {
        if (state.loading || state.loadFailed) return@withLock
        state = state.copy(saving = true, message = null, error = false)
        try {
            val current = state.additions
            val next = withContext(Dispatchers.IO) { transform(current).also { store.save(it) } }
            state = state.copy(additions = next, saving = false, message = success, error = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = state.copy(saving = false,
                message = "No se pudo guardar la lista de la compra. Comprueba que la receta tiene ingredientes y que hay espacio disponible.", error = true)
        }
    }
}

class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = ShoppingListController(
        ShoppingListStore(File(application.filesDir, "shopping-list.bin")))
    val state: ShoppingListState get() = controller.state

    init { viewModelScope.launch { controller.load() } }
    fun add(recipe: Recipe) { viewModelScope.launch { controller.add(recipe) } }
    fun clear() { viewModelScope.launch { controller.clear() } }
}

/** Stores complete snapshots through a same-directory atomic replacement. */
class ShoppingListStore(
    private val file: File,
    private val replace: (File, File) -> Unit = { staged, active ->
        Files.move(staged.toPath(), active.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    },
) {
    private val pending get() = File(file.absoluteFile.parentFile, "${file.name}.pending")

    fun load(): List<IngredientAddition> {
        if (!file.exists()) return emptyList()
        DataInputStream(FileInputStream(file)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == FORMAT) { "Invalid shopping list" }
            val count = input.readInt()
            require(count in 0..MAX_ADDITIONS) { "Invalid shopping list" }
            return List(count) {
                IngredientAddition(input.readString(), input.readString(), input.readString())
            }.also { require(input.read() == -1) { "Invalid shopping list" } }
        }
    }

    fun save(additions: List<IngredientAddition>) {
        require(additions.size <= MAX_ADDITIONS) { "Shopping list is too large" }
        val parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) error("Cannot create shopping list storage")
        try {
            FileOutputStream(pending).use { fileOutput ->
                DataOutputStream(fileOutput).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(FORMAT)
                    output.writeInt(additions.size)
                    additions.forEach {
                        output.writeString(it.sourcePath)
                        output.writeString(it.title)
                        output.writeString(it.ingredientsMarkdown)
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            replace(pending, file)
        } finally {
            pending.delete()
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "Shopping list entry is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_FIELD_BYTES) { "Invalid shopping list" }
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private companion object {
        const val MAGIC = 0x5245434C
        const val FORMAT = 1
        const val MAX_ADDITIONS = 20_000
        const val MAX_FIELD_BYTES = 256 * 1024
    }
}
