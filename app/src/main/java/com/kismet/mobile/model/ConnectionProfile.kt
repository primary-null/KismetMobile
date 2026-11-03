package com.kismet.mobile.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConnectionProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 2501,
    val username: String = "",
    val password: String = "",
    // SSH Tunnel settings
    val useSsh: Boolean = false,
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshPrivateKey: String = "",
) : Parcelable
