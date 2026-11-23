package com.tomatoinsight.data

internal val nameSuffixChoices = listOf("", "Jr.", "Sr.", "II", "III", "IV", "V")

internal fun normalizeNameSuffix(value: String?): String = when (value.orEmpty().trim().trimEnd(',').lowercase()) {
    "jr", "jr.", "junior" -> "Jr."
    "sr", "sr.", "senior" -> "Sr."
    "ii", "2nd" -> "II"
    "iii", "3rd" -> "III"
    "iv", "4th" -> "IV"
    "v", "5th" -> "V"
    else -> ""
}

data class ProfileNameParts(
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val nameSuffix: String = ""
) {
    val displayName: String
        get() = listOf(firstName, middleName, lastName, normalizeNameSuffix(nameSuffix))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}

fun parseProfileName(displayName: String?): ProfileNameParts {
    if (displayName.isNullOrBlank()) return ProfileNameParts()

    val parts = displayName.trim().split(Regex("\\s+"))
    val suffix = if (parts.size >= 3) normalizeNameSuffix(parts.last()) else ""
    val core = if (suffix.isEmpty()) parts else parts.dropLast(1)
    val hasInitial = core.size >= 3 && core[1].length == 1
    return ProfileNameParts(
        firstName = core.first(),
        middleName = if (hasInitial) core[1].uppercase() else "",
        lastName = core.drop(if (hasInitial) 2 else 1).joinToString(" "),
        nameSuffix = suffix
    )
}

/** Prefer non-blank cloud fields without wiping filled local profile values. */
fun mergeFarmerProfiles(local: FarmerProfile, cloud: FarmerProfile): FarmerProfile {
    return FarmerProfile(
        firstName = cloud.firstName.trim().ifBlank { local.firstName.trim() },
        middleInitial = cloud.middleInitial.trim().ifBlank { local.middleInitial.trim() }
            .uppercase()
            .take(1),
        lastName = cloud.lastName.trim().ifBlank { local.lastName.trim() },
        nameSuffix = cloud.nameSuffix ?: local.nameSuffix,
        farmName = cloud.farmName.trim().ifBlank { local.farmName.trim() },
        email = cloud.email.trim().ifBlank { local.email.trim() },
        photo = cloud.photo ?: local.photo
    )
}

/** Fill blank name/email from Firebase Auth when Firestore profile is still empty. */
fun FarmerProfile.withAuthFallback(displayName: String?, email: String?): FarmerProfile {
    val parsed = parseProfileName(displayName)
    return copy(
        firstName = firstName.trim().ifBlank { parsed.firstName },
        middleInitial = middleInitial.trim().ifBlank {
            parsed.middleName.trim().uppercase().take(1)
        },
        lastName = lastName.trim().ifBlank { parsed.lastName },
        nameSuffix = nameSuffix ?: parsed.nameSuffix.takeIf { it.isNotEmpty() },
        email = this.email.trim().ifBlank { email?.trim().orEmpty() }
    )
}
