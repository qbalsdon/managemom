package com.balsdon.managemom

/**
 * Holds app names from a delete signal so MainActivity can show a Snackbar
 * when the signal was received in the background (e.g. by the accessibility service).
 */
object PendingDeleteSnackbar {

    @Volatile
    var appNames: List<String>? = null
        private set

    /** Optional: called when a new signal is set (e.g. from FCM). Set from MainActivity; callbacks run on main thread. */
    var onNewSignal: ((List<String>) -> Unit)? = null

    fun set(appNames: List<String>) {
        this.appNames = if (appNames.isEmpty()) null else appNames
    }

    /** Call when a new signal is set and you want to notify (e.g. show Snackbar). Runs callback on main thread. */
    fun setAndNotify(appNames: List<String>, mainHandler: android.os.Handler) {
        val list = if (appNames.isEmpty()) null else appNames
        this.appNames = list
        if (list != null) {
            mainHandler.post { onNewSignal?.invoke(list) }
        }
    }

    /** Returns and clears the pending list. Call on main thread from MainActivity. */
    fun getAndClear(): List<String>? {
        val pending = appNames
        appNames = null
        return pending
    }
}
