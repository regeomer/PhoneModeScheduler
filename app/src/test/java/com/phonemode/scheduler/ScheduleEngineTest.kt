package com.phonemode.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleEngineTest {

    private val night = ScheduleEngine.defaultRules()
    private fun at(s: String) = LocalDateTime.parse(s)

    @Test fun defaultRuleIsVibrateFrom23To10() {
        val r = night.single()
        assertEquals(23 * 60, r.startMinute)
        assertEquals(10 * 60, r.endMinute)
        assertEquals(RingMode.VIBRATE, r.mode)
        assertEquals(11 * 60, r.durationMinutes)
    }

    @Test fun vibrateDuringNightSoundDuringDay() {
        val d = RingMode.NORMAL
        assertEquals(RingMode.NORMAL, ScheduleEngine.desiredMode(night, at("2026-11-02T22:59:59"), d))
        assertEquals(RingMode.VIBRATE, ScheduleEngine.desiredMode(night, at("2026-11-02T23:00"), d))
        assertEquals(RingMode.VIBRATE, ScheduleEngine.desiredMode(night, at("2026-11-03T00:00"), d))
        assertEquals(RingMode.VIBRATE, ScheduleEngine.desiredMode(night, at("2026-11-03T09:59:59"), d))
        assertEquals(RingMode.NORMAL, ScheduleEngine.desiredMode(night, at("2026-11-03T10:00"), d))
        assertEquals(RingMode.NORMAL, ScheduleEngine.desiredMode(night, at("2026-11-03T15:00"), d))
    }

    @Test fun nextTransitions() {
        assertEquals(at("2026-11-02T23:00"), ScheduleEngine.nextTransition(night, at("2026-11-02T12:00")))
        assertEquals(at("2026-11-03T10:00"), ScheduleEngine.nextTransition(night, at("2026-11-02T23:00")))
        assertEquals(at("2026-11-03T10:00"), ScheduleEngine.nextTransition(night, at("2026-11-03T03:00")))
        assertEquals(at("2026-11-03T23:00"), ScheduleEngine.nextTransition(night, at("2026-11-03T10:00")))
    }

    @Test fun nextModeChange() {
        val c = ScheduleEngine.nextModeChange(night, at("2026-11-03T03:00"), RingMode.NORMAL)
        assertEquals(at("2026-11-03T10:00") to RingMode.NORMAL, c)
    }

    @Test fun overnightRuleRespectsStartDay() {
        // Friday-night-only rule; Saturday 02:00 counts, Saturday 23:30 doesn't
        val fri = listOf(Rule("f", "Fri", 22 * 60, 8 * 60, RingMode.SILENT, setOf(DayOfWeek.FRIDAY)))
        // 2026-11-06 is a Friday
        assertEquals(RingMode.SILENT, ScheduleEngine.desiredMode(fri, at("2026-11-07T02:00"), RingMode.NORMAL))
        assertEquals(RingMode.NORMAL, ScheduleEngine.desiredMode(fri, at("2026-11-07T23:30"), RingMode.NORMAL))
        assertEquals(at("2026-11-13T22:00"), ScheduleEngine.nextTransition(fri, at("2026-11-07T08:00")))
    }

    @Test fun overlappingRulesLatestStartWins() {
        val rules = night + Rule("m", "Meeting", 9 * 60, 9 * 60 + 30, RingMode.SILENT)
        assertEquals(RingMode.SILENT, ScheduleEngine.desiredMode(rules, at("2026-11-03T09:15"), RingMode.NORMAL))
        assertEquals(RingMode.VIBRATE, ScheduleEngine.desiredMode(rules, at("2026-11-03T09:45"), RingMode.NORMAL))
    }

    @Test fun disabledRulesIgnored() {
        val off = night.map { it.copy(enabled = false) }
        assertEquals(RingMode.NORMAL, ScheduleEngine.desiredMode(off, at("2026-11-03T02:00"), RingMode.NORMAL))
        assertNull(ScheduleEngine.nextTransition(off, at("2026-11-03T02:00")))
    }

    @Test fun equalStartEndMeansAllDay() {
        val all = listOf(Rule("a", "All", 8 * 60, 8 * 60, RingMode.SILENT, setOf(DayOfWeek.MONDAY)))
        // 2026-11-02 is a Monday
        assertEquals(RingMode.SILENT, ScheduleEngine.desiredMode(all, at("2026-11-03T07:59"), RingMode.NORMAL))
        assertEquals(RingMode.NORMAL, ScheduleEngine.desiredMode(all, at("2026-11-03T08:00"), RingMode.NORMAL))
    }
}
