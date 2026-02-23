package ch.threema.domain.onprem

import java.time.Instant

data class OnPremLicense(
    val id: String,
    val expires: Instant,
    val count: Int,
)
