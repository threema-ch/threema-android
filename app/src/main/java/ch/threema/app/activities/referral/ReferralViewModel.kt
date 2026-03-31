package ch.threema.app.activities.referral

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.stores.IdentityProvider

class ReferralViewModel(
    private val identityProvider: IdentityProvider,
) : BaseViewModel<Unit, ReferralScreenEvent>() {

    override fun initialize() = runInitialization { }

    fun onClickShareInvitationLink() = runAction {
        val ownIdentity = identityProvider.getIdentity()
        emitEvent(
            if (ownIdentity != null) {
                ReferralScreenEvent.ShareInvitationLink(ownIdentity)
            } else {
                ReferralScreenEvent.Error
            },
        )
    }
}
