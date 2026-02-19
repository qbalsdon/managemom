package com.balsdon.managemom

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM data messages. Expected payload: "packages" = comma-separated package names
 * (e.g. "com.example.app1,com.example.app2"). Shows Snackbar with app names, adds to
 * blocklist, and triggers uninstall for any that are currently installed.
 */
class ManageMomMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val packages = message.data["packages"] ?: message.data["pendingUninstalls"] ?: return
        val packageNames = packages.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (packageNames.isEmpty()) return
        val appNames = packageNames.map { PackageHelper.getAppLabel(this, it) }
        val mainHandler = Handler(Looper.getMainLooper())
        PendingDeleteSnackbar.setAndNotify(appNames, mainHandler)
        UninstallBlocklist.addPackages(this, packageNames)
        val pm = packageManager
        for (pkg in packageNames) {
            try {
                pm.getPackageInfo(pkg, 0)
                PackageHelper.requestUninstall(this, pkg)
            } catch (_: PackageManager.NameNotFoundException) { }
        }
    }

    override fun onNewToken(token: String) {
        FirebaseSync.saveFcmToken(this, token)
    }
}
