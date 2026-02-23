package ch.threema.domain.onprem

enum class OnPremConfigDomainRuleSpkiAlgorithm(val value: String) {
    SHA256("sha256"),
    ;

    companion object {
        fun fromStringOrNull(string: String): OnPremConfigDomainRuleSpkiAlgorithm? =
            entries.find { it.value == string }
    }
}
