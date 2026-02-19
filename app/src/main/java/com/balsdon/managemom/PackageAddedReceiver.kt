package com.balsdon.managemom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * When any package is installed, if it is in the uninstall blocklist we trigger uninstall
 * so it is auto-removed (e.g. after a reinstall).
 */
class PackageAddedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        if (!UninstallBlocklist.contains(context, pkg)) return
        PackageHelper.requestUninstall(context, pkg)
    }
}
