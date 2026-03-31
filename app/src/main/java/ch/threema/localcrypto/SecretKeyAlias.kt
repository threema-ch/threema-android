package ch.threema.localcrypto

enum class SecretKeyAlias(val value: String) {
    PRIMARY("threema_master_key_a"),
    SECONDARY("threema_master_key_b"),
    ;

    companion object {
        fun fromValue(value: String): SecretKeyAlias? =
            entries.find { it.value == value }
    }
}
