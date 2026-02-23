package ch.threema.domain.onprem

import java.time.Instant

data class OnPremConfig(
    val validUntil: Instant,
    val license: OnPremLicense,
    val domains: OnPremConfigDomains?,
    val chat: OnPremConfigChat,
    val directory: OnPremConfigDirectory,
    val blob: OnPremConfigBlob,
    val work: OnPremConfigWork,
    val avatar: OnPremConfigAvatar,
    val safe: OnPremConfigSafe,
    val web: OnPremConfigWeb?,
    val mediator: OnPremConfigMediator?,
    val maps: OnPremConfigMaps?,
)
