package ch.threema.app.testutils

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import io.mockk.every

fun PreferenceStore.mockUser(contact: TestHelpers.TestContact) {
    every { getString(PreferenceStore.PREFS_IDENTITY) } returns contact.identity
    every { getBytes(PreferenceStore.PREFS_PUBLIC_KEY) } returns contact.publicKey
    every { getString(PreferenceStore.PREFS_PUBLIC_NICKNAME) } returns "nickname of ${contact.identity}"
    every { getString(PreferenceStore.PREFS_SERVER_GROUP) } returns "serverGroup"
}

fun EncryptedPreferenceStore.mockUser(contact: TestHelpers.TestContact) {
    every { getBytes(EncryptedPreferenceStore.PREFS_PRIVATE_KEY) } returns contact.privateKey
}
