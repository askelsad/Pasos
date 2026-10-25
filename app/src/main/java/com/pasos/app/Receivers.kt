package com.pasos.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!ActivityRecognitionResult.hasResult(i)) return
        val a = ActivityRecognitionResult.extractResult(i)?.mostProbableActivity ?: return
        val p = Store.p(c)
        when {
            a.type == DetectedActivity.IN_VEHICLE && a.confidence >= 60 ->
                p.edit().putBoolean("veh", true).apply()
            a.type in listOf(DetectedActivity.ON_FOOT, DetectedActivity.WALKING, DetectedActivity.RUNNING) && a.confidence >= 50 ->
                p.edit().putBoolean("veh", false).apply()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { ContextCompat.startForegroundService(c, Intent(c, StepService::class.java)) } catch (e: Exception) {}
    }
}

class StepWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { Widget.update(c) }
}
