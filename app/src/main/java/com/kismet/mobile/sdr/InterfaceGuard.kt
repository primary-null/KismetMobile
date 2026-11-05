package com.kismet.mobile.sdr

import android.content.Context
import android.net.ConnectivityManager
import com.kismet.mobile.model.DataSource

sealed class GuardResult {
    object Safe : GuardResult()
    data class Warning(val reason: String) : GuardResult()
}

object InterfaceGuard {

    private val TUNNEL_PREFIXES = listOf("tun", "wg", "ipsec", "vpn", "ppp")

    fun check(context: Context, source: DataSource, serverHost: String): GuardResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork ?: return GuardResult.Safe
        val props = cm.getLinkProperties(activeNet) ?: return GuardResult.Safe
        val localIface = props.interfaceName ?: return GuardResult.Safe

        val srcIface = (source.interfaceName ?: "").ifEmpty { source.name ?: "" }

        if (srcIface.isNotEmpty() && localIface.isNotEmpty() &&
            (srcIface == localIface ||
             srcIface.startsWith(localIface) ||
             localIface.startsWith(srcIface))) {
            return GuardResult.Warning(
                "'$srcIface' is your active network interface to $serverHost. " +
                "Monitor mode will disconnect the app."
            )
        }

        if (TUNNEL_PREFIXES.any { srcIface.startsWith(it) }) {
            return GuardResult.Warning("'$srcIface' appears to be a VPN/tunnel interface.")
        }

        return GuardResult.Safe
    }

    fun isActiveInterface(context: Context, source: DataSource): Boolean =
        check(context, source, "") is GuardResult.Warning
}
