package com.pasos.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.ActivityRecognition
import java.time.LocalTime

class StepService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdate = 0L
    private val tick = object : Runnable {
        override fun run() {
            checkIdle(); refresh()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("steps", "Pasos", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("remind", "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT))
        ServiceCompat.startForeground(this, 1, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(this, ActivityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        try { ActivityRecognition.getClient(this).requestActivityUpdates(10_000L, pi) } catch (e: Exception) {}

        val p = Store.p(this)
        if (p.getLong("lastStep", 0) == 0L) p.edit().putLong("lastStep", System.currentTimeMillis()).apply()
        handler.postDelayed(tick, 60_000)
    }

    override fun onDestroy() {
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val total = e.values[0]
        val p = Store.p(this)
        val last = p.getFloat("last", -1f)
        // Solo suma si NO está en vehículo
        if (last >= 0 && total > last && !p.getBoolean("veh", false)) {
            Store.add(this, (total - last).toInt())
            p.edit().putLong("lastStep", System.currentTimeMillis()).apply()
        }
        p.edit().putFloat("last", total).apply()
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 3000) { lastUpdate = now; refresh() }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun refresh() {
        getSystemService(NotificationManager::class.java).notify(1, build())
        Widget.update(this)
    }

    private fun build(): Notification {
        val pr = Store.profile(this)
        val s = Store.steps(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "steps")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("$s / ${pr.goal} pasos")
            .setContentText("${Calc.kcal(s, pr)} kcal · ${"%.2f".format(Calc.km(s, pr))} km · ${Calc.level(s)}")
            .setProgress(pr.goal, s.coerceAtMost(pr.goal), false)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build()
    }

    private fun checkIdle() {
        val p = Store.p(this)
        val now = System.currentTimeMillis()
        val hr = LocalTime.now().hour
        if (p.getBoolean("remind", true) && !p.getBoolean("veh", false) && hr in 8..21 &&
            now - p.getLong("lastStep", now) > 3_600_000 && now - p.getLong("lastRemind", 0) > 3_600_000
        ) {
            p.edit().putLong("lastRemind", now).apply()
            val n = NotificationCompat.Builder(this, "remind")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("¡Hora de moverte!")
                .setContentText("Llevás más de una hora sin caminar.")
                .setAutoCancel(true).build()
            getSystemService(NotificationManager::class.java).notify(2, n)
        }
    }
}
