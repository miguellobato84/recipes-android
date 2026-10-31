package com.miguellobato84.recetas

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Image
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text as MarkdownText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdateManager.start(applicationContext)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { RecipeApp() }
            }
        }
    }
}

@Composable
private fun RecipeApp(
    browser: BrowserViewModel = viewModel(),
    shopping: ShoppingListViewModel = viewModel(),
    notes: RecipeNotesViewModel = viewModel(),
) {
    var shoppingSelected by rememberSaveable { mutableStateOf(false) }
    val destinations = rememberSaveableStateHolder()
    BackHandler(shoppingSelected) { shoppingSelected = false }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shoppingSelected) destinations.SaveableStateProvider("shopping") {
                ShoppingListScreen(shopping.state, shopping::clear)
            } else destinations.SaveableStateProvider("recipes/${browser.sessionId}") {
                RecipeBrowser(browser, shopping.state, shopping::add, notes)
            }
        }
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            NavigationBarItem(selected = !shoppingSelected, onClick = { shoppingSelected = false },
                icon = { Text("R", Modifier.clearAndSetSemantics { }) }, label = { Text("Recetas") })
            NavigationBarItem(selected = shoppingSelected, onClick = { shoppingSelected = true },
                icon = { Text("≡", Modifier.clearAndSetSemantics { }) }, label = { Text("Lista de la compra") })
        }
    }
}

@Composable
private fun ShoppingStatus(state: ShoppingListState) {
    if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.message?.let {
        Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ScreenHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.replaceFirstChar { it.titlecase() }, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        actions()
    }
    HorizontalDivider()
}

@Composable
private fun ShoppingListScreen(state: ShoppingListState, clear: () -> Unit) {
    val groups = remember(state.additions) { shoppingRecipeGroups(state.additions) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Lista de la compra") {
            HeaderActionIcon(R.drawable.ic_delete, "Vaciar carrito",
                enabled = !state.loading && !state.loadFailed, filled = true, onClick = clear)
        }
        ShoppingStatus(state)
        when {
            state.loading -> Text("Cargando lista de la compra…", Modifier.padding(20.dp))
            state.loadFailed -> Unit
            groups.isEmpty() -> Text("La lista de la compra está vacía.", Modifier.padding(20.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item { Text("Recetas incluidas e ingredientes", Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleMedium) }
                groups.forEach { group ->
                    item(key = group.sourcePath) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${group.count}x ${group.title}", style = MaterialTheme.typography.titleLarge)
                            // The path also distinguishes recipes whose titles happen to match.
                            Text(group.sourcePath, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(group.additions.size, key = { index -> "${group.sourcePath}#$index" }) { index ->
                        val addition = group.additions[index]
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Adición ${index + 1}: ${addition.title}", style = MaterialTheme.typography.titleSmall)
                            MarkdownContent(addition.sourcePath, addition.ingredientsMarkdown)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeBrowser(model: BrowserViewModel, shopping: ShoppingListState,
    add: (Recipe) -> Unit, notes: RecipeNotesViewModel) {
    val state = model.state
    val updateStatus by AppUpdateManager.status.collectAsState()
    var selectedPath by rememberSaveable { mutableStateOf("") }
    var expandedFolders by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet<String>()) }
    val treeScroll = rememberLazyListState()
    val tree = remember(state.recipes) { RecipeTree(state.recipes) }
    val folderAppearances = remember(tree.folders) { FolderAppearances.forFolders(tree.folders) }
    val expanded = remember(tree, expandedFolders) { expandedFolders.intersect(tree.folders) }
    val rows = remember(tree, expanded) { tree.visibleEntries(expanded) }
    val recipe = state.recipes.firstOrNull { it.path == selectedPath }
    LaunchedEffect(tree, state.loading) {
        if (!state.loading) {
            expandedFolders = expanded
            if (recipe == null) selectedPath = ""
        }
    }
    BackHandler(recipe != null) { selectedPath = "" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(recipe?.title ?: "Recetas") {
            if (recipe != null) RecipeHeaderActions(recipe, !shopping.loading && !shopping.loadFailed,
                add = { add(recipe) })
        }
        if (state.loading || state.refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (state.loading) "Cargando recetas guardadas…" else "Descargando y validando recetas…",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        state.message?.takeIf { state.error }?.let {
            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
                color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        updateStatus.message?.let {
            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
                color = if (updateStatus.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        ShoppingStatus(shopping)
        when {
            state.loading -> Unit
            state.recipes.isEmpty() -> Text("No hay recetas disponibles.", Modifier.padding(24.dp))
            recipe != null -> key(recipe.path) {
                RecipeDetail(recipe, notes.state, { notes.edit(recipe.path, it) }, notes::retry)
            }
            else -> {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh,
                    modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = treeScroll) {
                        items(rows, key = { it.entry.path }) { row ->
                            val entry = row.entry
                            val isExpanded = entry.path in expanded
                            ListItem(
                                headlineContent = { Text(entry.label.replaceFirstChar { it.titlecase() }) },
                                leadingContent = if (entry.isFolder) ({ FolderBadge(folderAppearances.getValue(entry.path)) }) else null,
                                trailingContent = { Text(if (entry.isFolder && isExpanded) "▾" else "›",
                                    Modifier.clearAndSetSemantics { }) },
                                modifier = Modifier.padding(start = (row.depth * 16).dp)
                                    .semantics {
                                        if (entry.isFolder) stateDescription = if (isExpanded) "Expandida" else "Contraída"
                                    }
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = if (entry.isFolder) {
                                            if (isExpanded) "Contraer carpeta" else "Expandir carpeta"
                                        } else "Abrir receta",
                                    ) {
                                        if (entry.isFolder) expandedFolders = tree.toggle(expanded, entry.path)
                                        else selectedPath = entry.path
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeHeaderActions(recipe: Recipe, canAdd: Boolean, add: () -> Unit) {
    val context = LocalContext.current
    HeaderActionIcon(R.drawable.ic_shopping_cart, "Añadir a la lista de la compra",
        enabled = canAdd, onClick = add)
    HeaderActionIcon(R.drawable.ic_share, "Compartir receta", onClick = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, recipeSourceUrl(recipe.path).toASCIIString())
        }
        try {
            context.startActivity(Intent.createChooser(intent, null))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se puede compartir esta receta.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "No se puede compartir esta receta.", Toast.LENGTH_LONG).show()
        }
    })
}

@Composable
private fun RecipeDetail(recipe: Recipe, notes: RecipeNotesState,
    editNote: (String) -> Unit, retryNotes: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Raciones: ${recipe.servings}", style = MaterialTheme.typography.titleMedium)
        if (recipe.tags.isNotEmpty()) Text("Etiquetas: ${recipe.tags.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        MarkdownContent(recipe.path, recipe.markdown, titleInHeader = true)
        HorizontalDivider()
        OutlinedTextField(
            value = notes.notes[recipe.path].orEmpty(),
            onValueChange = editNote,
            label = { Text("Mis notas") },
            placeholder = { Text("Escribe tus notas sobre esta receta") },
            enabled = !notes.loading && !notes.loadFailed,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(notes.error ?: when {
            notes.loading -> "Cargando notas…"
            notes.saving || notes.dirty -> "Guardando notas…"
            else -> "Notas guardadas en este dispositivo."
        }, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = if (notes.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
        if (notes.error != null) {
            TextButton(onClick = retryNotes, enabled = !notes.loading && !notes.saving) {
                Text("Reintentar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderActionIcon(icon: Int, label: String, enabled: Boolean = true,
    filled: Boolean = false, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        val content: @Composable () -> Unit = {
            Icon(painterResource(icon), contentDescription = label)
        }
        if (filled) FilledTonalIconButton(onClick = onClick, enabled = enabled, content = content)
        else IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
private fun MarkdownContent(sourcePath: String, markdown: String, titleInHeader: Boolean = false) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = LocalDensity.current
    val textSizePx = with(density) { MaterialTheme.typography.bodyLarge.fontSize.toPx() }
    val markwon = remember(sourcePath, titleInHeader) {
        Markwon.builder(context).usePlugin(object : AbstractMarkwonPlugin() {
            override fun beforeRender(node: Node) {
                fun replaceImages(parent: Node) {
                    var child = parent.firstChild
                    while (child != null) {
                        val next = child.next
                        if (titleInHeader && parent === node && child is Heading && child.level == 1) {
                            child.unlink()
                        } else if (child is Image) {
                            val alt = StringBuilder()
                            fun collect(node: Node) {
                                if (node is MarkdownText) alt.append(node.literal)
                                var nested = node.firstChild
                                while (nested != null) { collect(nested); nested = nested.next }
                            }
                            collect(child)
                            child.insertBefore(MarkdownText(alt.toString()))
                            child.unlink()
                        } else replaceImages(child)
                        child = next
                    }
                }
                replaceImages(node)
            }
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { _, link ->
                    val uri = externalLink(sourcePath, link)
                    try {
                        if (uri == null) throw ActivityNotFoundException()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No se puede abrir este enlace.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }).build()
    }
    val rendered by produceState<CharSequence?>(null, markdown, markwon) {
        value = withContext(Dispatchers.Default) { markwon.toMarkdown(markdown) }
    }
    Column(Modifier.fillMaxWidth()) {
        if (rendered == null) Text("Preparando texto…")
        AndroidView(
            factory = { TextView(it).apply { movementMethod = LinkMovementMethod.getInstance() } },
            update = { view ->
                view.setTextColor(color)
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
                view.setLinkTextColor(color)
                if (view.text !== rendered) view.text = rendered ?: ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
