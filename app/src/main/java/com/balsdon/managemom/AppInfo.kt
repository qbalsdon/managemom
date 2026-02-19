package com.balsdon.managemom

/**
 * Installed app display model: label, package name, whether it can be uninstalled, and sync state.
 * System apps that cannot be uninstalled have canUninstall = false.
 */
data class AppInfo(
    val packageName: String,
    val appName: CharSequence,
    val canUninstall: Boolean,
    val firstInstallTime: Long,
    val isSynced: Boolean = false
)
