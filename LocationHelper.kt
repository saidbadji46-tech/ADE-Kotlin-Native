package com.ade.meterreading

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class GpsResult(val lat: Double, val lng: Double, val accuracy: Int?)

/** يحاول الحصول على موقع واحد: يعيد آخر موقع معروف فوراً إن وُجد، وإلا ينتظر تحديثاً جديداً بحد أقصى 8 ثوانٍ. */
object LocationHelper {

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestOnce(context: Context, onResult: (GpsResult?) -> Unit) {
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onResult(null)
            return
        }

        val providers = ArrayList<String>()
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            // تجاهل
        }
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            // تجاهل
        }

        var best: Location? = null
        for (p in providers) {
            try {
                val l = lm.getLastKnownLocation(p)
                if (l != null && (best == null || l.time > (best?.time ?: 0L))) best = l
            } catch (e: SecurityException) {
                // تجاهل
            }
        }
        val fresh = best != null && System.currentTimeMillis() - (best?.time ?: 0L) < 2 * 60 * 1000
        if (fresh) {
            val b = best
            if (b != null) {
                onResult(GpsResult(b.latitude, b.longitude, if (b.hasAccuracy()) b.accuracy.toInt() else null))
                return
            }
        }

        if (providers.isEmpty()) {
            val b = best
            if (b != null) {
                onResult(GpsResult(b.latitude, b.longitude, if (b.hasAccuracy()) b.accuracy.toInt() else null))
            } else {
                onResult(null)
            }
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                try {
                    lm.removeUpdates(this)
                } catch (e: Exception) {
                    // تجاهل
                }
                onResult(GpsResult(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy.toInt() else null))
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            lm.requestLocationUpdates(providers[0], 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            onResult(null)
            return
        }

        handler.postDelayed({
            if (!done) {
                done = true
                try {
                    lm.removeUpdates(listener)
                } catch (e: Exception) {
                    // تجاهل
                }
                val b = best
                if (b != null) {
                    onResult(GpsResult(b.latitude, b.longitude, if (b.hasAccuracy()) b.accuracy.toInt() else null))
                } else {
                    onResult(null)
                }
            }
        }, 12000)
    }
}
