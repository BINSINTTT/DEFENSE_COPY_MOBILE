package com.tomatoinsight.util

private val GMAIL_ADDRESS = Regex("^[\\w.+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)

fun isAllowedGmail(email: String): Boolean =
    GMAIL_ADDRESS.matches(email.trim())

fun emailValidationError(email: String): String? {
    val trimmed = email.trim()
    if (trimmed.isBlank()) return "Please enter your email"
    if (!isAllowedGmail(trimmed)) return "Email must end with @gmail.com"
    return null
}
