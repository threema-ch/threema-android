package ch.threema.app.availabilitystatus.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import ch.threema.app.R
import ch.threema.app.availabilitystatus.containerColor
import ch.threema.app.availabilitystatus.displayNameRes
import ch.threema.app.availabilitystatus.iconColor
import ch.threema.app.availabilitystatus.iconRes
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.buttons.primary.ButtonPrimaryRounded
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.color.BrandGreyColors
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.utils.ConfigUtils
import ch.threema.data.datatypes.AvailabilityStatus
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class EditAvailabilityStatusBottomSheetDialog : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ConfigUtils.supportsAvailabilityStatus()) {
            throw IllegalStateException("Can not show this bottom sheet as the current build does not support this feature in general.")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ThreemaTheme {
                    val viewModel by viewModel<EditAvailabilityStatusViewModel>()

                    EventHandler(viewModel, ::onEvent)

                    WithViewState(viewModel) { state ->
                        if (state != null) {
                            ModalBottomSheet(
                                sheetState = rememberModalBottomSheetState(
                                    skipPartiallyExpanded = true,
                                ),
                                onDismissRequest = {
                                    dismiss()
                                },
                                sheetGesturesEnabled = false,
                                dragHandle = null,
                                contentWindowInsets = {
                                    BottomSheetDefaults.windowInsets
                                        .only(
                                            sides = WindowInsetsSides.Top,
                                        )
                                },
                            ) {
                                SetStatusBottomSheetContent(
                                    windowInsetsBottom = BottomSheetDefaults.windowInsets
                                        .only(
                                            sides = WindowInsetsSides.Bottom,
                                        ),
                                    status = state.status,
                                    descriptionState = state.descriptionState,
                                    isLoading = state.isLoading,
                                    hasError = state.hasError,
                                    onClickStatus = viewModel::onClickStatus,
                                    onChangeDescription = viewModel::onChangeDescription,
                                    onClickCancel = viewModel::onClickCancel,
                                    onClickSave = viewModel::onClickSave,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onEvent(event: EditAvailabilityStatusEvent) {
        when (event) {
            EditAvailabilityStatusEvent.Cancel, EditAvailabilityStatusEvent.Saved -> {
                setFragmentResultIfRequested(
                    didChangeStatus = event == EditAvailabilityStatusEvent.Saved,
                )
                dismiss()
            }
        }
    }

    private fun setFragmentResultIfRequested(didChangeStatus: Boolean) {
        val requestKey = arguments?.getString(REQUEST_KEY)
        if (requestKey != null) {
            setFragmentResult(
                requestKey = requestKey,
                result = bundleOf(
                    RESULT_KEY_DID_CHANGE_STATUS to didChangeStatus,
                ),
            )
        }
    }

    companion object {

        const val RESULT_KEY_DID_CHANGE_STATUS = "did-change-status"
        private const val REQUEST_KEY = "request-key"

        fun newInstance(requestKey: String? = null): EditAvailabilityStatusBottomSheetDialog =
            EditAvailabilityStatusBottomSheetDialog().apply {
                arguments = bundleOf(
                    REQUEST_KEY to requestKey,
                )
            }
    }
}

@Composable
fun SetStatusBottomSheetContent(
    windowInsetsBottom: WindowInsets,
    status: AvailabilityStatus,
    descriptionState: AvailabilityStatusDescriptionState,
    isLoading: Boolean,
    hasError: Boolean,
    onClickStatus: (AvailabilityStatus) -> Unit,
    onChangeDescription: (String) -> Unit,
    onClickCancel: () -> Unit,
    onClickSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = GridUnit.x2,
            )
            .verticalScroll(
                state = rememberScrollState(),
            ),
    ) {
        SpacerVertical(GridUnit.x4)

        ThemedText(
            text = stringResource(R.string.edit_availability_status_modal_title),
            style = MaterialTheme.typography.titleLarge,
        )

        SpacerVertical(GridUnit.x1)

        ThemedText(
            text = stringResource(R.string.edit_availability_status_modal_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )

        SpacerVertical(GridUnit.x2)

        StatusOptionItem(
            status = AvailabilityStatus.None,
            isSelected = status is AvailabilityStatus.None,
            enabled = !isLoading,
            onClick = onClickStatus,
        )

        StatusOptionItem(
            status = AvailabilityStatus.Busy(),
            isSelected = status is AvailabilityStatus.Busy,
            enabled = !isLoading,
            onClick = onClickStatus,
        )

        StatusOptionItem(
            status = AvailabilityStatus.Unavailable(),
            isSelected = status is AvailabilityStatus.Unavailable,
            enabled = !isLoading,
            onClick = onClickStatus,
        )

        AnimatedVisibility(
            visible = status != AvailabilityStatus.None,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                ),
            ),
        ) {
            Column {
                SpacerVertical(GridUnit.x2)

                val defaultTextFieldColors = OutlinedTextFieldDefaults.colors()

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = descriptionState.description,
                    onValueChange = onChangeDescription,
                    label = {
                        ThemedText(
                            text = stringResource(R.string.edit_availability_status_modal_optional_message_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current,
                            maxLines = 1,
                        )
                    },
                    placeholder = {
                        ThemedText(
                            text = stringResource(R.string.edit_availability_status_modal_optional_message_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    enabled = !isLoading && status is AvailabilityStatus.Set,
                    isError = descriptionState.exceedsLimit,
                    colors = defaultTextFieldColors.copy(
                        unfocusedLabelColor = defaultTextFieldColors.unfocusedLabelColor.copy(
                            alpha = 0.8f,
                        ),
                        unfocusedPlaceholderColor = defaultTextFieldColors.unfocusedPlaceholderColor.copy(
                            alpha = 0.5f,
                        ),
                        focusedPlaceholderColor = defaultTextFieldColors.focusedPlaceholderColor.copy(
                            alpha = 0.5f,
                        ),
                    ),
                    maxLines = 2,
                )

                AnimatedVisibility(
                    visible = descriptionState.exceedsLimit,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = IntSize.VisibilityThreshold,
                        ),
                    ),
                ) {
                    ThemedText(
                        modifier = Modifier.padding(
                            top = GridUnit.x0_25,
                            start = GridUnit.x0_25,
                            end = GridUnit.x0_25,
                        ),
                        text = stringResource(R.string.edit_availability_status_modal_optional_message_too_long),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                SpacerVertical(GridUnit.x1)

                ThemedText(
                    text = stringResource(R.string.edit_availability_status_modal_optional_message_description),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                ),
            ),
        ) {
            ThemedText(
                modifier = Modifier.padding(
                    top = GridUnit.x3,
                ),
                text = stringResource(R.string.an_error_occurred),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SpacerVertical(GridUnit.x3)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonOutlined(
                text = stringResource(R.string.cancel),
                onClick = onClickCancel,
                maxLines = 1,
            )

            SpacerHorizontal(GridUnit.x1_5)

            ButtonPrimaryRounded(
                text = stringResource(R.string.save),
                onClick = onClickSave,
                enabled = !isLoading &&
                    (!descriptionState.exceedsLimit || status !is AvailabilityStatus.Set),
                isLoading = isLoading,
            )
        }

        SpacerVertical(GridUnit.x2)

        SpacerVertical(windowInsetsBottom.asPaddingValues().calculateBottomPadding())
    }
}

@Composable
fun StatusOptionItem(
    status: AvailabilityStatus,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: (clickedStatus: AvailabilityStatus) -> Unit,
) {
    val isDarkTheme: Boolean = isSystemInDarkTheme()
    val backgroundColor = remember(isSelected, isDarkTheme) {
        if (isSelected) {
            Color(if (isDarkTheme) BrandGreyColors.SHADE_700 else BrandGreyColors.SHADE_300)
        } else {
            Color.Transparent
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                shape = RoundedCornerShape(GridUnit.x1),
            )
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                onClick = {
                    onClick(status)
                },
            )
            .padding(
                vertical = GridUnit.x1_5,
            )
            .alpha(
                if (enabled) AlphaValues.FULLY_OPAQUE else AlphaValues.DISABLED,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpacerHorizontal(GridUnit.x1_5)

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(
                    shape = RoundedCornerShape(
                        size = GridUnit.x1,
                    ),
                )
                .background(
                    color = status.containerColor(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(status.iconRes()),
                tint = status.iconColor(),
                contentDescription = null,
            )
        }

        SpacerHorizontal(GridUnit.x1_5)

        ThemedText(
            modifier = Modifier.weight(1f),
            text = stringResource(status.displayNameRes()),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )

        SpacerHorizontal(GridUnit.x2)
    }
}

private class PreviewProviderSetStatusBottomSheetContent : PreviewParameterProvider<AvailabilityStatus> {

    override val values: Sequence<AvailabilityStatus> = sequenceOf(
        AvailabilityStatus.None,
        AvailabilityStatus.Busy(),
        AvailabilityStatus.Busy(
            description = "In a short coffee break",
        ),
        AvailabilityStatus.Busy(
            description = "I cant keep my status description short because I am a person that likes to talk a lot.",
        ),
        AvailabilityStatus.Unavailable(),
        AvailabilityStatus.Unavailable(
            description = "Free day today",
        ),
        AvailabilityStatus.Unavailable(
            description = "I am on vacation and want to base jump mount everest. Hope to see you all when I make it back.",
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun Preview_SetStatusBottomSheetContent(
    @PreviewParameter(PreviewProviderSetStatusBottomSheetContent::class)
    availabilityStatus: AvailabilityStatus,
) {
    ThreemaThemePreview {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            ),
        ) {
            SetStatusBottomSheetContent(
                windowInsetsBottom = BottomSheetDefaults.windowInsets
                    .only(
                        sides = WindowInsetsSides.Bottom,
                    ),
                status = availabilityStatus,
                descriptionState = AvailabilityStatusDescriptionState(
                    description = when (availabilityStatus) {
                        AvailabilityStatus.None -> ""
                        is AvailabilityStatus.Busy -> availabilityStatus.description
                        is AvailabilityStatus.Unavailable -> availabilityStatus.description
                    },
                    exceedsLimit = true,
                ),
                isLoading = false,
                hasError = true,
                onClickStatus = {},
                onChangeDescription = {},
                onClickCancel = {},
                onClickSave = {},
            )
        }
    }
}
