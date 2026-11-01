package com.phonemode.scheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

/** Saves rules and settings in SharedPreferences as JSON. */
class RuleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("phone_mode_scheduler", Context.MODE_PRIVATE)

    var automationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Mode used when no rule is active. */
    var defaultMode: RingMode
        get() = runCatching { RingMode.valueOf(prefs.getString(KEY_DEFAULT_MODE, null)!!) }
            .getOrDefault(RingMode.NORMAL)
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODE, value.name).apply()

    fun hasSavedRules(): Boolean = prefs.contains(KEY_RULES)

    fun loadRules(): List<Rule> {
        val raw = prefs.getString(KEY_RULES, null)
        if (raw == null) {
            // First launch: install the built-in 23:00-10:00 vibrate rule.
            val defaults = ScheduleEngine.defaultRules()
            saveRules(defaults)
            return defaults
        }
        return runCatching { decode(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    fun saveRules(rules: List<Rule>) {
        prefs.edit().putString(KEY_RULES, encode(rules).toString()).apply()
    }

    fun upsert(rule: Rule) {
        val rules = loadRules().toMutableList()
        val i = rules.indexOfFirst { it.id == rule.id }
        if (i >= 0) rules[i] = rule else rules += rule
        saveRules(rules)
    }

    fun delete(id: String) = saveRules(loadRules().filterNot { it.id == id })

    private fun encode(rules: List<Rule>) = JSONArray().apply {
        rules.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("start", r.startMinute)
                put("end", r.endMinute)
                put("mode", r.mode.name)
                put("days", JSONArray(r.days.map { it.value }.sorted()))
                put("enabled", r.enabled)
            })
        }
    }

    private fun decode(arr: JSONArray): List<Rule> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val days = o.getJSONArray("days")
        Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Rule"),
            startMinute = o.getInt("start"),
            endMinute = o.getInt("end"),
            mode = RingMode.valueOf(o.getString("mode")),
            days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
            enabled = o.optBoolean("enabled", true),
        )
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEFAULT_MODE = "default_mode"
        private const val KEY_RULES = "rules"
    }
}
