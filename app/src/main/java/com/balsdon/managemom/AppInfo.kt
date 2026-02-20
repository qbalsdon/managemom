package com.balsdon.managemom

/**
 * Installed app display model: label, package name, whether it can be uninstalled, sync state, bug flag, marked-for-deletion, and installer.
 * System apps that cannot be uninstalled have canUninstall = false.
 * isBug = true puts the app at the top of the list.
 * isMarkedForDeletion = true when the app is in the deletion list (bug icon shown only then).
 * installerPackageName = package that installed this app (e.g. com.android.vending for Play), null if unknown/sideloaded.
 */
data class AppInfo(
    val packageName: String,
    val appName: CharSequence,
    val canUninstall: Boolean,
    val firstInstallTime: Long,
    val isSynced: Boolean = false,
    val isBug: Boolean = false,
    val isMarkedForDeletion: Boolean = false,
    val installerPackageName: String? = null
)
