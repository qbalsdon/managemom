package com.balsdon.managemom

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object PackageHelper {

    /**
     * Builds a list with a "System apps" section header before the first system app.
     * Used for the RecyclerView that shows both section headers and app rows.
     */
    fun buildListWithSectionHeaders(context: Context, apps: List<AppInfo>): List<AppListItem> {
        val myPackage = context.packageName
        val systemAppsTitle = context.getString(R.string.system_apps)
        val result = mutableListOf<AppListItem>()
        var systemHeaderAdded = false
        for (app in apps) {
            if (!app.canUninstall && app.packageName != myPackage && !systemHeaderAdded) {
                result.add(AppListItem.SectionHeader(systemAppsTitle))
                systemHeaderAdded = true
            }
            result.add(AppListItem.App(app))
        }
        return result
    }

    /** Preferred order for installation source tabs (sources not in this list appear after, sorted by label). */
    private val SOURCE_TAB_ORDER = listOf(
        "Sideloaded",
        "Google Play",
        "Package Installer",
        "Amazon Appstore"
    )

    /**
     * Groups apps by installation source; user apps per source, then "System apps" tab last.
     * Returns (tabLabel, apps) in order: source tabs (user apps only), then ("System apps", system apps).
     */
    fun groupAppsBySource(context: Context, apps: List<AppInfo>): List<Pair<String, List<AppInfo>>> {
        val (userApps, systemApps) = apps.partition { it.canUninstall }
        val grouped = userApps.groupBy { getInstallSourceLabel(it.installerPackageName) }
        val orderedSources = SOURCE_TAB_ORDER.filter { it in grouped.keys } +
            grouped.keys.filter { it !in SOURCE_TAB_ORDER }.sorted()
        val sourceTabs = orderedSources.map { source -> source to grouped[source]!! }
        val systemAppsTitle = context.getString(R.string.system_apps)
        return sourceTabs + (systemAppsTitle to systemApps)
    }

    /**
     * List all installed applications. Packages marked as bug appear at the top.
     * Then user-installed (by install date, alphabetical), then system apps at the bottom.
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val bugSet = BugPackages.getPackages(context)
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        val markedForDeletionSet = UninstallBlocklist.getPackages(context)
        val list = apps
            .filter { it.packageName != "android" }
            .map { applicationInfo ->
                val isSelf = applicationInfo.packageName == myPackage
                val label = applicationInfo.loadLabel(pm)
                val canUninstall = !isSelf && canBeUninstalled(applicationInfo)
                val installTime = getFirstInstallTime(pm, applicationInfo.packageName)
                val isBug = applicationInfo.packageName in bugSet
                val isMarkedForDeletion = applicationInfo.packageName in markedForDeletionSet
                val installerPackageName = getInstallerPackageName(context, applicationInfo.packageName)
                AppInfo(
                    packageName = applicationInfo.packageName,
                    appName = label,
                    canUninstall = canUninstall,
                    firstInstallTime = installTime,
                    isBug = isBug,
                    isMarkedForDeletion = isMarkedForDeletion,
                    installerPackageName = installerPackageName
                )
            }
        val (selfApp, others) = list.partition { it.packageName == myPackage }
        val (bugApps, nonBugApps) = others.partition { it.isBug }
        val (userNonBug, systemNonBug) = nonBugApps.partition { it.canUninstall }
        val (userBugs, systemBugs) = bugApps.partition { it.canUninstall }
        // Sort: by installation source, then by install date (newest first), then by name
        val sort = compareBy<AppInfo> { it.installerPackageName ?: "" }
            .thenByDescending { it.firstInstallTime }
            .thenBy { it.appName.toString().lowercase() }
        val sortedUserBugs = userBugs.sortedWith(sort)
        val sortedSystemBugs = systemBugs.sortedWith(sort)
        val sortedUserNonBug = userNonBug.sortedWith(sort)
        val sortedSystemNonBug = systemNonBug.sortedWith(sort)
        return sortedUserBugs + sortedSystemBugs + sortedUserNonBug + sortedSystemNonBug + selfApp
    }

    /**
     * True if the app can be uninstalled (user-installed or updated system app).
     * Pure system apps return false.
     */
    private fun canBeUninstalled(applicationInfo: ApplicationInfo): Boolean {
        val isSystem = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return !isSystem || isUpdatedSystem
    }

    private fun getFirstInstallTime(pm: PackageManager, packageName: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, 0).firstInstallTime
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).firstInstallTime
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Installer package name for this app (e.g. com.android.vending for Play Store). Null if unknown or sideloaded.
     */
    fun getInstallerPackageName(context: Context, packageName: String): String? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Human-readable installation source label for display (e.g. "Google Play", "Sideloaded"). */
    fun getInstallSourceLabel(installerPackageName: String?): String {
        when {
            installerPackageName.isNullOrBlank() -> return "Sideloaded"
            installerPackageName == "com.android.vending" -> return "Google Play"
            installerPackageName == "com.google.android.packageinstaller" -> return "Package Installer"
            installerPackageName == "com.amazon.venezia" -> return "Amazon Appstore"
            else -> return installerPackageName
        }
    }

    /**
     * Human-readable app label for a package, or the package name if not installed / not found.
     */
    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            ai.loadLabel(context.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * If any blocklisted package is currently installed, start the uninstall flow for the first one.
     * Used when markedForDeletion is updated in Firebase so the app responds to remote list changes.
     */
    fun requestUninstallForFirstBlocklistedInstalled(context: Context) {
        val self = context.packageName
        val pm = context.packageManager
        for (pkg in UninstallBlocklist.getPackages(context)) {
            if (pkg == self) continue
            try {
                pm.getPackageInfo(pkg, 0)
                requestUninstall(context, pkg)
                return
            } catch (_: PackageManager.NameNotFoundException) { }
        }
    }

    /**
     * Returns true if the device allows this app to request package uninstall
     * (an activity can handle ACTION_UNINSTALL_PACKAGE).
     */
    fun canRequestUninstall(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.fromParts("package", "com.example.dummy", null)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo != null
    }

    /**
     * Start the system uninstall flow for the given package.
     * Uses ACTION_UNINSTALL_PACKAGE (with REQUEST_DELETE_PACKAGES); falls back to
     * app details in Settings if the uninstall intent is not available.
     * Never uninstalls this app itself.
     */
    fun requestUninstall(context: Context, packageName: String) {
        if (packageName == context.packageName) return
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        try {
            context.startActivity(uninstallIntent)
        } catch (e: Exception) {
            Log.w("PackageHelper", "Uninstall intent failed, opening app details: $e")
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(settingsIntent)
            } catch (e2: Exception) {
                Log.e("PackageHelper", "Could not open uninstall or app details", e2)
            }
        }
    }
}
