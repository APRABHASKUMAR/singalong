package com.personal.concertlyrics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Finds the user's approximate location. Only runs when the user has granted location permission:
 * the phone's own (approximate) location first, then an IP-address lookup if the phone can't get a fix.
 * If permission was refused it returns null and the app works without a location.
 * Call from a background thread.
 */
object Locator {

    fun detect(ctx: Context): UserLocation? {
        if (!hasPermission(ctx)) return null
        return runCatching { fromDevice(ctx) }.getOrNull() ?: runCatching { fromIp() }.getOrNull()
    }

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun fromDevice(ctx: Context): UserLocation? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val loc = last ?: current(lm) ?: return null

        @Suppress("DEPRECATION")
        val addr = runCatching {
            Geocoder(ctx, Locale.ENGLISH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        }.getOrNull()

        if (addr?.countryCode != null) {
            return UserLocation(
                city = addr.locality ?: addr.subAdminArea ?: addr.adminArea.orEmpty(),
                countryCode = addr.countryCode,
                countryName = addr.countryName,
                lat = loc.latitude,
                lon = loc.longitude,
                source = "phone",
            )
        }
        // Couldn't turn coordinates into a city: keep the exact coordinates, take the names from the IP lookup.
        return runCatching { fromIp() }.getOrNull()?.copy(lat = loc.latitude, lon = loc.longitude, source = "phone")
    }

    @SuppressLint("MissingPermission")
    private fun current(lm: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
        var result: Location? = null
        val latch = CountDownLatch(1)
        lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, { it.run() }) {
            result = it
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        return result
    }

    private fun fromIp(): UserLocation? {
        val o = JSONObject(Http.get("https://ipwho.is/") ?: return null)
        if (!o.optBoolean("success", false)) return null
        return UserLocation(
            city = o.str("city").orEmpty(),
            countryCode = o.str("country_code"),
            countryName = o.str("country"),
            lat = o.dbl("latitude"),
            lon = o.dbl("longitude"),
            source = "internet",
        )
    }
}
