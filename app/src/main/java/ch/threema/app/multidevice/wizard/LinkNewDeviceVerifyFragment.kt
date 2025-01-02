/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.multidevice.wizard

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ch.threema.app.R
import com.google.android.material.button.MaterialButton

class LinkNewDeviceVerifyFragment: LinkNewDeviceFragment() {
    private val emojiSelectionViews = arrayOfNulls<LinkNewDeviceEmojiSelectionView>(3)

    private val onValidSelectionListener = View.OnClickListener {
        returnData(true)
    }

    private val onInvalidSelectionListener = View.OnClickListener {
        setupEmojiSelectionViews()
    }

    private val onNoMatchListener = View.OnClickListener {
        returnData(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_link_new_device_verify, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.info_text).text = getString(R.string.trust_new_device_info, getString(R.string.app_name))

        emojiSelectionViews[0] = view.findViewById(R.id.emoji_selection1)
        emojiSelectionViews[1] = view.findViewById(R.id.emoji_selection2)
        emojiSelectionViews[2] = view.findViewById(R.id.emoji_selection3)

        emojiSelectionViews[0]?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)
        emojiSelectionViews[1]?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)
        emojiSelectionViews[2]?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        view.findViewById<MaterialButton>(R.id.no_match_button).setOnClickListener(onNoMatchListener)

        setupEmojiSelectionViews()
    }

    private fun setupEmojiSelectionViews() {
        val validViewIndex = (emojiSelectionViews.indices).random()

        repeat(emojiSelectionViews.size) { i ->
            emojiSelectionViews[i]?.let {
                if (i == validViewIndex) {
                    it.setEmojis(intArrayOf(
                        viewModel.connected.emojiIndices.first,
                        viewModel.connected.emojiIndices.second,
                        viewModel.connected.emojiIndices.third))
                    it.setOnClickListener(onValidSelectionListener)
                } else {
                    it.setRandomEmojis()
                    it.setOnClickListener(onInvalidSelectionListener)
                }
            }
        }
    }

    private fun returnData(success: Boolean) {
        try {
            if (success) {
                viewModel.connected.confirmRendezvousPath()
                viewModel.showLinkingProgress()
            } else {
                viewModel.connected.declineRendezvousPath()
                viewModel.showFailure(getString(R.string.verification_failed))
            }
        } catch (e: Exception) {
            viewModel.showFailure("Error confirming or declining rendezvous path")
        }
    }
}
