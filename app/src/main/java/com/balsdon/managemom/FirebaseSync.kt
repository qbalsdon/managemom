package com.balsdon.managemom

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Uploads device id and installed package list to Firestore.
 * Collection: "devices", document id = deviceId.
 */
object FirebaseSync {

    const val FIELD_PENDING_UNINSTALLS = "pendingUninstalls"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_MARKED_FOR_DELETION = "markedForDeletion"
    const val FIELD_ACCESSIBILITY_ENABLED = "accessibilityEnabled"

    private const val COLLECTION_DEVICES = "devices"
    private const val FIELD_DEVICE_ID = "deviceId"
    private const val FIELD_PACKAGES = "packages"
    private const val FIELD_LAST_UPDATED = "lastUpdated"

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Upload current device id and package list. On success, [onSuccess] is invoked.
     * markedForDeletion is merged with existing in Firebase (app never removes, only adds).
     * Catches SecurityException when GMS is unavailable (e.g. emulator without Play).
     */
    fun syncToFirebase(
        context: Context,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        try {
            val store = firestore
            val deviceId = DeviceIdHelper.getDeviceId(context)
            val docRef = store.collection(COLLECTION_DEVICES).document(deviceId)
            docRef.get()
                .addOnSuccessListener { snapshot ->
                    val apps = PackageHelper.getInstalledApps(context)
                    val packages = apps.map { app ->
                        hashMapOf(
                            "packageName" to app.packageName,
                            "appName" to app.appName.toString(),
                            "firstInstallTime" to app.firstInstallTime,
                            "installerPackageName" to (app.installerPackageName ?: "")
                        )
                    }
                    val localMarked = UninstallBlocklist.getPackages(context)
                    @Suppress("UNCHECKED_CAST")
                    val existingMarked = (snapshot.get(FIELD_MARKED_FOR_DELETION) as? List<*>)?.filterIsInstance<String>()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                    val mergedMarked = (existingMarked + localMarked).toList().sorted()
                    val data = hashMapOf(
                        FIELD_DEVICE_ID to deviceId,
                        FIELD_PACKAGES to packages,
                        FIELD_LAST_UPDATED to com.google.firebase.Timestamp.now(),
                        FIELD_MARKED_FOR_DELETION to mergedMarked,
                        FIELD_ACCESSIBILITY_ENABLED to ManageMomAccessibilityService.isEnabled(context)
                    )
                    docRef.set(data, SetOptions.merge())
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError(it) }
                }
                .addOnFailureListener { onError(it) }
        } catch (e: SecurityException) {
            onError(e)
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * One-time read of device document: if pendingUninstalls is set, add to local blocklist,
     * uninstall any that are installed, and clear the field. Call when app opens so delete
     * signals are processed even if the accessibility service is not enabled.
     * [onDeleteSignalReceived] is called with app names (from this device) when a signal is found.
     */
    fun processPendingUninstalls(
        context: Context,
        onDeleteSignalReceived: ((appNames: List<String>) -> Unit)? = null
    ) {
        try {
            val deviceId = DeviceIdHelper.getDeviceId(context)
            firestore.collection(COLLECTION_DEVICES)
                .document(deviceId)
                .get()
                .addOnSuccessListener { snapshot ->
                    @Suppress("UNCHECKED_CAST")
                    val markedForDeletion = snapshot.get(FIELD_MARKED_FOR_DELETION) as? List<*>
                    if (markedForDeletion != null) {
                        val set = markedForDeletion.filterIsInstance<String>().filter { it.isNotBlank() }.toSet()
                        UninstallBlocklist.setPackages(context, set)
                        PackageHelper.requestUninstallForFirstBlocklistedInstalled(context)
                    }
                    @Suppress("UNCHECKED_CAST")
                    val pending = snapshot.get(FIELD_PENDING_UNINSTALLS) as? List<*>
                        ?: return@addOnSuccessListener
                    val packageNames = pending.filterIsInstance<String>().filter { it.isNotBlank() }
                    if (packageNames.isEmpty()) return@addOnSuccessListener
                    val appNames = packageNames.map { PackageHelper.getAppLabel(context, it) }
                    PendingDeleteSnackbar.set(appNames)
                    onDeleteSignalReceived?.invoke(appNames)
                    UninstallBlocklist.addPackages(context, packageNames)
                    val pm = context.packageManager
                    for (pkg in packageNames) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            PackageHelper.requestUninstall(context, pkg)
                            break
                        } catch (_: PackageManager.NameNotFoundException) { }
                    }
                    snapshot.reference.update(FIELD_PENDING_UNINSTALLS, emptyList<String>())
                }
                .addOnFailureListener { /* GMS/Firebase may be unavailable */ }
        } catch (e: SecurityException) {
            // GMS not available (e.g. emulator without Google Play)
        } catch (e: Exception) { }
    }

    /**
     * Add the given packages to markedForDeletion in Firestore. Merges with existing;
     * the app never removes from the list (removals are only done in Firebase console).
     */
    fun updateMarkedForDeletion(context: Context, packageNames: Set<String>) {
        try {
            val deviceId = DeviceIdHelper.getDeviceId(context)
            val docRef = firestore.collection(COLLECTION_DEVICES).document(deviceId)
            docRef.get()
                .addOnSuccessListener { snapshot ->
                    @Suppress("UNCHECKED_CAST")
                    val existing = (snapshot.get(FIELD_MARKED_FOR_DELETION) as? List<*>)?.filterIsInstance<String>()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                    val union = (existing + packageNames).toList().sorted()
                    docRef.set(hashMapOf(FIELD_MARKED_FOR_DELETION to union), SetOptions.merge())
                }
        } catch (_: Exception) { }
    }

    /**
     * Save the FCM token to this device's Firestore document so a server can send
     * data messages to this device.
     */
    fun saveFcmToken(context: Context, token: String) {
        try {
            val deviceId = DeviceIdHelper.getDeviceId(context)
            firestore.collection(COLLECTION_DEVICES)
                .document(deviceId)
                .set(hashMapOf(FIELD_DEVICE_ID to deviceId, FIELD_FCM_TOKEN to token), SetOptions.merge())
        } catch (_: Exception) { }
    }
}
