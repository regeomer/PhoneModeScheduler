package com.phonemode.scheduler

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var store: RuleStore
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RuleStore(this)
        val firstRun = !store.hasSavedRules()
        store.loadRules() // adds the default 23:00-10:00 vibrate rule on first run

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        })
        // Set the right mode right away the first time the app opens.
        if (firstRun) ModeScheduler.sync(this, applyNow = true)
    }

    override fun onResume() {
        super.onResume()
        // Set the alarm again, but don't undo a manual change by the user.
        ModeScheduler.sync(this, applyNow = false)
        render()
    }

    /** Saves a change, sets the mode, and redraws the screen. */
    private fun changed() {
        ModeScheduler.sync(this, applyNow = true)
        render()
    }

    // ---------------------------------------------------------------- UI

    private fun render() {
        root.removeAllViews()
        val rules = store.loadRules()
        val enabled = store.automationEnabled

        root.addView(text("Phone Mode Scheduler", 24f, bold = true))
        root.addView(text("Sets Sound / Vibrate / Silent based on the time.", 14f).apply {
            alpha = 0.7f; setPadding(0, dp(4), 0, dp(16))
        })

        // Status card
        root.addView(card().apply {
            val now = LocalDateTime.now()
            val active = ScheduleEngine.activeOccurrence(rules, now)
            addView(text("Phone is in ${RingerController.currentMode(this@MainActivity).label} mode", 18f, bold = true))
            if (!enabled) {
                addView(text("Automatic switching is off.", 14f))
            } else {
                val scheduled = active?.rule?.mode ?: store.defaultMode
                val why = active?.let { "rule \"${it.rule.name}\" until ${it.end.format(HM)}" } ?: "no rule active"
                addView(text("Rules set: ${scheduled.label} ($why)", 14f))
                ScheduleEngine.nextModeChange(rules, now, store.defaultMode)?.let { (at, mode) ->
                    addView(text("Next: ${mode.label} at ${at.format(DAY_HM)}", 14f))
                }
            }
        })

        root.addView(Switch(this).apply {
            text = "Automatic switching"
            textSize = 16f
            isChecked = enabled
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, checked -> store.automationEnabled = checked; changed() }
        })

        permissionWarnings(rules)

        // Default mode
        root.addView(section("When no rule is active"))
        root.addView(modePicker(store.defaultMode) { store.defaultMode = it; changed() })

        // Rules
        root.addView(section("Rules"))
        if (rules.isEmpty()) root.addView(text("No rules yet.", 14f).apply { alpha = 0.7f })
        rules.forEach { root.addView(ruleRow(it)) }
        root.addView(Button(this).apply {
            text = "+ Add rule"
            setOnClickListener { editRule(null) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(text(
            "If you change the mode yourself, your choice stays until the next rule starts or ends. " +
                "If rules overlap, the one that started last wins.", 12f,
        ).apply { alpha = 0.6f; setPadding(0, dp(16), 0, 0) })
    }

    private fun permissionWarnings(rules: List<Rule>) {
        val needsSilent = rules.any { it.enabled && it.mode == RingMode.SILENT } || store.defaultMode == RingMode.SILENT
        if (!RingerController.hasDndAccess(this)) {
            root.addView(warning(
                if (needsSilent) "Silent mode needs Do Not Disturb access. Until you allow it, Silent rules use Vibrate."
                else "Optional: allow Do Not Disturb access so the app can use Silent mode and turn sound back on reliably.",
                "Allow access",
            ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AlarmManager::class.java)!!.canScheduleExactAlarms()
        ) {
            root.addView(warning(
                "Allow \"Alarms & reminders\" so modes switch at the exact minute. Without it, Android may switch a few minutes late.",
                "Allow",
            ) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            })
        }
    }

    private fun ruleRow(rule: Rule): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(rule.name, 16f, bold = true))
            val next = if (rule.crossesMidnight) " (next day)" else ""
            addView(text("${formatMinute(rule.startMinute)} – ${formatMinute(rule.endMinute)}$next  ·  ${rule.mode.label}", 14f))
            addView(text(daysLabel(rule.days), 12f).apply { alpha = 0.7f })
        }
        addView(info, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(Switch(context).apply {
            isChecked = rule.enabled
            setOnCheckedChangeListener { _, c -> store.upsert(rule.copy(enabled = c)); changed() }
        })
        alpha = if (rule.enabled) 1f else 0.55f
        setOnClickListener { editRule(rule) }
    }

    // ---------------------------------------------------------------- Editor

    private fun editRule(existing: Rule?) {
        var start = existing?.startMinute ?: (22 * 60)
        var end = existing?.endMinute ?: (7 * 60)
        var mode = existing?.mode ?: RingMode.VIBRATE

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            hint = "Name (e.g. Work, Night)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(existing?.name ?: "")
        }
        form.addView(name)

        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = Button(this)
        val endBtn = Button(this)
        fun label() {
            startBtn.text = "From ${formatMinute(start)}"
            endBtn.text = "To ${formatMinute(end)}"
        }
        label()
        startBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> start = h * 60 + m; label() }, start / 60, start % 60, true).show()
        }
        endBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> end = h * 60 + m; label() }, end / 60, end % 60, true).show()
        }
        times.addView(startBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        times.addView(endBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        form.addView(times)

        form.addView(section("Mode"))
        form.addView(modePicker(mode) { mode = it })

        form.addView(section("Starts on"))
        val dayBoxes = DayOfWeek.values().map { d ->
            CheckBox(this).apply {
                text = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                isChecked = existing?.days?.contains(d) ?: true
            }
        }
        dayBoxes.chunked(4).forEach { row ->
            form.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
                repeat(4 - row.size) { addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
            })
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New rule" else "Edit rule")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ -> store.delete(existing.id); changed() }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val days = DayOfWeek.values().filterIndexed { i, _ -> dayBoxes[i].isChecked }.toSet()
                when {
                    days.isEmpty() -> name.error = "Pick at least one day"
                    else -> {
                        store.upsert(
                            Rule(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.text.toString().trim().ifEmpty { "${mode.label} rule" },
                                startMinute = start,
                                endMinute = end,
                                mode = mode,
                                days = days,
                                enabled = existing?.enabled ?: true,
                            )
                        )
                        dialog.dismiss()
                        changed()
                    }
                }
            }
        }
        dialog.show()
    }

    // ---------------------------------------------------------------- Helpers

    private fun modePicker(selected: RingMode, onPick: (RingMode) -> Unit): View = RadioGroup(this).apply {
        orientation = RadioGroup.HORIZONTAL
        RingMode.values().forEach { m ->
            addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = m.label
                isChecked = m == selected
                setOnCheckedChangeListener { _, c -> if (c) onPick(m) }
            }, RadioGroup.LayoutParams(0, WRAP_CONTENT, 1f))
        }
    }

    private fun daysLabel(days: Set<DayOfWeek>): String = when {
        days.size == 7 -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == WEEKEND -> "Weekends"
        else -> DayOfWeek.values().filter { it in days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(title: String) = text(title, 15f, bold = true).apply {
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.card)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
    }

    private fun warning(message: String, action: String, onClick: () -> Unit) = card().apply {
        setBackgroundResource(R.drawable.warning)
        addView(text(message, 14f))
        addView(Button(context).apply { text = action; setOnClickListener { onClick() } },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.END })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val HM = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY_HM = DateTimeFormatter.ofPattern("EEE HH:mm")
        private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
