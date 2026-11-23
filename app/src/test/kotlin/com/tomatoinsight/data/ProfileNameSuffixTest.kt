package com.tomatoinsight.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNameSuffixTest {
    @Test
    fun parsesWebsiteSuffixesAndPreservesCompoundSurnames() {
        for ((input, expected) in listOf("junior" to "Jr.", "SR.," to "Sr.", "2nd" to "II",
            "iii" to "III", "4th" to "IV", "5th" to "V")) {
            val parts = parseProfileName("Ana m De Leon $input")
            assertEquals(ProfileNameParts("Ana", "M", "De Leon", expected), parts)
            assertEquals("Ana M De Leon $expected", parts.displayName)
        }
        assertEquals(ProfileNameParts("Ana", "", "De Leon"), parseProfileName("Ana De Leon"))
        assertEquals(ProfileNameParts("Ana", "", "Junior"), parseProfileName("Ana Junior"))
    }

    @Test
    fun clearingSuffixOnWebsiteOverridesCachedAndAuthNames() {
        val cached = FarmerProfile(firstName = "Ana", lastName = "Cruz", nameSuffix = "III")
        val cleared = mergeFarmerProfiles(cached, FarmerProfile(nameSuffix = ""))
        assertEquals("Ana Cruz", cleared.withAuthFallback("Ana Cruz III", null).displayName)
        assertEquals("Ana Cruz III", FarmerProfile().withAuthFallback("Ana Cruz 3rd", null).displayName)
    }
}
