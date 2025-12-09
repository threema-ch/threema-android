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

package ch.threema.app.activities.referral

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.threema.android.buildIntent
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.compose.common.DynamicSpacerSize1
import ch.threema.app.compose.common.DynamicSpacerSize3
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonPrimaryOverride
import ch.threema.app.compose.common.buttons.TextButtonPrimaryOverride
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.compose.theme.dimens.responsive
import ch.threema.app.framework.EventHandler
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.MimeUtil
import ch.threema.domain.types.Identity
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReferralActivity : ThreemaActivity() {

    val viewModel by viewModel<ReferralViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ConfigUtils.isReferralProgramEnabled()) {
            finish()
            return
        }

        setContent {
            EventHandler(viewModel, ::handleScreenEvent)

            ThreemaTheme {
                ReferralScreenContent(
                    onClickedBack = {
                        finish()
                    },
                    onClickShareInvitationLink = {
                        viewModel.onClickShareInvitationLink()
                    },
                    onClickViewTerms = {
                        // TODO(ANDR-4376): Open terms & conditions when provided
                    },
                )
            }
        }
    }

    private fun handleScreenEvent(event: ReferralScreenEvent) {
        when (event) {
            is ReferralScreenEvent.ShareInvitationLink -> {
                shareInvitationLink(ownIdentity = event.ownIdentity)
            }
            ReferralScreenEvent.Error -> {
                showToast(R.string.an_error_occurred)
            }
        }
    }

    private fun shareInvitationLink(ownIdentity: Identity) {
        val resolvedPersonalInvitationLink = getString(R.string.referral_program_invitation_link, ownIdentity)
        val invitationMessageContent = getString(R.string.referral_invitation_message_content, resolvedPersonalInvitationLink)
        val sendLinkIntent = buildIntent {
            action = Intent.ACTION_SEND
            type = MimeUtil.MIME_TYPE_TEXT
            putExtra(Intent.EXTRA_TEXT, invitationMessageContent)
        }
        val wrappedChooserIntent = Intent.createChooser(sendLinkIntent, getString(R.string.share_via))
        startActivity(wrappedChooserIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferralScreenContent(
    onClickedBack: () -> Unit,
    onClickShareInvitationLink: () -> Unit,
    onClickViewTerms: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
                scrollBehavior = scrollBehavior,
                title = {
                    ThemedText(
                        style = MaterialTheme.typography.titleLarge,
                        text = stringResource(R.string.referral_screen_title),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClickedBack,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { insetsPadding ->

        Column(
            modifier = Modifier
                .padding(insetsPadding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = GridUnit.x2.responsive)
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                DynamicSpacerSize3()

                ThemedText(
                    text = stringResource(R.string.referral_screen_subtitle),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                )

                SpacerVertical(GridUnit.x3)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            shape = RoundedCornerShape(GridUnit.x1_5),
                        ),
                    color = colorResource(R.color.work_primary_fixed),
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(GridUnit.x6)
                            .size(80.dp),
                        painter = painterResource(R.drawable.ic_gift),
                        tint = colorResource(R.color.work_onPrimary_fixed),
                        contentDescription = null,
                    )
                }

                SpacerVertical(GridUnit.x3)

                ThemedText(
                    text = stringResource(R.string.referral_how_it_works_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                SpacerVertical(GridUnit.x1)

                Surface(
                    shape = RoundedCornerShape(GridUnit.x1),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        ExplanationItem(
                            modifier = Modifier.padding(GridUnit.x2),
                            iconRes = R.drawable.ic_person_add_rounded,
                            title = R.string.referral_how_it_works_referral_title,
                            body = R.string.referral_how_it_works_referral_body,
                        )
                        ExplanationItem(
                            modifier = Modifier.padding(GridUnit.x2),
                            iconRes = R.drawable.ic_contract,
                            title = R.string.referral_how_it_works_requirements_title,
                            body = R.string.referral_how_it_works_requirements_body,
                        )
                        ExplanationItem(
                            modifier = Modifier.padding(GridUnit.x2),
                            iconRes = R.drawable.ic_confirmation,
                            title = R.string.referral_how_it_works_confirmation_title,
                            body = R.string.referral_how_it_works_confirmation_body,
                        )
                    }
                }

                SpacerVertical(GridUnit.x3)

                ThemedText(
                    text = stringResource(R.string.referral_your_reward_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                SpacerVertical(GridUnit.x1)

                Surface(
                    shape = RoundedCornerShape(GridUnit.x1),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ExplanationItem(
                        modifier = Modifier.padding(GridUnit.x2),
                        iconRes = R.drawable.ic_gift,
                        title = R.string.referral_your_reward_subtitle,
                        body = R.string.referral_your_reward_body,
                    )
                }

                DynamicSpacerSize3()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GridUnit.x2.responsive),
            ) {
                DynamicSpacerSize1()

                ButtonPrimaryOverride(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.referral_share_invitation_link),
                    colorPrimaryOverride = colorResource(R.color.work_primary_fixed),
                    colorOnPrimaryOverride = colorResource(R.color.work_onPrimary_fixed),
                    maxLines = 1,
                    onClick = onClickShareInvitationLink,
                )

                DynamicSpacerSize1()

                TextButtonPrimaryOverride(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.referral_view_terms_and_conditions),
                    colorPrimaryOverride = colorResource(R.color.work_primary_fixed),
                    onClick = onClickViewTerms,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExplanationItem(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    @StringRes title: Int,
    @StringRes body: Int,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(GridUnit.x3),
                painter = painterResource(iconRes),
                contentDescription = null,
            )

            SpacerHorizontal(GridUnit.x2)

            ThemedText(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        SpacerVertical(GridUnit.x1)

        ThemedText(
            modifier = Modifier.padding(
                start = GridUnit.x5,
            ),
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
        )
    }
}

@PreviewLightDark
@Composable
private fun ReferralScreenContent_Preview() {
    ThreemaThemePreview {
        ReferralScreenContent(
            onClickedBack = {},
            onClickShareInvitationLink = {},
            onClickViewTerms = {},
        )
    }
}

@PreviewDynamicColors
@Composable
private fun ReferralScreenContent_Preview_DynamicColors() {
    ThreemaThemePreview(
        shouldUseDynamicColors = true,
    ) {
        ReferralScreenContent(
            onClickedBack = {},
            onClickShareInvitationLink = {},
            onClickViewTerms = {},
        )
    }
}
