package com.example.fakeroute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import kotlin.math.*

class MockService : Service() {

    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private var seg = 0
    private var segPos = 0.0          // mètres parcourus dans le segment courant
    private var lat = 0.0
    private var lon = 0.0
    private var bearing = 0f

    private val tick = object : Runnable {
        override fun run() {
            if (step()) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()

        if (MockState.waypoints.size < 2) {
            stopSelf(); return START_NOT_STICKY
        }
        try {
            providers.forEach { setupProvider(it) }
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Choisis FakeRoute comme « application de position fictive » dans les options pour développeurs.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf(); return START_NOT_STICKY
        }

        handler.removeCallbacks(tick)
        seg = 0; segPos = 0.0
        handler.post(tick)
        return START_NOT_STICKY
    }

    private fun setupProvider(p: String) {
        try { lm.removeTestProvider(p) } catch (_: Exception) {}
        lm.addTestProvider(
            p, false, false, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE
        )
        lm.setTestProviderEnabled(p, true)
    }

    /** Avance d'une seconde. Retourne false quand le trajet est terminé. */
    private fun step(): Boolean {
        val wp = MockState.waypoints
        var move = MockState.speedKmh / 3.6   // mètres pour 1 s
        var reachedEnd = false

        while (move > 0) {
            if (seg >= wp.size - 1) { reachedEnd = true; break }
            val len = dist(wp[seg], wp[seg + 1])
            val remain = len - segPos
            if (move < remain) {
                segPos += move; move = 0.0
            } else {
                move -= remain; seg++; segPos = 0.0
            }
        }

        if (reachedEnd) {
            if (MockState.loop) {
                seg = 0; segPos = 0.0
            } else {
                val last = wp.last()
                lat = last[0]; lon = last[1]
                push()
                stopSelf()
                return false
            }
        }

        val a = wp[seg]
        val b = wp[min(seg + 1, wp.size - 1)]
        val len = dist(a, b)
        val f = if (len > 0) segPos / len else 0.0
        var dLon = b[1] - a[1]
        if (dLon > 180) dLon -= 360
        if (dLon < -180) dLon += 360
        lat = a[0] + (b[0] - a[0]) * f
        lon = ((a[1] + dLon * f + 540) % 360) - 180
        if (len > 0) bearing = bearing(a, b)
        push()
        return true
    }

    private fun push() {
        val speedMs = (MockState.speedKmh / 3.6).toFloat()
        for (p in providers) {
            val loc = Location(p).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                accuracy = 5f
                speed = speedMs
                bearing = this@MockService.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            try { lm.setTestProviderLocation(p, loc) } catch (_: Exception) {}
        }
    }

    // ---- Maths ----
    private fun dist(a: DoubleArray, b: DoubleArray): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(a[0]); val p2 = Math.toRadians(b[0])
        val dp = p2 - p1
        val dl = Math.toRadians(b[1] - a[1])
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    private fun bearing(a: DoubleArray, b: DoubleArray): Float {
        val p1 = Math.toRadians(a[0]); val p2 = Math.toRadians(b[0])
        val dl = Math.toRadians(b[1] - a[1])
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    // ---- Service au premier plan ----
    private fun startInForeground() {
        val channelId = "fakeroute"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Trajet simulé", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = Notification.Builder(this, channelId)
            .setContentTitle("FakeRoute")
            .setContentText("Trajet simulé en cours")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        providers.forEach {
            try {
                lm.setTestProviderEnabled(it, false)
                lm.removeTestProvider(it)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
