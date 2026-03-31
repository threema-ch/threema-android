package ch.threema.app.compose.preview

import ch.threema.android.ResolvableString
import ch.threema.android.ResolvedString
import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.services.ContactService
import ch.threema.common.now
import ch.threema.domain.types.Identity
import ch.threema.storage.models.MessageType
import java.util.Date

/**
 *  Often used values for Compose `@Preview`s.
 *
 *  Do **not** use values elsewhere.
 */
object PreviewData {

    val IDENTITY_ME = Identity("12345678")

    val IDENTITY_OTHER_1 = Identity("11111111")
    val IDENTITY_OTHER_2 = Identity("22222222")
    val IDENTITY_OTHER_3 = Identity("33333333")

    val IDENTITY_BROADCAST = Identity("*0000000")

    val publicKey: ByteArray = ByteArray(32)

    const val LOREM_IPSUM_WORDS_50 =
        "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, " +
            "sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est " +
            "Lorem ipsum dolor sit amet."

    val mentionNames = mapOf(
        Identity(ContactService.ALL_USERS_PLACEHOLDER_ID) to ResourceIdString(R.string.all),
        IDENTITY_ME to ResourceIdString(R.string.me_myself_and_i),
        IDENTITY_OTHER_1 to ResolvedString("Roberto Diaz"),
        IDENTITY_OTHER_2 to ResolvedString("Lisa Goldman"),
        IDENTITY_BROADCAST to ResolvedString("Broadcast"),
    )

    object LatestMessageData {

        fun incomingTextMessage(
            body: String?,
            postedAt: Date = now(),
            modifiedAt: Date = now(),
            mentionNames: Map<Identity, ResolvableString> = PreviewData.mentionNames,
        ) = ConversationUiModel.LatestMessageData(
            type = MessageType.TEXT,
            body = body,
            caption = null,
            isOutbox = false,
            isDeleted = false,
            postedAt = postedAt,
            modifiedAt = modifiedAt,
            mentionNames = mentionNames,
        )

        fun incomingFileMessage(
            caption: String?,
            postedAt: Date = now(),
            modifiedAt: Date = now(),
            mentionNames: Map<Identity, ResolvableString> = PreviewData.mentionNames,
        ) = ConversationUiModel.LatestMessageData(
            type = MessageType.FILE,
            body = "[\"\",\"\",\"application/octet-stream\",1,\"filename.bin\",0,true,\"$caption\",null,{}]",
            caption = caption,
            isOutbox = false,
            isDeleted = false,
            postedAt = postedAt,
            modifiedAt = modifiedAt,
            mentionNames = mentionNames,
        )

        fun incomingDeletedTextMessage() = ConversationUiModel.LatestMessageData(
            type = MessageType.TEXT,
            body = null,
            caption = null,
            isOutbox = false,
            isDeleted = true,
            postedAt = now(),
            modifiedAt = now(),
            mentionNames = mentionNames,
        )
    }
}
