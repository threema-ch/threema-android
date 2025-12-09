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

package ch.threema.app.multidevice.wizard.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.threema.app.R
import ch.threema.app.compose.common.DynamicSpacerSize1
import ch.threema.app.compose.common.DynamicSpacerSize4
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonPrimary
import ch.threema.app.compose.common.rememberLinkifyWeb
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("LinkNewDevicePFSInfoFragment")

class LinkNewDevicePFSInfoFragment : LinkNewDeviceFragment() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ThreemaTheme {
                    LinkNewDevicePFSInfoContent(
                        modifier = Modifier.fillMaxSize(),
                        onClickedLinkDevice = {
                            viewModel.switchToFragment(LinkNewDeviceScanQrFragment::class.java)
                        },
                        onClickedCancel = viewModel::cancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkNewDevicePFSInfoContent(
    modifier: Modifier,
    onClickedLinkDevice: () -> Unit,
    onClickedCancel: () -> Unit,
) {
    Surface(
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = dimensionResource(R.dimen.grid_unit_x4),
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.systemBars))

            DynamicSpacerSize1()

            Icon(
                modifier = Modifier.size(120.dp),
                painter = painterResource(R.drawable.ic_info_rounded),
                contentDescription = stringResource(R.string.accessibility_device_linking_pfs_warning_icon),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(GridUnit.x6))

            val warningBodyRaw = stringResource(R.string.device_linking_pfs_warning_body)
            val faqEntryUrl = stringResource(R.string.device_linking_pfs_warning_faq_link_value)
            val warningBodyAnnotated = warningBodyRaw.rememberLinkifyWeb(faqEntryUrl)
            Text(
                textAlign = TextAlign.Center,
                text = warningBodyAnnotated,
            )

            Spacer(Modifier.height(GridUnit.x6))

            ButtonPrimary(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClickedLinkDevice,
                text = stringResource(R.string.device_linking_pfs_warning_button_continue),
                maxLines = 2,
            )

            Spacer(Modifier.height(GridUnit.x1_5))

            TextButton(
                modifier = Modifier.heightIn(min = GridUnit.x6),
                onClick = onClickedCancel,
            ) {
                ThemedText(
                    modifier = Modifier.padding(horizontal = GridUnit.x2),
                    text = stringResource(R.string.device_linking_pfs_warning_button_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DynamicSpacerSize4()
        }
    }
}

@Composable
@PreviewThreemaAll
private fun LinkNewDevicePFSInfoFragmentContentPreviewFoldable() = ThreemaThemePreview {
    LinkNewDevicePFSInfoContent(
        modifier = Modifier.fillMaxSize(),
        onClickedLinkDevice = {},
        onClickedCancel = {},
    )
}
