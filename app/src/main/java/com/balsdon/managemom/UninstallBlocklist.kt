package com.balsdon.managemom

import android.content.Context
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val FILENAME = "uninstall_blocklist.txt"

/**
 * Persists package names that should be removed when present (or on reinstall).
 * Stored as a local file, one package name per line.
 */
object UninstallBlocklist {

    private val lock = ReentrantReadWriteLock()

    private fun file(context: Context): File =
        File(context.filesDir, FILENAME)

    /** Add package names to the blocklist (appends to file, no duplicates). */
    fun addPackages(context: Context, packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        val toAdd = packageNames.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (toAdd.isEmpty()) return
        lock.write {
            val f = file(context)
            val existing = if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotBlank() }.toSet() else emptySet()
            f.writeText((existing + toAdd).sorted().joinToString("\n"))
        }
    }

    /** Return the set of blocklisted package names. */
    fun getPackages(context: Context): Set<String> = lock.read {
        val f = file(context)
        if (!f.exists()) return emptySet()
        f.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /** True if [packageName] is in the blocklist. */
    fun contains(context: Context, packageName: String): Boolean =
        getPackages(context).contains(packageName)
}
