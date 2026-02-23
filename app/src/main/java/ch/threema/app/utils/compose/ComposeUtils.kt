package ch.threema.app.utils.compose

import android.content.Context
import android.util.TypedValue
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

@Composable
fun getColor(color: Int): Color {
    return colorResource(LocalContext.current.getColorFromAttrs(color).resourceId)
}

fun Context.getColorFromAttrs(attr: Int): TypedValue {
    return TypedValue().apply {
        theme.resolveAttribute(attr, this, true)
    }
}

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
