package ch.threema.app.stores

import ch.threema.domain.types.Identity

interface IdentityProvider {
    fun getIdentity(): Identity?
}
