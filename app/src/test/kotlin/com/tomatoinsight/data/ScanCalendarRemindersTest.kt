package com.tomatoinsight.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanCalendarRemindersTest {
    @Test
    fun eachTileBecomesDueSevenDaysAfterItsOwnScan() {
        val onNovember8 = ScanCalendar(
            enabled = true,
            anchor = "2026-11-01",
            selected = "2026-11-08"
        )
        assertEquals(5, reminderWeekForPlant(onNovember8, 4, 0, "2026-11-01"))
        assertNull(reminderWeekForPlant(onNovember8, 4, 0, "2026-11-04"))

        val onNovember11 = onNovember8.copy(selected = "2026-11-11")
        assertEquals(5, reminderWeekForPlant(onNovember11, 4, 0, "2026-11-04"))
    }

    @Test
    fun timingHoldsAcrossWeeksMonthsAndYears() {
        for (anchor in listOf("2026-01-01", "2026-11-01", "2026-12-28")) {
            for (offset in listOf(0L, 1L, 3L, 6L, 7L, 10L, 25L, 55L)) {
                val scanDay = LocalDate.parse(anchor).plusDays(offset)
                val latestWeek = 4 + (offset / 7).toInt()
                val before = ScanCalendar(
                    enabled = true,
                    anchor = anchor,
                    selected = scanDay.plusDays(6).toString()
                )
                assertNull(
                    "$anchor: $scanDay should not be due after six days",
                    reminderWeekForPlant(before, latestWeek, 0, scanDay.toString())
                )

                val due = before.copy(selected = scanDay.plusDays(7).toString())
                assertEquals(
                    "$anchor: $scanDay should be due after seven days",
                    latestWeek + 1,
                    reminderWeekForPlant(due, latestWeek, 0, scanDay.toString())
                )
            }
        }

        val lastWeek = ScanCalendar(
            enabled = true,
            anchor = "2026-11-01",
            selected = "2027-01-03"
        )
        assertNull(reminderWeekForPlant(lastWeek, 12, 0, "2026-12-27"))
    }

    @Test
    fun legacyTimestampIsUsedAndMissingDateCannotCreateReminder() {
        val calendar = ScanCalendar(
            enabled = true,
            anchor = "2026-11-01",
            selected = "2026-11-08"
        )
        assertNull(reminderWeekForPlant(calendar, 4, parseScanDay("2026-11-04")!!))
        assertEquals(5, reminderWeekForPlant(calendar, 4, parseScanDay("2026-11-01")!!))
        assertNull(reminderWeekForPlant(calendar, 4, 0))
    }
}
