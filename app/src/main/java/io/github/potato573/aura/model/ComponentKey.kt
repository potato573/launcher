package io.github.potato573.aura.model

/**
 * Stable identity of a launchable activity for a given user profile.
 *
 * [userSerial] is the value of `UserManager.getSerialNumberForUser`, which (unlike a
 * UserHandle id) is stable across reboots, so it is safe to persist.
 */
data class ComponentKey(
    val packageName: String,
    val className: String,
    val userSerial: Long = 0L,
) : Comparable<ComponentKey> {

    fun serialize(): String = "$packageName/$className#$userSerial"

    override fun compareTo(other: ComponentKey): Int = compareValuesBy(
        this, other, { it.packageName }, { it.className }, { it.userSerial },
    )

    companion object {
        /** Parses the output of [serialize]. Returns null for malformed input instead of throwing. */
        fun parse(value: String?): ComponentKey? {
            if (value.isNullOrBlank()) return null
            val hash = value.lastIndexOf('#')
            val body = if (hash >= 0) value.substring(0, hash) else value
            val serial = if (hash >= 0) value.substring(hash + 1).toLongOrNull() ?: return null else 0L
            val slash = body.indexOf('/')
            if (slash <= 0 || slash == body.length - 1) return null
            val pkg = body.substring(0, slash)
            var cls = body.substring(slash + 1)
            // Support the short form "pkg/.Activity" used by ComponentName.flattenToShortString().
            if (cls.startsWith('.')) cls = pkg + cls
            return ComponentKey(pkg, cls, serial)
        }
    }
}
