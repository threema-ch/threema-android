package ch.threema.domain.onprem

data class OnPremConfigWeb(
    val url: String,
    val overrideSaltyRtcHost: String?,
    val overrideSaltyRtcPort: Int,
)
