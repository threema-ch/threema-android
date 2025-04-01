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

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import ch.threema.app.R
import com.google.android.material.button.MaterialButton

class LinkNewDeviceSuccessFragment : LinkNewDeviceMessageFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.emoji).setImageResource(R.drawable.emoji_party_popper)
        view.findViewById<TextView>(R.id.title).text =
            getString(R.string.device_linked_successfully)
        view.findViewById<TextView>(R.id.body).text =
            getString(R.string.device_linked_successfully_explain, getString(R.string.app_name))
        view.findViewById<MaterialButton>(R.id.button).text = getString(R.string.label_continue)
        view.findViewById<MaterialButton>(R.id.button).setOnClickListener {
            viewModel.switchToFragment(null)
        }
    }
}
