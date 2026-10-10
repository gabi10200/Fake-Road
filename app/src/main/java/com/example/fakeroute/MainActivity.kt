package com.example.fakeroute

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : Activity() {

    private lateinit var map: MapView
    private lateinit var speedLabel: TextView
    private val points = mutableListOf<GeoPoint>()
    private val line = Polyline()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(this@MainActivity, getSharedPreferences("osm", MODE_PRIVATE))
            userAgentValue = packageName
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(46.6, 2.3))
        }
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { addPoint(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        map.overlays.add(line)
        root.addView(map, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 24)
        }

        speedLabel = TextView(this).apply { text = "Vitesse : 50 km/h" }
        val seek = SeekBar(this).apply {
            max = 1000          // jusqu'à 1000 km/h
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, user: Boolean) {
                    val kmh = maxOf(1, v)
                    MockState.speedKmh = kmh.toDouble()
                    speedLabel.text = "Vitesse : $kmh km/h"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        val loopBox = CheckBox(this).apply {
            text = "Boucler le trajet"
            setOnCheckedChangeListener { _, c -> MockState.loop = c }
        }

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            row.addView(this, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        btn("Démarrer") { start() }
        btn("Arrêter") { stopService(Intent(this, MockService::class.java)) }
        btn("Effacer") { clearPoints() }

        val devBtn = Button(this).apply {
            text = "Options pour développeurs"
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }

        panel.addView(speedLabel)
        panel.addView(seek)
        panel.addView(loopBox)
        panel.addView(row)
        panel.addView(devBtn)
        root.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)

        Toast.makeText(this, "Touche la carte pour poser les points du trajet", Toast.LENGTH_LONG).show()
    }

    private fun addPoint(p: GeoPoint) {
        points.add(p)
        val m = Marker(map).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Point ${points.size}"
        }
        map.overlays.add(m)
        line.setPoints(points)
        map.invalidate()
    }

    private fun clearPoints() {
        points.clear()
        map.overlays.removeAll { it is Marker }
        line.setPoints(emptyList())
        map.invalidate()
    }

    private fun start() {
        if (points.size < 2) {
            Toast.makeText(this, "Pose au moins 2 points sur la carte", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Autorise d'abord la localisation", Toast.LENGTH_SHORT).show()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        MockState.waypoints = points.map {
            doubleArrayOf(it.latitude.coerceIn(-85.0, 85.0), ((it.longitude + 540) % 360) - 180)
        }
        startForegroundService(Intent(this, MockService::class.java))
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
}
