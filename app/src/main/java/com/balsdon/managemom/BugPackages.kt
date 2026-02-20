package com.balsdon.managemom

import android.content.Context
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val FILENAME = "bug_packages.txt"

/**
 * Persists package names marked as "bug" (shown at top of list).
 * One package name per line.
 */
object BugPackages {

    private val lock = ReentrantReadWriteLock()

    private fun file(context: Context): File =
        File(context.filesDir, FILENAME)

    fun contains(context: Context, packageName: String): Boolean =
        getPackages(context).contains(packageName)

    fun getPackages(context: Context): Set<String> = lock.read {
        val f = file(context)
        if (!f.exists()) return emptySet()
        f.readLines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    /** Toggle bug state for [packageName]. Returns true if now marked as bug. */
    fun toggle(context: Context, packageName: String): Boolean {
        val pkg = packageName.trim().ifBlank { return false }
        lock.write {
            val f = file(context)
            val existing = if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotBlank() }.toMutableSet() else mutableSetOf()
            val isNowBug = if (pkg in existing) {
                existing.remove(pkg)
                false
            } else {
                existing.add(pkg)
                true
            }
            f.writeText(existing.sorted().joinToString("\n"))
            return isNowBug
        }
    }
}
