package ch.threema.app.compose.common.emoji

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import ch.threema.app.emojis.EmojiDrawable
import ch.threema.app.emojis.EmojiManager
import ch.threema.app.emojis.SpriteCoordinates

/**
 *  @param modifier Is the modifier used by the [Image] composable when the emoji was successfully loaded.
 */
@Composable
fun AsyncEmojiImage(
    modifier: Modifier,
    spriteCoordinates: SpriteCoordinates,
    contentDescription: String?,
    contentLoading: @Composable (Modifier) -> Unit,
    contentFailure: @Composable (Modifier) -> Unit,
) {
    var state: AsyncEmojiImageState by remember {
        mutableStateOf(AsyncEmojiImageState.Loading)
    }

    val context = LocalContext.current
    LaunchedEffect(spriteCoordinates) {
        val emojiDrawable = EmojiManager.getInstance(context).getEmojiDrawableAsync(spriteCoordinates)
        state = try {
            emojiDrawable.awaitLoaded()
            AsyncEmojiImageState.Success(emojiDrawable.toBitmap().asImageBitmap())
        } catch (_: EmojiDrawable.EmojiLoadingException) {
            AsyncEmojiImageState.Failure
        }
    }

    when (val currentState = state) {
        AsyncEmojiImageState.Loading -> contentLoading(modifier)
        AsyncEmojiImageState.Failure -> contentFailure(modifier)
        is AsyncEmojiImageState.Success -> Image(
            modifier = modifier,
            painter = BitmapPainter(currentState.bitmap),
            contentDescription = contentDescription,
        )
    }
}

private sealed interface AsyncEmojiImageState {

    data object Loading : AsyncEmojiImageState

    data class Success(val bitmap: ImageBitmap) : AsyncEmojiImageState

    data object Failure : AsyncEmojiImageState
}
