package ch.threema.app.usecases.avatar

import android.graphics.Bitmap
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.glide.AvatarOptions
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.DistributionListReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("GetAndPrepareAvatarUseCase")

class GetAndPrepareAvatarUseCase(
    private val avatarCacheService: AvatarCacheService,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  Load the avatar bitmap data for the given [receiverIdentifier] and prepare it **for display** by uploading data into the GPUs vRAM.
     *
     *  Devices with hardware acceleration benefit from this bitmap preparation. On devices without hardware acceleration, this preparation is
     *  effectively no-op.
     *
     *  @see [Bitmap.prepareToDraw]
     */
    suspend fun call(receiverIdentifier: ReceiverIdentifier): ImmutableBitmap? = withContext(dispatcherProvider.io) {
        val bitmap: Bitmap? = when (receiverIdentifier) {
            is ContactReceiverIdentifier -> getContactAvatar(receiverIdentifier)
            is GroupReceiverIdentifier -> getGroupAvatar(receiverIdentifier)
            is DistributionListReceiverIdentifier -> getDistributionListAvatar(receiverIdentifier)
        }
        try {
            bitmap?.prepareToDraw()
        } catch (exception: Exception) {
            logger.warn("Could not prepare bitmap for draw", exception)
        }
        bitmap?.toImmutableBitmap()
    }

    private fun getContactAvatar(contactReceiverIdentifier: ContactReceiverIdentifier): Bitmap? =
        avatarCacheService.getIdentityAvatar(
            /* identity = */
            contactReceiverIdentifier.identity,
            /* options = */
            AvatarOptions.PRESET_DEFAULT_FALLBACK,
        )

    private fun getGroupAvatar(groupReceiverIdentifier: GroupReceiverIdentifier): Bitmap? {
        val groupIdentity = GroupIdentity(
            creatorIdentity = groupReceiverIdentifier.groupCreatorIdentity,
            groupId = groupReceiverIdentifier.groupApiId,
        )
        return avatarCacheService.getGroupAvatar(
            /* groupIdentity = */
            groupIdentity,
            /* options = */
            AvatarOptions.PRESET_DEFAULT_FALLBACK,
        )
    }

    private fun getDistributionListAvatar(distributionListReceiverIdentifier: DistributionListReceiverIdentifier): Bitmap? =
        avatarCacheService.getDistributionListAvatarLow(
            /* distributionListId = */
            distributionListReceiverIdentifier.id,
        )
}
