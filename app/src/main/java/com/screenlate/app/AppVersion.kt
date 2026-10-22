package com.screenlate.app

object AppVersion {
    fun label(versionName: String, versionCode: Int): String = "v$versionName ($versionCode)"

    val current: String
        get() = label(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
}
