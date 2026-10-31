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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text as MarkdownText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdateManager.start(applicationContext)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { RecipeBrowser() }
            }
        }
    }
}

@Composable
private fun RecipeBrowser(model: BrowserViewModel = viewModel()) {
    val state = model.state
    val updateStatus by AppUpdateManager.status.collectAsState()
    var location by rememberSaveable { mutableStateOf("") }
    val visibleLocation = if (state.loading) location else survivingLocation(state.recipes, location)
    LaunchedEffect(visibleLocation) { if (!state.loading) location = visibleLocation }
    val recipe = state.recipes.firstOrNull { it.path == visibleLocation }
    val folder = if (recipe == null) visibleLocation else recipe.path.substringBeforeLast('/')
    fun back() { location = visibleLocation.substringBeforeLast('/', "") }
    BackHandler(visibleLocation.isNotEmpty()) { back() }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            if (visibleLocation.isNotEmpty()) TextButton(onClick = { back() }, modifier = Modifier.weight(1f)) { Text("Atrás") }
            else Text("Recetas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(12.dp))
        }
        if (state.loading || state.refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (state.loading) "Cargando recetas guardadas…" else "Descargando y validando recetas…",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let {
            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
                color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        updateStatus.message?.let {
            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
                color = if (updateStatus.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (folder.isNotEmpty()) {
            Text(folder.replace("/", " / "), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
        when {
            state.loading -> Unit
            state.recipes.isEmpty() -> Text("No hay recetas disponibles.", Modifier.padding(24.dp))
            recipe != null -> key(recipe) { RecipeDetail(recipe) }
            else -> key(visibleLocation) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(folderEntries(state.recipes, visibleLocation), key = { it.path }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.label) },
                            supportingContent = if (entry.isFolder) ({ Text("Carpeta") }) else null,
                            trailingContent = { Text("›") },
                            modifier = Modifier.clickable { location = entry.path },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeDetail(recipe: Recipe) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = LocalDensity.current
    val textSizePx = with(density) { MaterialTheme.typography.bodyLarge.fontSize.toPx() }
    val markwon = remember(recipe.path) {
        Markwon.builder(context).usePlugin(object : AbstractMarkwonPlugin() {
            override fun beforeRender(node: Node) {
                fun replaceImages(parent: Node) {
                    var child = parent.firstChild
                    while (child != null) {
                        val next = child.next
                        if (child is Image) {
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
                    val uri = externalLink(recipe.path, link)
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
    val rendered by produceState<CharSequence?>(null, recipe.markdown, markwon) {
        value = withContext(Dispatchers.Default) { markwon.toMarkdown(recipe.markdown) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Raciones: ${recipe.servings}", style = MaterialTheme.typography.titleMedium)
        if (recipe.tags.isNotEmpty()) Text("Etiquetas: ${recipe.tags.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        if (rendered == null) Text("Preparando receta…")
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
