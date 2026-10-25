package com.example.smartringer

import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {
    private var startHour = 22
    private var startMinute = 0
    private var endHour = 7
    private var endMinute = 0
    private var selectedMode = AudioManager.RINGER_MODE_SILENT
    private val dayBoxes = mutableListOf<CheckBox>()
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var modeLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("schedule", Context.MODE_PRIVATE)
        startHour = prefs.getInt("startHour", 22); startMinute = prefs.getInt("startMinute", 0)
        endHour = prefs.getInt("endHour", 7); endMinute = prefs.getInt("endMinute", 0)
        selectedMode = prefs.getInt("mode", AudioManager.RINGER_MODE_SILENT)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.layoutParams = ViewGroup.LayoutParams(-1, -1)
        setContentView(root)

        content.addView(TextView(this).apply { text = "Smart Ringer Scheduler"; textSize = 25f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 20) })
        content.addView(TextView(this).apply { text = "દરરોજના સમય અને દિવસો પસંદ કરો"; textSize = 17f; setPadding(0, 0, 0, 16) })
        startLabel = TextView(this); endLabel = TextView(this); modeLabel = TextView(this)
        addButton(content, "શરૂઆતનો સમય પસંદ કરો", "${fmt(startHour, startMinute)}") { chooseTime(true) }
        content.addView(startLabel.apply { text = "શરૂઆત: ${fmt(startHour, startMinute)}"; textSize = 16f; setPadding(0, 6, 0, 12) })
        addButton(content, "સમાપ્ત થવાનો સમય પસંદ કરો", "${fmt(endHour, endMinute)}") { chooseTime(false) }
        content.addView(endLabel.apply { text = "સમાપ્ત: ${fmt(endHour, endMinute)}"; textSize = 16f; setPadding(0, 6, 0, 12) })
        content.addView(TextView(this).apply { text = "શરૂઆતના સમયે કયો મોડ?"; textSize = 17f; setPadding(0, 12, 0, 6) })
        addButton(content, "મોડ બદલો", modeName(selectedMode)) { cycleMode() }
        content.addView(modeLabel.apply { text = "પસંદ કરેલો મોડ: ${modeName(selectedMode)}"; textSize = 16f; setPadding(0, 6, 0, 12) })
        content.addView(TextView(this).apply { text = "કયા દિવસોમાં લાગુ કરવો?"; textSize = 17f; setPadding(0, 12, 0, 4) })
        val days = listOf("ஞாயிறு / રવિવાર", "સોમવાર", "મંગળવાર", "બુધવાર", "ગુરુવાર", "શુક્રવાર", "શનિવાર")
        val savedDays = prefs.getStringSet("days", (1..5).map { it.toString() }.toSet()) ?: emptySet()
        days.forEachIndexed { index, day ->
            val box = CheckBox(this).apply { text = day; isChecked = savedDays.contains(index.toString()) }
            dayBoxes.add(box); content.addView(box)
        }
        addButton(content, "પરવાનગીઓ ખોલો", "Settings") { openPermissions() }
        addButton(content, "સમયપત્રક સાચવો અને ચાલુ કરો", "ચાલુ કરો") { saveAndSchedule() }
        addButton(content, "સમયપત્રક બંધ કરો", "બંધ કરો") { Scheduler.cancelAll(this); prefs.edit().putBoolean("enabled", false).apply(); toast("સમયપત્રક બંધ કર્યું") }
        content.addView(TextView(this).apply { text = "નોંધ: Silent મોડ Androidની Do Not Disturb પરવાનગી પર આધારિત છે. ફોન ફરી ચાલુ કર્યા પછી પણ સમયપત્રક ફરી ગોઠવવાનો પ્રયાસ થશે."; textSize = 13f; setPadding(0, 20, 0, 24) })
    }

    private fun addButton(parent: LinearLayout, label: String, buttonText: String, action: () -> Unit) {
        parent.addView(TextView(this).apply { text = label; textSize = 16f; setPadding(0, 10, 0, 6) })
        parent.addView(Button(this).apply { text = buttonText; setOnClickListener { action() } })
    }

    private fun chooseTime(start: Boolean) {
        val h = if (start) startHour else endHour; val m = if (start) startMinute else endMinute
        TimePickerDialog(this, { _, hour, minute ->
            if (start) { startHour = hour; startMinute = minute; startLabel.text = "શરૂઆત: ${fmt(hour, minute)}" }
            else { endHour = hour; endMinute = minute; endLabel.text = "સમાપ્ત: ${fmt(hour, minute)}" }
        }, h, m, false).show()
    }

    private fun cycleMode() {
        selectedMode = when (selectedMode) {
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_SILENT
        }
        modeLabel.text = "પસંદ કરેલો મોડ: ${modeName(selectedMode)}"
    }

    private fun saveAndSchedule() {
        val chosen = dayBoxes.mapIndexedNotNull { i, box -> if (box.isChecked) i.toString() else null }.toSet()
        if (chosen.isEmpty()) { toast("ઓછામાં ઓછો એક દિવસ પસંદ કરો"); return }
        getSharedPreferences("schedule", Context.MODE_PRIVATE).edit()
            .putInt("startHour", startHour).putInt("startMinute", startMinute)
            .putInt("endHour", endHour).putInt("endMinute", endMinute)
            .putInt("mode", selectedMode).putStringSet("days", chosen).putBoolean("enabled", true).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                toast("ચોક્કસ અલાર્મની પરવાનગી આપો, પછી ફરી ચાલુ કરો")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)); return
            }
        }
        Scheduler.scheduleNext(this)
        toast("સમયપત્રક સાચવ્યું અને ચાલુ કર્યું")
    }

    private fun openPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        else toast("આ પરવાનગી Settingsમાં તપાસો")
    }
    private fun fmt(h: Int, m: Int) = String.format("%02d:%02d", h, m)
    private fun modeName(mode: Int) = when (mode) { AudioManager.RINGER_MODE_NORMAL -> "Ring"; AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; else -> "Silent / DND" }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

object Scheduler {
    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(0, 1).forEach { id ->
            val pi = android.app.PendingIntent.getBroadcast(context, id, Intent(context, ModeReceiver::class.java).putExtra("slot", id), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            am.cancel(pi)
        }
    }

    fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return
        cancelAll(context)
        val days = prefs.getStringSet("days", emptySet()) ?: emptySet()
        val startMode = prefs.getInt("mode", AudioManager.RINGER_MODE_SILENT)
        scheduleOne(context, 0, prefs.getInt("startHour", 22), prefs.getInt("startMinute", 0), days, startMode)
        scheduleOne(context, 1, prefs.getInt("endHour", 7), prefs.getInt("endMinute", 0), days, AudioManager.RINGER_MODE_NORMAL)
    }

    private fun scheduleOne(context: Context, slot: Int, hour: Int, minute: Int, days: Set<String>, mode: Int) {
        val now = Calendar.getInstance()
        var next: Calendar? = null
        for (offset in 0..7) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val weekdayIndex = (c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).toString()
            val matchesDay = if (slot == 1) {
                val previous = ((c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 6) % 7).toString()
                weekdayIndex in days || previous in days
            } else weekdayIndex in days
            if (matchesDay && c.after(now)) { next = c; break }
        }
        val target = next ?: return
        val intent = Intent(context, ModeReceiver::class.java).putExtra("slot", slot).putExtra("mode", mode)
        val pi = android.app.PendingIntent.getBroadcast(context, slot, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
    }
}
