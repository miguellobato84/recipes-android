package com.miguellobato84.recetas

import java.text.Collator
import java.util.Locale

data class TreeEntry(val entry: BrowserEntry, val depth: Int)

/** Index a snapshot once; expanding a branch only walks visible entries. */
class RecipeTree(recipes: List<Recipe>) {
    private val children: Map<String, List<BrowserEntry>>
    val folders: Set<String>

    init {
        val entries = linkedMapOf<String, BrowserEntry>()
        for (recipe in recipes) {
            var slash = recipe.path.indexOf('/')
            while (slash >= 0) {
                val folder = recipe.path.substring(0, slash)
                entries[folder] = BrowserEntry(folder, folder.substringAfterLast('/'), true)
                slash = recipe.path.indexOf('/', slash + 1)
            }
            entries[recipe.path] = BrowserEntry(recipe.path, recipe.title, false)
        }
        folders = entries.values.filter { it.isFolder }.map { it.path }.toSet()
        val collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }
        val order = Comparator<BrowserEntry> { a, b ->
            when {
                a.isFolder != b.isFolder -> if (a.isFolder) -1 else 1
                else -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.path.compareTo(b.path)
            }
        }
        children = entries.values.groupBy { it.path.substringBeforeLast('/', "") }
            .mapValues { (_, values) -> values.sortedWith(order) }
    }

    fun visibleEntries(expanded: Set<String>): List<TreeEntry> {
        val visible = mutableListOf<TreeEntry>()
        val pending = java.util.ArrayDeque<TreeEntry>()
        fun appendChildren(folder: String, depth: Int) {
            children[folder].orEmpty().asReversed().forEach { pending.addLast(TreeEntry(it, depth)) }
        }
        appendChildren("", 0)
        while (pending.isNotEmpty()) {
            val row = pending.removeLast()
            visible += row
            if (row.entry.isFolder && row.entry.path in expanded) appendChildren(row.entry.path, row.depth + 1)
        }
        return visible
    }

    fun toggle(expanded: Set<String>, folder: String): Set<String> {
        val surviving = expanded.intersect(folders)
        if (folder !in folders) return surviving
        return if (folder in surviving) surviving.filterNot { it == folder || it.startsWith("$folder/") }.toSet()
        else surviving + folder
    }
}
