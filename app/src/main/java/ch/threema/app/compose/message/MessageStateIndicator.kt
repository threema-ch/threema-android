package ch.threema.app.compose.message

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MessageStateIndicator(
    @DrawableRes deliveryIconRes: Int? = null,
    @StringRes deliveryIconContentDescriptionRes: Int? = null,
    deliveryIndicatorTintColor: Color? = null,
) {
    if (deliveryIconRes != null && deliveryIconContentDescriptionRes != null) {
        DeliveryIndicator(
            deliveryIconRes = deliveryIconRes,
            deliveryIconContentDescriptionRes = deliveryIconContentDescriptionRes,
            tintColor = deliveryIndicatorTintColor,
        )
    }
}
