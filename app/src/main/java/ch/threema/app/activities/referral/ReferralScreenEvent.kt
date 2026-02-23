package ch.threema.app.activities.referral

import ch.threema.domain.types.Identity

sealed interface ReferralScreenEvent {
    data class ShareInvitationLink(val ownIdentity: Identity) : ReferralScreenEvent
    data object Error : ReferralScreenEvent
}
