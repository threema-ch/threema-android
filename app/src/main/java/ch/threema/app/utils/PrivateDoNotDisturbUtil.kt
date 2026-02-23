package ch.threema.app.utils

import ch.threema.app.stores.IdentityProvider

class PrivateDoNotDisturbUtil(
    override val identityProvider: IdentityProvider,
) : DoNotDisturbUtil() {
    override fun isDoNotDisturbActive() = false
}
