package ch.threema.app.compose.preview

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

// Device configuration specifications copied from [androidx.compose.ui.tooling.preview.Devices]

private const val LOCALE = "de-rCH"

private const val GROUP_NAME_PHONE = "Phone"
private const val BASE_SPEC_PHONE = "width=411dp,height=891dp"

private const val GROUP_NAME_TABLET = "Tablet"
private const val BASE_SPEC_TABLET = "width=1280dp,height=800dp,dpi=240"

private const val GROUP_NAME_FOLDABLE = "Foldable"
private const val BASE_SPEC_FOLDABLE = "width=673dp,height=841dp"

@Preview(
    name = "default",
    group = GROUP_NAME_PHONE,
    device = "spec:$BASE_SPEC_PHONE,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "scaled-up-font",
    group = GROUP_NAME_PHONE,
    fontScale = 2.0f,
    device = "spec:$BASE_SPEC_PHONE,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "dark-mode",
    group = GROUP_NAME_PHONE,
    uiMode = UI_MODE_NIGHT_YES,
    device = "spec:$BASE_SPEC_PHONE,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "landscape",
    group = GROUP_NAME_PHONE,
    device = "spec:$BASE_SPEC_PHONE,orientation=landscape",
    locale = LOCALE,
)
annotation class PreviewThreemaPhone

@Preview(
    name = "default",
    group = GROUP_NAME_TABLET,
    device = "spec:$BASE_SPEC_TABLET,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "scaled-up-font",
    group = GROUP_NAME_TABLET,
    fontScale = 2.0f,
    device = "spec:$BASE_SPEC_TABLET,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "dark-mode",
    group = GROUP_NAME_TABLET,
    uiMode = UI_MODE_NIGHT_YES,
    device = "spec:$BASE_SPEC_TABLET,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "landscape",
    group = GROUP_NAME_TABLET,
    device = "spec:$BASE_SPEC_TABLET,orientation=landscape",
    locale = LOCALE,
)
annotation class PreviewThreemaTablet

@Preview(
    name = "default",
    group = GROUP_NAME_FOLDABLE,
    device = "spec:$BASE_SPEC_FOLDABLE,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "scaled-up-font",
    group = GROUP_NAME_FOLDABLE,
    fontScale = 2.0f,
    device = "spec:$BASE_SPEC_FOLDABLE,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "dark-mode",
    group = GROUP_NAME_FOLDABLE,
    uiMode = UI_MODE_NIGHT_YES,
    device = "spec:$BASE_SPEC_FOLDABLE,orientation=portrait",
    locale = LOCALE,
)
@Preview(
    name = "landscape",
    group = GROUP_NAME_FOLDABLE,
    device = "spec:$BASE_SPEC_FOLDABLE,orientation=landscape",
    locale = LOCALE,
)
annotation class PreviewThreemaFoldable

@PreviewThreemaPhone
@PreviewThreemaTablet
@PreviewThreemaFoldable
annotation class PreviewThreemaAll

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
annotation class PreviewLightAndDarkMode
