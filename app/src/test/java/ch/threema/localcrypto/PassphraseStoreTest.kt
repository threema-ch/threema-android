package ch.threema.localcrypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassphraseStoreTest {
    @Test
    fun `passphrase can be set, read and unset`() {
        val myPassphrase = "Hello".toCharArray()
        val passphraseStore = PassphraseStore()

        // Initially, there is no passphrase
        assertNull(passphraseStore.passphrase)

        // Passphrase can be set
        passphraseStore.passphrase = myPassphrase
        assertContentEquals("Hello".toCharArray(), myPassphrase)

        // Removing the passphrase clears it from memory
        val oldPassphrase = passphraseStore.passphrase!!
        passphraseStore.passphrase = null
        assertNull(passphraseStore.passphrase)
        assertTrue(oldPassphrase.all { it == ' ' })

        // Original passphrase instance is not changed
        assertContentEquals("Hello".toCharArray(), myPassphrase)
    }
}
