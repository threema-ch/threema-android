package ch.threema.localcrypto

class PassphraseStore {
    var passphrase: CharArray? = null
        set(value) {
            if (field !== value) {
                // Remove the passphrase from memory
                field?.fill(' ')
            }
            field = value?.copyOf()
        }
}
