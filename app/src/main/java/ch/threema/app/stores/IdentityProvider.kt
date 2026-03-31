package ch.threema.app.stores

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

interface IdentityProvider {
    fun getIdentity(): Identity?

    fun getIdentityString(): IdentityString?
}
