package ch.threema.app.availabilitystatus

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.data.datatypes.AvailabilityStatus
import kotlinx.coroutines.flow.MutableStateFlow

class AvailabilityStatusIconElevatedView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val statusFlow = MutableStateFlow<AvailabilityStatus.Set?>(null)

    fun setStatus(status: AvailabilityStatus.Set?) {
        statusFlow.value = status
    }

    @Composable
    override fun Content() {
        ThreemaTheme {
            val status by statusFlow.collectAsStateWithLifecycle()
            status?.let { availabilityStatusSet ->
                AvailabilityStatusIconElevated(
                    status = availabilityStatusSet,
                )
            }
        }
    }
}

@Composable
fun AvailabilityStatusIconElevated(
    modifier: Modifier = Modifier,
    status: AvailabilityStatus.Set,
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(
                shape = CircleShape,
            )
            .background(
                color = Color.White,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(status.iconRes()),
            tint = status.iconColor(),
            contentDescription = stringResource(status.displayNameRes()),
        )
    }
}
