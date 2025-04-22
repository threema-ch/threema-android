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

package ch.threema.app.utils.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import ch.threema.app.utils.RingtoneUtil

object PickRingtoneContract : ActivityResultContract<PickRingtoneContract.Input, Uri?>() {
    override fun createIntent(context: Context, input: Input): Intent =
        with(input) {
            RingtoneUtil.getRingtonePickerIntent(type, currentUri, defaultUri)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent
            ?.takeIf { resultCode == Activity.RESULT_OK }
            ?.run {
                getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: Uri.EMPTY
            }

    data class Input(val type: Int, val currentUri: Uri?, val defaultUri: Uri?)
}

fun ActivityResultLauncher<PickRingtoneContract.Input>.launch(
    type: Int,
    currentUri: Uri?,
    defaultUri: Uri?,
) {
    launch(PickRingtoneContract.Input(type, currentUri, defaultUri))
}
