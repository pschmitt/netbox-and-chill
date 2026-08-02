package dev.pschmitt.netboxandchill.ui.dashboard

enum class DashboardSection(val key: String, val title: String) {
    Stats("stats", "Stats"),
    Search("search", "Search NetBox"),
    News("news", "NetBox news"),
    Bookmarks("bookmarks", "Bookmarks"),
    RecentChanges("recent_changes", "Recent changes"),
}

fun orderedDashboardSections(
    savedOrder: List<String>,
    hidden: Set<String>,
): List<DashboardSection> {
    val customRank = savedOrder.withIndex().associate { it.value to it.index }
    return DashboardSection.entries
        .filterNot { it.key in hidden }
        .sortedWith(
            compareBy<DashboardSection> { customRank[it.key] == null }
                .thenBy { customRank[it.key] ?: Int.MAX_VALUE }
                .thenBy { it.ordinal }
        )
}

fun allDashboardSectionKeys(): List<String> = DashboardSection.entries.map { it.key }
