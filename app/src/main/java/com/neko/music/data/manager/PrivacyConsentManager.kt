package com.neko.music.data.manager

import android.content.Context

object PrivacyConsentManager {
    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT = "accepted_at"

    const val CURRENT_VERSION = 20260621

    fun hasAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION
    }

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }
}
