package com.balsdon.managemom

/**
 * RecyclerView list item: either a section header or an app row.
 */
sealed class AppListItem {

    data class SectionHeader(val title: String) : AppListItem()

    data class App(val appInfo: AppInfo) : AppListItem()
}
