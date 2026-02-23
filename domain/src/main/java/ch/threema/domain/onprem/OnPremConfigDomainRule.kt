package ch.threema.domain.onprem

data class OnPremConfigDomainRule(
    val fqdn: String,
    val matchMode: OnPremConfigDomainRuleMatchMode,
    val spkis: List<OnPremConfigDomainRuleSpki>?,
)
