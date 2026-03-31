package ch.threema.domain.onprem

import ch.threema.common.replaceLast

data class OnPremServerConfigParameters(
    val oppfUrl: String,
    val username: String?,
    val password: String?,
) {
    val oppfFallbackUrl: String
        get() = oppfUrl.replaceLast(".oppf", ".fallback.oppf")
}
