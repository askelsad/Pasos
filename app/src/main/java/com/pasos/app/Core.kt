package com.pasos.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

data class Profile(val kg: Float, val cm: Int, val male: Boolean, val goal: Int)

object Calc {
    fun km(s: Int, p: Profile) = s * p.cm * (if (p.male) 0.415 else 0.413) / 100000.0
    fun kcal(s: Int, p: Profile) = (0.57 * p.kg * km(s, p)).toInt()
    fun activeMin(s: Int) = s / 100
    fun level(s: Int) = when {
        s < 3000 -> "Sedentario"
        s < 7000 -> "Ligero"
        s < 10000 -> "Moderado"
        else -> "Intenso"
    }
}

object Store {
    fun p(c: Context) = c.getSharedPreferences("pasos", Context.MODE_PRIVATE)
    private fun key(d: LocalDate) = "s_$d"
    fun steps(c: Context, d: LocalDate = LocalDate.now()) = p(c).getInt(key(d), 0)
    fun add(c: Context, n: Int) {
        p(c).edit().putInt(key(LocalDate.now()), steps(c) + n).apply()
    }
    fun profile(c: Context): Profile = p(c).let {
        Profile(it.getFloat("kg", 70f), it.getInt("cm", 170), it.getBoolean("male", true), it.getInt("goal", 8000))
    }
    fun save(c: Context, pr: Profile, remind: Boolean) {
        p(c).edit().putFloat("kg", pr.kg).putInt("cm", pr.cm).putBoolean("male", pr.male)
            .putInt("goal", pr.goal).putBoolean("remind", remind).apply()
    }
}

object Widget {
    fun update(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, StepWidget::class.java))
        if (ids.isEmpty()) return
        val pr = Store.profile(c)
        val s = Store.steps(c)
        val rv = RemoteViews(c.packageName, R.layout.widget)
        rv.setTextViewText(R.id.w_steps, "$s pasos")
        rv.setTextViewText(R.id.w_sub, "${Calc.kcal(s, pr)} kcal · meta ${pr.goal}")
        rv.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        m.updateAppWidget(ids, rv)
    }
}
