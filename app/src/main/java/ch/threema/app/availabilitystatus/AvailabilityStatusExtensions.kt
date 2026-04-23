package ch.threema.app.availabilitystatus

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.app.compose.theme.color.CustomColors
import ch.threema.common.takeUnlessBlank
import ch.threema.data.datatypes.AvailabilityStatus

@StringRes
fun AvailabilityStatus.displayNameRes(): Int =
    when (this) {
        AvailabilityStatus.None -> R.string.availability_status_none
        is AvailabilityStatus.Busy -> R.string.availability_status_busy
        is AvailabilityStatus.Unavailable -> R.string.availability_status_unavailable
    }

fun AvailabilityStatus.displayText(): ResolvableString =
    when (this) {
        AvailabilityStatus.None -> ResourceIdString(R.string.availability_status_none)
        is AvailabilityStatus.Busy -> description.takeUnlessBlank()?.toResolvedString()
            ?: ResourceIdString(R.string.availability_status_busy)
        is AvailabilityStatus.Unavailable -> description.takeUnlessBlank()?.toResolvedString()
            ?: ResourceIdString(R.string.availability_status_unavailable)
    }

@DrawableRes
fun AvailabilityStatus.iconRes(): Int =
    when (this) {
        AvailabilityStatus.None -> R.drawable.ic_availability_status_none
        is AvailabilityStatus.Busy -> R.drawable.ic_availability_status_busy
        is AvailabilityStatus.Unavailable -> R.drawable.ic_availability_status_unavailable
    }

@Composable
@ReadOnlyComposable
fun AvailabilityStatus.iconColor(): Color =
    when (this) {
        AvailabilityStatus.None -> CustomColors.availabilityStatusIconNone
        is AvailabilityStatus.Busy -> CustomColors.availabilityStatusIconBusy
        is AvailabilityStatus.Unavailable -> CustomColors.availabilityStatusIconUnavailable
    }

@Composable
@ReadOnlyComposable
fun AvailabilityStatus.containerColor(): Color =
    when (this) {
        AvailabilityStatus.None -> CustomColors.availabilityStatusContainerNone
        is AvailabilityStatus.Busy -> CustomColors.availabilityStatusContainerBusy
        is AvailabilityStatus.Unavailable -> CustomColors.availabilityStatusContainerUnavailable
    }

@Composable
@ReadOnlyComposable
fun AvailabilityStatus.Set.onContainerColor(): Color =
    when (this) {
        is AvailabilityStatus.Busy -> CustomColors.availabilityStatusOnContainerBusy
        is AvailabilityStatus.Unavailable -> CustomColors.availabilityStatusOnContainerUnavailable
    }
