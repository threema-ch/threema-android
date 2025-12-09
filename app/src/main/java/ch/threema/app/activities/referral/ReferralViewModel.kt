/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
