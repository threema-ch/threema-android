package ch.threema.domain.models

data class WorkClientInfo(
    override val appVersion: String,
    override val appLocale: String,
    override val deviceModel: String,
    override val osVersion: String,
    val workFlavor: WorkFlavor,
) : ClientInfo {
    enum class WorkFlavor {
        ON_PREM,
        WORK,
    }
}
