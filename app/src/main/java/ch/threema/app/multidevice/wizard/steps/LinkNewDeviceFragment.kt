/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.multidevice.wizard.steps

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ch.threema.app.R
import ch.threema.app.multidevice.wizard.LinkNewDeviceWizardViewModel
import ch.threema.app.utils.getStatusBarHeightPxCompat
import ch.threema.app.utils.withCurrentWindowInsets

open class LinkNewDeviceFragment : Fragment() {
    val viewModel: LinkNewDeviceWizardViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel.setCurrentFragment(this)

        super.onCreate(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Add extra vertical top padding to prevent ui elements being overlapped
        // by the system status bar when the bottom-sheet is fully expanded
        withCurrentWindowInsets { _, insets ->
            view?.findViewById<View>(R.id.parent_layout)?.updatePadding(
                top = insets.getStatusBarHeightPxCompat(),
            )
        }
        super.onConfigurationChanged(newConfig)
    }
}
