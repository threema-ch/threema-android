package ch.threema.app.compose.message

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun DeliveryIndicator(
    @DrawableRes deliveryIconRes: Int,
    @StringRes deliveryIconContentDescriptionRes: Int,
    tintColor: Color?,
) {
    Icon(
        modifier = Modifier.size(18.dp),
        painter = painterResource(deliveryIconRes),
        tint = tintColor ?: MaterialTheme.colorScheme.onSurface,
        contentDescription = stringResource(deliveryIconContentDescriptionRes),
    )
}
