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

package ch.threema.app.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.ButtonPrimaryWebsite
import ch.threema.app.compose.common.buttons.TextButtonNeutral
import ch.threema.app.compose.common.buttons.TextButtonPrimary
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.compose.theme.dimens.responsive
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LinkifyUtil
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.compose.currentLocaleOrDefault
import ch.threema.app.utils.context
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.utils.showToast
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("WorkIntroActivity")

/**
 *  Used to limit only certain ui elements in their width for a good look on tablets
 */
private val MAX_CONTENT_WIDTH = 600.dp

class WorkIntroActivity : ThreemaActivity() {

    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThreemaTheme(
                isDarkTheme = true,
            ) {
                WorkIntroContent(
                    onClickSignIn = ::onClickedSignIn,
                    onClickLearnMore = ::onClickLearnMore,
                    onClickDownload = ::onClickedDownload,
                )
            }
        }
    }

    private fun onClickedSignIn() {
        logger.info("Login button clicked")
        startActivity(EnterSerialActivity.createIntent(context))
    }

    private fun onClickLearnMore() {
        logger.info("Threema Work link clicked")
        val workInfoLink = getString(
            when {
                ConfigUtils.isOnPremBuild() -> R.string.threema_onprem_url
                else -> R.string.threema_work_url
            },
        )
        LinkifyUtil.getInstance().openLink(workInfoLink.toUri(), null, this)
    }

    private fun onClickedDownload() {
        try {
            when (BuildFlavor.current.licenseType) {
                BuildFlavor.LicenseType.HMS_WORK -> openConsumerAppInHuaweiAppGallery()
                else -> openConsumerAppInPlayStore()
            }
        } catch (exception: Exception) {
            logger.error("Could not find an app to handle the download-app intent", exception)
            showToast(
                message = R.string.no_activity_for_intent,
            )
        }
    }

    private fun openConsumerAppInPlayStore() {
        logger.info("Opening Play Store")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(getString(R.string.private_download_url).toUri())
        intent.setPackage("com.android.vending")
        startActivity(intent)
    }

    private fun openConsumerAppInHuaweiAppGallery() {
        logger.info("Opening Huawai App Gallery")
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = ("market://details?id=" + this.packageName).toUri()
        intent.setData(uri)
        intent.setPackage("com.huawei.appmarket")
        startActivity(intent)
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<WorkIntroActivity>(context)
    }
}

@Composable
private fun WorkIntroContent(
    onClickSignIn: () -> Unit,
    onClickLearnMore: () -> Unit,
    onClickDownload: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                )
                .padding(
                    horizontal = GridUnit.x3.responsive,
                )
                .verticalScroll(
                    state = scrollState,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpacerVertical(height = contentPadding.calculateTopPadding())

            SpacerVertical(height = GridUnit.x5.responsive)

            Column(
                modifier = Modifier
                    .widthIn(
                        max = MAX_CONTENT_WIDTH,
                    )
                    .clip(
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .padding(
                        horizontal = GridUnit.x2.responsive,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(GridUnit.x6))

                Image(
                    modifier = Modifier.fillMaxWidth(
                        fraction = 0.7f,
                    ),
                    painter = painterResource(R.drawable.logo_main_white),
                    contentDescription = stringResource(R.string.app_name),
                )

                Spacer(modifier = Modifier.height(GridUnit.x4))

                ThemedText(
                    text = pickFlavoredText(
                        textWork = R.string.work_intro_screen_work_title,
                        textOnPrem = R.string.work_intro_screen_onprem_title,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(GridUnit.x2))

                ThemedText(
                    text = pickFlavoredText(
                        textWork = R.string.work_intro_screen_work_subtitle,
                        textOnPrem = R.string.work_intro_screen_onprem_subtitle,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(GridUnit.x3))

                ButtonPrimaryWebsite(
                    onClick = onClickSignIn,
                    text = pickFlavoredText(
                        textWork = R.string.work_intro_screen_work_sign_in_button_label,
                        textOnPrem = R.string.work_intro_screen_onprem_sign_in_button_label,
                    ).uppercase(
                        locale = currentLocaleOrDefault(),
                    ),
                    iconLeading = ButtonIconInfo(
                        icon = R.drawable.ic_arrow_right,
                        contentDescription = null,
                    ),
                )

                Spacer(modifier = Modifier.height(GridUnit.x3))

                ThemedText(
                    text = pickFlavoredText(
                        textWork = R.string.work_intro_screen_work_learn_more_hint,
                        textOnPrem = R.string.work_intro_screen_onprem_learn_more_hint,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                TextButtonPrimary(
                    onClick = onClickLearnMore,
                    text = pickFlavoredText(
                        textWork = R.string.work_intro_screen_work_learn_more_button_label,
                        textOnPrem = R.string.work_intro_screen_onprem_learn_more_button_label,
                    ).uppercase(
                        locale = currentLocaleOrDefault(),
                    ),
                    iconLeading = ButtonIconInfo(
                        icon = R.drawable.ic_arrow_right,
                        contentDescription = null,
                    ),
                )

                Spacer(modifier = Modifier.height(GridUnit.x2))
            }

            Spacer(modifier = Modifier.height(GridUnit.x5))

            ThemedText(
                modifier = Modifier
                    .widthIn(
                        max = MAX_CONTENT_WIDTH,
                    )
                    .padding(
                        horizontal = GridUnit.x2.responsive,
                    ),
                text = pickFlavoredText(
                    textWork = R.string.work_intro_screen_work_use_private_hint,
                    textOnPrem = R.string.work_intro_screen_onprem_use_private_hint,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(GridUnit.x2))

            TextButtonNeutral(
                modifier = Modifier.widthIn(
                    max = MAX_CONTENT_WIDTH,
                ),
                onClick = onClickDownload,
                text = pickFlavoredText(
                    textWork = R.string.work_intro_screen_work_download_private_button_label,
                    textOnPrem = R.string.work_intro_screen_onprem_download_private_button_label,
                ).uppercase(
                    locale = currentLocaleOrDefault(),
                ),
                iconLeading = ButtonIconInfo(
                    icon = R.drawable.ic_arrow_right,
                    contentDescription = null,
                ),
            )

            SpacerVertical(height = contentPadding.calculateBottomPadding())

            SpacerVertical(height = GridUnit.x5.responsive)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun pickFlavoredText(
    @StringRes textWork: Int,
    @StringRes textOnPrem: Int,
): String = stringResource(
    id = if (ConfigUtils.isOnPremBuild()) textOnPrem else textWork,
)

@PreviewThreemaAll
@Composable
private fun WorkIntroContent_Preview() {
    ThreemaThemePreview(
        isDarkTheme = true,
    ) {
        WorkIntroContent(
            onClickSignIn = {},
            onClickLearnMore = {},
            onClickDownload = {},
        )
    }
}

@PreviewFontScale
@Composable
private fun WorkIntroContent_Preview_Scale() {
    ThreemaThemePreview(
        isDarkTheme = true,
    ) {
        WorkIntroContent(
            onClickSignIn = {},
            onClickLearnMore = {},
            onClickDownload = {},
        )
    }
}
