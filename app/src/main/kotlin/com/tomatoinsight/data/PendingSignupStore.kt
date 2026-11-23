package com.tomatoinsight.data

import android.content.Context

data class PendingSignup(
    val email: String,
    val password: String,
    val status: String,
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val resendUntilMs: Long = 0L,
    val nameSuffix: String = ""
) {
    val isCreating: Boolean get() = status == STATUS_CREATING
    val isAwaitingVerification: Boolean get() = status == STATUS_AWAITING_VERIFICATION

    companion object {
        const val STATUS_CREATING = "creating"
        const val STATUS_AWAITING_VERIFICATION = "awaiting_verification"
    }
}

/**
 * Survives process death / leaving auth screens until Gmail verification succeeds.
 * Mirrors the web app's tomatoInsight.pendingSignup localStorage gate.
 */
object PendingSignupStore {
    private const val PREFS = "tomatoinsight_pending_signup"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_STATUS = "status"
    private const val KEY_FIRST = "first_name"
    private const val KEY_MIDDLE = "middle_name"
    private const val KEY_SUFFIX = "name_suffix"
    private const val KEY_LAST = "last_name"
    private const val KEY_STARTED = "started_at"
    private const val KEY_RESEND_UNTIL = "resend_until"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun read(context: Context): PendingSignup? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_EMAIL, "").orEmpty().trim()
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        val status = prefs.getString(KEY_STATUS, "").orEmpty()
        if (email.isBlank() || password.isBlank()) return null
        if (status != PendingSignup.STATUS_CREATING &&
            status != PendingSignup.STATUS_AWAITING_VERIFICATION
        ) {
            return null
        }
        val startedAt = prefs.getLong(KEY_STARTED, 0L)
        if (startedAt > 0L && System.currentTimeMillis() - startedAt > MAX_AGE_MS) {
            clear(context)
            return null
        }
        return PendingSignup(
            email = email,
            password = password,
            status = status,
            firstName = prefs.getString(KEY_FIRST, "").orEmpty(),
            middleName = prefs.getString(KEY_MIDDLE, "").orEmpty(),
            nameSuffix = normalizeNameSuffix(prefs.getString(KEY_SUFFIX, "")),
            lastName = prefs.getString(KEY_LAST, "").orEmpty(),
            startedAt = startedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            resendUntilMs = prefs.getLong(KEY_RESEND_UNTIL, 0L)
        )
    }

    fun hasPending(context: Context): Boolean = read(context) != null

    fun save(context: Context, pending: PendingSignup) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, pending.email.trim())
            .putString(KEY_PASSWORD, pending.password)
            .putString(KEY_STATUS, pending.status)
            .putString(KEY_FIRST, pending.firstName)
            .putString(KEY_MIDDLE, pending.middleName)
            .putString(KEY_SUFFIX, pending.nameSuffix)
            .putString(KEY_LAST, pending.lastName)
            .putLong(KEY_STARTED, pending.startedAt)
            .putLong(KEY_RESEND_UNTIL, pending.resendUntilMs)
            .apply()
    }

    fun updateStatus(context: Context, status: String) {
        val current = read(context) ?: return
        save(context, current.copy(status = status))
    }

    fun markResendCooldown(context: Context, seconds: Int = 60) {
        val current = read(context) ?: return
        save(
            context,
            current.copy(resendUntilMs = System.currentTimeMillis() + seconds * 1000L)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
