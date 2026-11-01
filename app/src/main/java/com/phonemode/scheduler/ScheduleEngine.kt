package com.phonemode.scheduler

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The phone ringer modes the app can switch between. */
enum class RingMode(val label: String, val priority: Int) {
    NORMAL("Sound", 0),
    VIBRATE("Vibrate", 1),
    SILENT("Silent", 2),
}

/**
 * One timed rule, e.g. "Night: Vibrate from 23:00 to 10:00".
 *
 * Times are minutes after midnight (0..1439). If [endMinute] <= [startMinute]
 * the rule runs past midnight into the next day. Equal values mean all day (24h).
 * [days] holds the days the rule *starts* on.
 */
data class Rule(
    val id: String,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val mode: RingMode,
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val enabled: Boolean = true,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range" }
    }

    /** Length of one occurrence in minutes (1..1440). */
    val durationMinutes: Int
        get() {
            val d = (endMinute - startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return if (d == 0) MINUTES_PER_DAY else d
        }

    val crossesMidnight: Boolean get() = startMinute + durationMinutes > MINUTES_PER_DAY

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** A concrete occurrence of a rule: [start, end). */
data class Occurrence(val rule: Rule, val start: LocalDateTime, val end: LocalDateTime) {
    operator fun contains(t: LocalDateTime): Boolean = !t.isBefore(start) && t.isBefore(end)
}

object ScheduleEngine {

    /** All occurrences of enabled rules that start between [from] and [to] (both dates inclusive). */
    fun occurrences(rules: List<Rule>, from: LocalDate, to: LocalDate): List<Occurrence> {
        val out = ArrayList<Occurrence>()
        var date = from
        while (!date.isAfter(to)) {
            for (rule in rules) {
                if (!rule.enabled || date.dayOfWeek !in rule.days) continue
                val start = date.atTime(LocalTime.of(rule.startMinute / 60, rule.startMinute % 60))
                out += Occurrence(rule, start, start.plusMinutes(rule.durationMinutes.toLong()))
            }
            date = date.plusDays(1)
        }
        return out
    }

    /**
     * The occurrence in effect at [now], or null if no rule applies.
     * When rules overlap, the one that started most recently wins; ties go to the
     * quieter mode (Silent > Vibrate > Sound).
     */
    fun activeOccurrence(rules: List<Rule>, now: LocalDateTime): Occurrence? {
        val today = now.toLocalDate()
        return occurrences(rules, today.minusDays(1), today)
            .filter { now in it }
            .maxWithOrNull(compareBy<Occurrence>({ it.start }, { it.rule.mode.priority }))
    }

    /** The mode the phone should be in at [now]. */
    fun desiredMode(rules: List<Rule>, now: LocalDateTime, defaultMode: RingMode): RingMode =
        activeOccurrence(rules, now)?.rule?.mode ?: defaultMode

    /**
     * The next moment strictly after [now] at which a rule starts or ends,
     * i.e. when the desired mode may change. Null if no rule is enabled.
     */
    fun nextTransition(rules: List<Rule>, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        return occurrences(rules, today.minusDays(1), today.plusDays(8))
            .flatMap { listOf(it.start, it.end) }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

    /** The next transition at which the desired mode actually changes, with the new mode. */
    fun nextModeChange(
        rules: List<Rule>,
        now: LocalDateTime,
        defaultMode: RingMode,
    ): Pair<LocalDateTime, RingMode>? {
        val current = desiredMode(rules, now, defaultMode)
        var t = now
        repeat(64) {
            val next = nextTransition(rules, t) ?: return null
            val mode = desiredMode(rules, next, defaultMode)
            if (mode != current) return next to mode
            t = next
        }
        return null
    }

    /** The rule that ships with the app: vibrate every night from 23:00 to 10:00. */
    fun defaultRules(): List<Rule> = listOf(
        Rule(
            id = "default-night",
            name = "Night",
            startMinute = 23 * 60,
            endMinute = 10 * 60,
            mode = RingMode.VIBRATE,
        )
    )
}

fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
