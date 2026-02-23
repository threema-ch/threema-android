package ch.threema.app.threemasafe

data class ThreemaSafeServerTestResponse(
    @JvmField
    val maxBackupBytes: Long,
    @JvmField
    val retentionDays: Int,
)
