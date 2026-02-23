package ch.threema.app.stores

import ch.threema.domain.types.Identity

interface MutableIdentityProvider : IdentityProvider {
    fun setIdentity(identity: Identity?)
}
