package com.miguellobato84.recetas

import java.text.Normalizer
import java.util.Locale

enum class FolderIcon(val drawable: Int) {
    SUNRISE(R.drawable.ic_category_sunrise), CAKE(R.drawable.ic_category_cake),
    PLATE(R.drawable.ic_category_plate), BOWL(R.drawable.ic_category_bowl),
    ROLLING_PIN(R.drawable.ic_category_rolling_pin), BREAD(R.drawable.ic_category_bread),
    DISH(R.drawable.ic_category_dish), MEAT(R.drawable.ic_category_meat),
    FISH(R.drawable.ic_category_fish), LEAF(R.drawable.ic_category_leaf),
    SAUCE(R.drawable.ic_category_sauce), FOLDER(R.drawable.ic_category_folder),
}

data class BadgeColors(val foreground: Long, val background: Long)

data class FolderAppearance(val icon: FolderIcon, val light: BadgeColors, val dark: BadgeColors) {
    fun colors(darkTheme: Boolean): BadgeColors = if (darkTheme) dark else light
}

/** Offline identities depend on folder paths, never expansion, recipes, or iteration order. */
object FolderAppearances {
    private fun badge(icon: FolderIcon, ink: Long, paper: Long, darkInk: Long, darkPaper: Long) =
        FolderAppearance(icon, BadgeColors(ink, paper), BadgeColors(darkInk, darkPaper))

    private val categories = mapOf(
        "desayunos" to badge(FolderIcon.SUNRISE, 0xFF754600, 0xFFFFF0CF, 0xFFFFD180, 0xFF382A10),
        "dulces" to badge(FolderIcon.CAKE, 0xFF8C2852, 0xFFFFE4ED, 0xFFFFB1CD, 0xFF402030),
        "entrantes" to badge(FolderIcon.PLATE, 0xFF00645D, 0xFFD8F4EE, 0xFF8DDED2, 0xFF143832),
        "guarniciones" to badge(FolderIcon.BOWL, 0xFF505900, 0xFFEEF0CD, 0xFFD4DC8C, 0xFF303510),
        "masas" to badge(FolderIcon.ROLLING_PIN, 0xFF6D501E, 0xFFF6EACA, 0xFFE6CB91, 0xFF382E1B),
        "panificadora" to badge(FolderIcon.BREAD, 0xFF754527, 0xFFF5E3D6, 0xFFE9BC9B, 0xFF3B291E),
        "principales" to badge(FolderIcon.DISH, 0xFF923D16, 0xFFFFE5D6, 0xFFFFB68F, 0xFF42291B),
        "carne" to badge(FolderIcon.MEAT, 0xFF963B36, 0xFFFFE2DE, 0xFFFFB4AC, 0xFF432522),
        "pescado" to badge(FolderIcon.FISH, 0xFF175A90, 0xFFDEEEFF, 0xFFA4CEFF, 0xFF1B3045),
        "vegetarianos" to badge(FolderIcon.LEAF, 0xFF306329, 0xFFE2F2D9, 0xFFACD99B, 0xFF21371D),
        "salsas" to badge(FolderIcon.SAUCE, 0xFF70408E, 0xFFF2E4FA, 0xFFDDB7F5, 0xFF35233F),
    )
    private val neutral = badge(FolderIcon.FOLDER, 0xFF3D444A, 0xFFF0F0F0, 0xFFD5DADF, 0xFF242424)

    fun forFolders(paths: Set<String>): Map<String, FolderAppearance> {
        val result = mutableMapOf<String, FolderAppearance>()
        paths.groupBy { it.substringBeforeLast('/', "") }.values.forEach { siblings ->
            val used = mutableSetOf<FolderAppearance>()
            // Reserve canonical identities first; unknown folders cannot displace them.
            val ordered = siblings.sortedWith(compareBy<String> { category(it) == null }.thenBy { it })
            for (path in ordered) {
                val known = category(path)
                val base = known ?: neutral
                var shade = if (known != null) 0 else path.hashCode() and 0x7fff
                var appearance = vary(base, shade)
                while (!used.add(appearance)) {
                    shade = (shade + 1) and 0x7fff
                    appearance = vary(base, shade)
                }
                result[path] = appearance
            }
        }
        return result
    }

    private fun category(path: String): FolderAppearance? {
        val name = Normalizer.normalize(path.substringAfterLast('/'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        return categories[name]
    }

    // 32,768 nearby background pairs: enough for every folder in the bounded snapshot.
    // Darken pale badges and lighten dark ones; the icon ink remains fixed.
    private fun vary(base: FolderAppearance, shade: Int): FolderAppearance {
        fun shifted(color: Long, direction: Int): Long {
            var rgb = 0xFF000000L
            for (channel in 0..2) {
                val shift = channel * 8
                val amount = (shade shr (channel * 5)) and 31
                val value = (((color shr shift) and 255).toInt() + direction * amount).coerceIn(0, 255)
                rgb = rgb or (value.toLong() shl shift)
            }
            return rgb
        }
        return base.copy(light = base.light.copy(background = shifted(base.light.background, -1)),
            dark = base.dark.copy(background = shifted(base.dark.background, 1)))
    }
}
