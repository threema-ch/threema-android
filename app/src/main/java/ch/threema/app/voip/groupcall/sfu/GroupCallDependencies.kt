package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.IdentityStore

data class GroupCallDependencies(
    val identityStore: IdentityStore,
    val contactService: ContactService,
    val groupService: GroupService,
    val apiConnector: APIConnector,
    val contactModelRepository: ContactModelRepository,
    val userService: UserService,
)
