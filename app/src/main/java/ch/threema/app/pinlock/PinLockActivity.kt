package ch.threema.app.pinlock

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ResolvableString
import ch.threema.android.buildActivityIntent
import ch.threema.android.navigateToLauncher
import ch.threema.android.toResolvedString
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.primary.ButtonPrimary
import ch.threema.app.compose.common.extensions.get
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.compose.theme.dimens.responsive
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlin.getValue
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

private val logger = getThreemaLogger("PinLockActivity")

class PinLockActivity : ThreemaAppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val isCheckOnly: Boolean
        get() = intent.getBooleanExtra(INTENT_DATA_CHECK_ONLY, false)

    private val viewModel by viewModel<PinLockViewModel> {
        parametersOf(isCheckOnly)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        lifecycleScope.launch {
            awaitAppFullyReady()
            onCreate()
        }
    }

    private fun onCreate() {
        setContent {
            EventHandler(viewModel, ::handleScreenEvent)

            BackHandler(onBack = viewModel::onPressBack)

            ThreemaTheme {
                WithViewState(viewModel) { state ->
                    if (state != null) {
                        PinLockScreenContent(
                            pin = state.pin,
                            pinEntryEnabled = state.pinEntryEnabled,
                            error = state.error,
                            onChangePin = viewModel::onChangedPin,
                            onClickSubmit = viewModel::onClickSubmit,
                            onClickCancel = viewModel::onClickCancel,
                        )
                    }
                }
            }
        }
    }

    private fun handleScreenEvent(event: PinLockScreenEvent) {
        when (event) {
            PinLockScreenEvent.Unlock -> {
                setResult(RESULT_OK)
                finish()
            }
            PinLockScreenEvent.Cancel -> {
                setResult(RESULT_CANCELED)
                finish()
            }
            PinLockScreenEvent.NavigateToLauncher -> {
                navigateToLauncher()
            }
        }
    }

    companion object {
        private const val INTENT_DATA_CHECK_ONLY = "check"

        @JvmStatic
        @JvmOverloads
        fun createIntent(context: Context, checkOnly: Boolean = false) = buildActivityIntent<PinLockActivity>(context) {
            putExtra(INTENT_DATA_CHECK_ONLY, checkOnly)
        }
    }
}

@Composable
private fun PinLockScreenContent(
    pin: String,
    pinEntryEnabled: Boolean,
    error: ResolvableString?,
    onChangePin: (String) -> Unit,
    onClickSubmit: () -> Unit,
    onClickCancel: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                )
                .padding(horizontal = GridUnit.x4.responsive)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpacerVertical(height = contentPadding.calculateTopPadding())
            SpacerVertical(height = GridUnit.x3.responsive)

            ThemedText(
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                text = stringResource(R.string.confirm_your_pin),
            )

            SpacerVertical(GridUnit.x1)

            ThemedText(
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                text = stringResource(R.string.pinentry_enter_pin),
            )

            SpacerVertical(GridUnit.x2)

            Spacer(modifier = Modifier.weight(1f))

            PinTextField(
                pin = pin,
                pinEntryEnabled = pinEntryEnabled,
                error = error,
                onChangePin = onChangePin,
                onImeSubmit = onClickSubmit,
            )

            Spacer(modifier = Modifier.weight(1f))

            SpacerVertical(GridUnit.x2)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ButtonPrimary(
                    text = stringResource(R.string.cancel),
                    onClick = onClickCancel,
                )

                SpacerHorizontal(width = GridUnit.x2)

                ButtonPrimary(
                    text = stringResource(R.string.ok),
                    enabled = pin.isNotEmpty(),
                    onClick = onClickSubmit,
                )
            }

            SpacerVertical(height = GridUnit.x3.responsive)
            SpacerVertical(height = contentPadding.calculateBottomPadding())
        }
    }
}

@Composable
private fun PinTextField(
    pin: String,
    pinEntryEnabled: Boolean,
    error: ResolvableString?,
    onChangePin: (String) -> Unit,
    onImeSubmit: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    TextField(
        modifier = Modifier.fillMaxWidth(fraction = 0.8f)
            .focusRequester(focusRequester),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            disabledSupportingTextColor = MaterialTheme.colorScheme.onBackground,
        ),
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
        ),
        value = pin,
        onValueChange = onChangePin,
        enabled = pinEntryEnabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            showKeyboardOnFocus = true,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(
            onGo = {
                onImeSubmit()
            },
        ),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        isError = error != null,
        supportingText = {
            Text(
                text = error?.get() ?: "",
            )
        },
    )

    LaunchedEffect(pinEntryEnabled) {
        if (pinEntryEnabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}

@PreviewThreemaAll
@Composable
private fun PinLockScreenContent_WithPin_Preview() {
    ThreemaThemePreview {
        PinLockScreenContent(
            pin = "1234",
            pinEntryEnabled = true,
            error = null,
            onChangePin = {},
            onClickSubmit = {},
            onClickCancel = {},
        )
    }
}

@PreviewThreemaAll
@Composable
private fun PinLockScreenContent_WithError_Preview() {
    ThreemaThemePreview {
        PinLockScreenContent(
            pin = "",
            pinEntryEnabled = true,
            error = stringResource(R.string.pinentry_wrong_pin).toResolvedString(),
            onChangePin = {},
            onClickSubmit = {},
            onClickCancel = {},
        )
    }
}
