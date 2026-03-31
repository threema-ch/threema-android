package ch.threema.domain.models

import androidx.compose.runtime.Immutable
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString

/**
 *  An immutable identifier used to uniquely identify a `MessageReceiver` object locally.
 */
@Immutable
sealed interface ReceiverIdentifier

@Immutable
data class ContactReceiverIdentifier(
    @JvmField val identity: IdentityString,
) : ReceiverIdentifier

@Immutable
data class GroupReceiverIdentifier(
    @JvmField val groupDatabaseId: GroupDatabaseId,
    @JvmField val groupCreatorIdentity: IdentityString,
    @JvmField val groupApiId: Long,
) : ReceiverIdentifier

@Immutable
data class DistributionListReceiverIdentifier(
    @JvmField val id: Long,
) : ReceiverIdentifier
