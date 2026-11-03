package com.kismet.mobile.model

enum class AlertTrigger { NEW_DEVICE, MAC_SEEN, SSID_SEEN, SIGNAL_THRESHOLD, CLIENT_COUNT }
enum class AlertAction { NOTIFY, LOG }

data class AlertRule(
    val trigger: AlertTrigger = AlertTrigger.NEW_DEVICE,
    val value: String = "",
    val action: AlertAction = AlertAction.NOTIFY,
)

data class FiredAlert(
    val ruleIndex: Int = 0,
    val deviceKey: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
