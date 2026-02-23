package ch.threema.app.activities.referral

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.stores.IdentityProvider
import ch.threema.domain.types.Identity

class ReferralViewModel(
    private val identityProvider: IdentityProvider,
) : BaseViewModel<Unit, ReferralScreenEvent>() {

    override fun initialize() = runInitialization { }

    fun onClickShareInvitationLink() = runAction {
        val ownIdentity: Identity? = identityProvider.getIdentity()
        emitEvent(
            if (ownIdentity != null) {
                ReferralScreenEvent.ShareInvitationLink(ownIdentity)
            } else {
                ReferralScreenEvent.Error
            },
        )
    }
}
