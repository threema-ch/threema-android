package ch.threema.app.compose.message

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.theme.color.CustomColors
import ch.threema.app.messagedetails.MessageDetailsUiModel
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode

@Composable
fun MessageDetailsListBox(
    modifier: Modifier = Modifier,
    messageDetailsUiModel: MessageDetailsUiModel,
    isOutbox: Boolean,
) {
    val borderColor: Color = if (isOutbox) {
        CustomColors.chatBubbleSendContainer
    } else {
        CustomColors.chatBubbleReceiveContainer
    }
    MessageDetailsList(
        modifier = modifier
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
            .then(
                stringResource(R.string.cd_message_details_container).let { contentDescription ->
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        isTraversalGroup = true
                    }
                },
            ),
        model = messageDetailsUiModel,
    )
}

@Composable
fun MessageDetailsList(
    modifier: Modifier = Modifier,
    model: MessageDetailsUiModel,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        model.messageId?.let { messageId ->
            MessageDetailsRow(
                label = stringResource(R.string.message_id),
                value = messageId,
                selectableValueOption = SelectableValueOption.Selectable(
                    onValueCopiedNoticeStringResId = R.string.message_details_message_id_copied,
                ),
            )
        }
        model.mimeType?.let { mimeType ->
            MessageDetailsRow(
                label = stringResource(R.string.mime_type),
                value = mimeType,
            )
        }
        model.fileSizeInBytes?.let { fileSizeInBytes ->
            MessageDetailsRow(
                label = stringResource(R.string.file_size),
                value = Formatter.formatShortFileSize(LocalContext.current, fileSizeInBytes),
            )
        }
        model.pfsState?.let { forwardSecurityMode ->
            MessageDetailsRow(
                label = stringResource(R.string.forward_security_mode),
                value = forwardSecurityMode.getName(LocalContext.current),
            )
        }
    }
}

private fun ForwardSecurityMode.getName(context: Context): String =
    when (this) {
        ForwardSecurityMode.NONE -> context.getString(R.string.forward_security_mode_none)
        ForwardSecurityMode.TWODH -> context.getString(R.string.forward_security_mode_2dh)
        ForwardSecurityMode.FOURDH -> context.getString(R.string.forward_security_mode_4dh)
        ForwardSecurityMode.ALL -> context.getString(R.string.forward_security_mode_all)
        ForwardSecurityMode.PARTIAL -> context.getString(R.string.forward_security_mode_partial)
    }

@Preview(name = "Outbox", group = "box")
@Composable
private fun MessageDetailsListBoxPreview_Outbox() {
    MessageDetailsListBox(
        modifier = Modifier.padding(16.dp),
        messageDetailsUiModel = MessageDetailsUiModel(
            messageId = "1234567890123456",
            mimeType = "image/png",
            fileSizeInBytes = 1024L,
            pfsState = ForwardSecurityMode.ALL,
        ),
        isOutbox = true,
    )
}

@Preview(name = "Inbox", group = "box")
@Composable
private fun MessageDetailsListBoxPreview_Inbox() {
    MessageDetailsListBox(
        modifier = Modifier.padding(16.dp),
        messageDetailsUiModel = MessageDetailsUiModel(
            messageId = "1234567890123456",
            mimeType = "image/png",
            fileSizeInBytes = 1024L,
            pfsState = ForwardSecurityMode.ALL,
        ),
        isOutbox = false,
    )
}

@Preview
@Composable
private fun MessageDetailsList_Preview() {
    MessageDetailsList(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        model = MessageDetailsUiModel(
            messageId = "1234567890123456",
            mimeType = "image/png",
            fileSizeInBytes = 1024L,
            pfsState = ForwardSecurityMode.ALL,
        ),
    )
}
