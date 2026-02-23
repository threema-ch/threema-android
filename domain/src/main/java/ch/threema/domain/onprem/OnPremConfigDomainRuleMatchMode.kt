package ch.threema.domain.onprem

enum class OnPremConfigDomainRuleMatchMode(val value: String) {
    EXACT("exact"),
    INCLUDE_SUBDOMAINS("include-subdomains"),
    ;

    companion object {
        fun fromStringOrNull(string: String): OnPremConfigDomainRuleMatchMode? =
            entries.find { it.value == string }
    }
}
