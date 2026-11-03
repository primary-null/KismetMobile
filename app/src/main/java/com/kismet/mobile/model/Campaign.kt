package com.kismet.mobile.model

enum class CampaignType { SURVEY, HUNT, ALERT, WARDRIVING }
enum class CampaignStatus { DRAFT, ACTIVE, PAUSED, COMPLETE }

data class Campaign(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val type: CampaignType = CampaignType.SURVEY,
    val description: String = "",
    val targetMacs: List<String> = emptyList(),
    val targetSsids: List<String> = emptyList(),
    val alertRules: List<AlertRule> = emptyList(),
    val deviceKeys: MutableList<String> = mutableListOf(),
    val notes: String = "",
    val surveyScope: String = "ALL",
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val firedAlerts: MutableList<FiredAlert> = mutableListOf(),
)
