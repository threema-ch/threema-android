package ch.threema.app.stores

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.Identity

class IdentityProviderImpl(
    private val preferenceStore: PreferenceStore,
) : MutableIdentityProvider {

    private var myIdentity: Identity? = null
        set(value) {
            require(value == null || value.length == ProtocolDefines.IDENTITY_LEN)
            field = value
        }

    init {
        myIdentity = preferenceStore.getString(PreferenceStore.PREFS_IDENTITY)
    }

    override fun getIdentity() = myIdentity

    override fun setIdentity(identity: Identity?) {
        myIdentity = identity
        if (identity != null) {
            preferenceStore.save(PreferenceStore.PREFS_IDENTITY, identity)
        } else {
            preferenceStore.remove(PreferenceStore.PREFS_IDENTITY)
        }
    }
}
