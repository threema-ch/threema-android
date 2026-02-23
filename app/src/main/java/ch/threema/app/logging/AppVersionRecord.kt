package ch.threema.app.logging

import java.time.Instant

data class AppVersionRecord(
    val versionCode: Int,
    val versionName: String,
    val time: Instant,
)
