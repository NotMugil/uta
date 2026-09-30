package com.notmugil.uta.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object NetworkPermissionHelper {

    /**
     * Determines whether a given URL points to a local or plain HTTP server
     * requiring nearby devices / local network permission.
     */
    fun requiresNearbyPermission(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        if (trimmed.startsWith("http://")) return true
        val host = runCatching {
            val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            candidate.toHttpUrlOrNull()?.host
        }.getOrNull() ?: ""
        return isLocalHost(host)
    }

    fun isLocalHost(host: String): Boolean {
        if (host.isBlank()) return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host.endsWith(".local") ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.startsWith("100.") || // Tailscale Carrier Grade NAT
            (host.startsWith("172.") && isClassBPrivate(host))
    }

    private fun isClassBPrivate(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size >= 2) {
            val second = parts[1].toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }

    /**
     * Returns the array of runtime permissions that should be requested for Nearby devices / Local network.
     */
    fun getNearbyPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= 37) {
            list.add("android.permission.ACCESS_LOCAL_NETWORK")
        }
        return list.toTypedArray()
    }

    /**
     * Returns which permissions are currently ungranted for a given URL (or general local access).
     */
    fun getUngrantedNearbyPermissions(context: Context, url: String? = null): List<String> {
        if (url != null && !requiresNearbyPermission(url)) {
            return emptyList()
        }
        val ungranted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ungranted.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (Build.VERSION.SDK_INT >= 37) {
            if (ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK")
                != PackageManager.PERMISSION_GRANTED
            ) {
                ungranted.add("android.permission.ACCESS_LOCAL_NETWORK")
            }
        }
        return ungranted
    }

    /**
     * Checks if the required nearby devices / local network permissions are granted.
     */
    fun hasNearbyDevicesPermission(context: Context, url: String? = null): Boolean {
        return getUngrantedNearbyPermissions(context, url).isEmpty()
    }

    /**
     * Opens system App Details settings so user can manually enable Nearby devices / Local network permission.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
