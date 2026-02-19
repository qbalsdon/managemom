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
     * List all installed applications: user-installed (and updated system) first sorted by
     * install date then alphabetical; system apps that cannot be uninstalled at the bottom,
     * same sort.
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        val list = apps
            .filter { it.packageName != myPackage && it.packageName != "android" }
            .map { applicationInfo ->
                val label = applicationInfo.loadLabel(pm)
                val canUninstall = canBeUninstalled(applicationInfo)
                val installTime = getFirstInstallTime(pm, applicationInfo.packageName)
                AppInfo(
                    packageName = applicationInfo.packageName,
                    appName = label,
                    canUninstall = canUninstall,
                    firstInstallTime = installTime
                )
            }
        val (userApps, systemApps) = list.partition { it.canUninstall }
        val sortedUser = userApps.sortedWith(
            compareByDescending<AppInfo> { it.firstInstallTime }
                .thenBy { it.appName.toString().lowercase() }
        )
        val sortedSystem = systemApps.sortedWith(
            compareByDescending<AppInfo> { it.firstInstallTime }
                .thenBy { it.appName.toString().lowercase() }
        )
        return sortedUser + sortedSystem
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
     */
    fun requestUninstall(context: Context, packageName: String) {
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
