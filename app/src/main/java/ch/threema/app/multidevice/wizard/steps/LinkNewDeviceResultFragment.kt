/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.R
import ch.threema.app.compose.common.DynamicSpacerSize1
import ch.threema.app.compose.common.DynamicSpacerSize4
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonPrimary
import ch.threema.app.compose.preview.PreviewThreemaPhone
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.multidevice.wizard.LinkingResult
import ch.threema.app.utils.ConfigUtils

class LinkNewDeviceResultFragment : LinkNewDeviceFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ThreemaTheme {
                    // We have to change the current color of the status bar once,
                    // because the compose threema theme sets it too
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        val window = (context as Activity).window
                        window.statusBarColor = Color.Transparent.toArgb()
                    }

                    val linkingResult by viewModel.linkingResult.collectAsStateWithLifecycle()

                    LinkNewDeviceResultContent(
                        modifier = Modifier.fillMaxSize(),
                        linkingResult = linkingResult ?: LinkingResult.Failure.Unexpected,
                        onClickedPrimary = {
                            viewModel.switchToFragment(null)
                        },
                    )
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onConfigurationChanged(newConfig: Configuration) {
        // Explicitly doing nothing, as we update the top padding using windowInsetsTopHeight from Compose
    }
}

@Composable
fun LinkNewDeviceResultContent(
    modifier: Modifier,
    linkingResult: LinkingResult,
    onClickedPrimary: () -> Unit,
) {
    Surface(
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimensionResource(R.dimen.spacing_four_grid_unit))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.systemBars))

            DynamicSpacerSize1()

            val context = LocalContext.current
            Icon(
                modifier = Modifier.size(120.dp),
                painter = painterResource(linkingResult.iconRes),
                contentDescription = null,
                tint = linkingResult.iconTintAttrRes?.let { colorAttrId ->
                    Color(ConfigUtils.getColorFromAttribute(context, colorAttrId))
                } ?: Color.Unspecified,
            )

            Spacer(Modifier.height(GridUnit.x6))

            ThemedText(
                text = linkingResult.resolveTitleText(LocalContext.current),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(GridUnit.x4))

            Text(
                text = linkingResult.resolveBodyText(LocalContext.current),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(GridUnit.x6))

            ButtonPrimary(
                onClick = onClickedPrimary,
                text = stringResource(linkingResult.primaryButtonTextRes),
                maxLines = 2,
            )

            DynamicSpacerSize4()
        }
    }
}

@Composable
@PreviewThreemaPhone
fun LinkNewDeviceResultContentPreviewFailure() = ThreemaThemePreview {
    LinkNewDeviceResultContent(
        modifier = Modifier.fillMaxSize(),
        linkingResult = LinkingResult.Failure.UnknownQrCode,
        onClickedPrimary = {},
    )
}

@Composable
@PreviewThreemaPhone
fun LinkNewDeviceResultContentPreviewSuccess() = ThreemaThemePreview {
    LinkNewDeviceResultContent(
        modifier = Modifier.fillMaxSize(),
        linkingResult = LinkingResult.Success,
        onClickedPrimary = {},
    )
}
