package ch.threema.app.stores

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.toIdentityOrNull

class IdentityProviderImpl(
    private val preferenceStore: PreferenceStore,
) : MutableIdentityProvider {

    private var myIdentity: Identity? = null

    init {
        myIdentity = preferenceStore.getString(PreferenceStore.PREFS_IDENTITY)
            ?.toIdentityOrNull()
    }

    override fun getIdentity() = myIdentity

    override fun setIdentity(identity: Identity?) {
        myIdentity = identity
        if (identity != null) {
            preferenceStore.save(PreferenceStore.PREFS_IDENTITY, identity.value)
        } else {
            preferenceStore.remove(PreferenceStore.PREFS_IDENTITY)
        }
    }

    override fun getIdentityString(): IdentityString? = myIdentity?.value
}
