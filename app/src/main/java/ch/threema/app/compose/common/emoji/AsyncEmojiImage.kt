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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        launch {
            val emojiDrawable: EmojiDrawable? = EmojiManager.getInstance(context).getEmojiDrawableSynchronously(spriteCoordinates)
            if (isActive) {
                state = if (emojiDrawable != null) {
                    AsyncEmojiImageState.Success(emojiDrawable.toBitmap().asImageBitmap())
                } else {
                    AsyncEmojiImageState.Failure
                }
            }
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
