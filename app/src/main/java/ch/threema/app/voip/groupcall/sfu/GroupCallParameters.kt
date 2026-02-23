package ch.threema.app.voip.groupcall.sfu

data class GroupCallParameters(
    val ipv6Enabled: Boolean,
    val aecMode: String,
    val videoCodec: String,
)
