package com.miguellobato84.recetas

import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import java.net.URI
import java.text.Collator
import java.util.Locale

data class Recipe(
    val path: String,
    val title: String,
    val servings: String,
    val tags: List<String>,
    val markdown: String,
)

class InvalidSnapshot(message: String) : Exception(message)

private fun requireRecipe(valid: Boolean, message: String) {
    if (!valid) throw InvalidSnapshot(message)
}

object RecipeParser {
    fun parse(path: String, source: String): Recipe {
        val text = source.replace("\r\n", "\n")
        requireRecipe(text.startsWith("---\n"), "Falta la cabecera YAML: $path")
        val end = text.indexOf("\n---\n", 4)
        requireRecipe(end >= 0, "Cabecera YAML incompleta: $path")
        val header = text.substring(4, end)
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 0
            nestingDepthLimit = 10
            codePointLimit = 262144
        }
        val yaml = Yaml(SafeConstructor(options))
        val fields = try {
            yaml.load<Map<String, Any?>>(header)
                ?: throw InvalidSnapshot("Cabecera vacía: $path")
        } catch (e: Exception) {
            throw InvalidSnapshot("Cabecera YAML no válida: $path")
        }
        requireRecipe(fields.keys == setOf("version_esquema", "titulo", "categoria", "etiquetas", "raciones"),
            "Campos YAML no válidos: $path")
        requireRecipe(fields["version_esquema"] == 1, "Versión de receta no compatible: $path")
        val title = fields["titulo"] as? String
        val category = fields["categoria"] as? String
        val tags = fields["etiquetas"] as? List<*>
        requireRecipe(!title.isNullOrBlank() && title.none { it == '\n' || it == '\r' }, "Título no válido: $path")
        requireRecipe(category == path.substringBefore('/'), "La categoría no coincide con la carpeta: $path")
        requireRecipe(tags != null && tags.all { it is String && it.isNotBlank() }, "Etiquetas no válidas: $path")
        // Read scalar spelling from the YAML node, so numeric servings never get rounded/coerced.
        val mapping = yaml.compose(header.reader()) as MappingNode
        val servings = mapping.value.first { (it.keyNode as ScalarNode).value == "raciones" }.valueNode
        requireRecipe(servings is ScalarNode && servings.value.isNotBlank() && fields["raciones"] != null,
            "Raciones no válidas: $path")
        val markdown = text.substring(end + 5).trim()
        val headings = mutableListOf<Heading>()
        val pending = java.util.ArrayDeque<Pair<Node, Int>>()
        pending.add(Parser.builder().build().parse(markdown) to 0)
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            requireRecipe(depth <= 64, "Markdown demasiado anidado: $path")
            if (node is Heading) headings += node
            var child = node.lastChild
            while (child != null) { pending.add(child to depth + 1); child = child.previous }
        }
        fun headingText(heading: Heading): String {
            // Titles in this contract are plain text, not inline formatting.
            val child = heading.firstChild
            return if (child is Text && child.next == null) child.literal else ""
        }
        requireRecipe(headings.filter { it.level == 1 }.map(::headingText) == listOf(title),
            "El título Markdown no coincide: $path")
        requireRecipe(headings.filter { it.level == 2 }.map(::headingText) ==
            listOf("Ingredientes", "Utensilios", "Pasos", "Notas"), "Secciones no válidas: $path")
        requireRecipe(headings.firstOrNull()?.level == 1, "Orden de secciones no válido: $path")
        return Recipe(path, title!!, (servings as ScalarNode).value, tags!!.map { it as String }, markdown)
    }
}

data class BrowserEntry(val path: String, val label: String, val isFolder: Boolean)

fun folderEntries(recipes: List<Recipe>, folder: String): List<BrowserEntry> {
    val prefix = if (folder.isEmpty()) "" else "$folder/"
    val entries = recipes.filter { it.path.startsWith(prefix) }.map { recipe ->
        val rest = recipe.path.removePrefix(prefix)
        if ('/' in rest) BrowserEntry(prefix + rest.substringBefore('/'), rest.substringBefore('/'), true)
        else BrowserEntry(recipe.path, recipe.title, false)
    }.distinctBy { it.path }
    val collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }
    return entries.sortedWith { a, b ->
        when {
            a.isFolder != b.isFolder -> if (a.isFolder) -1 else 1
            else -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.path.compareTo(b.path)
        }
    }
}

fun survivingLocation(recipes: List<Recipe>, location: String): String {
    var current = location
    while (current.isNotEmpty() && recipes.none { it.path == current || it.path.startsWith("$current/") }) {
        current = current.substringBeforeLast('/', "")
    }
    return current
}

fun recipeSourceUrl(recipePath: String): URI =
    URI("https", "github.com", "/miguellobato84/recipes/blob/main/$recipePath", null)

fun externalLink(recipePath: String, destination: String): URI? = runCatching {
    val base = recipeSourceUrl(recipePath)
    val uri = base.resolve(destination)
    uri.takeIf { it.scheme.lowercase(Locale.ROOT) in setOf("http", "https") && !it.host.isNullOrBlank() }
}.getOrNull()
