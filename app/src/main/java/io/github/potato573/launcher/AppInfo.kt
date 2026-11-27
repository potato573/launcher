package io.github.potato573.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable,
) {
    /** Kunci unik yang disimpan di pengaturan: "paket/kelas". */
    val key: String get() = "$packageName/$className"
}
