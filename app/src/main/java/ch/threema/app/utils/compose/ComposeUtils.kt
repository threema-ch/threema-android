package ch.threema.app.utils.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocaleOrDefault(): Locale {
    val configuration = LocalConfiguration.current
    return ConfigurationCompat.getLocales(configuration).get(0) ?: LocaleListCompat.getDefault()[0]!!
}

@Composable
@ReadOnlyComposable
fun stringResourceOrNull(@StringRes id: Int?): String? =
    if (id != null) stringResource(id) else null
