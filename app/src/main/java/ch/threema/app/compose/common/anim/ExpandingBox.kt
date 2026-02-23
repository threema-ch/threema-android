package ch.threema.app.compose.common.anim

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * @param modifier The modifier to be applied to the box.
 * @param collapsedMaxHeight The max height of the box when collapsed.
 * When expanded the box will wrap its content height.
 * @param expanded The initial expanded state.
 * Can be also used to pass a different state and update it from the parent of the box.
 * @param content The content of the box.
 * Receives the current expanded state and a function to toggle the expanded state.
 * The content composable needs to call `toggleExpandedState` itself in order to update the `expandedState`.
 * This can be used to define e.g. a clickable on a child instead of the whole box.
 */
@Composable
fun ExpandingBox(
    modifier: Modifier = Modifier,
    collapsedMaxHeight: Dp? = null,
    expanded: Boolean = false,
    content: @Composable (
        expandedState: Boolean,
        toggleExpandedState: () -> Unit,
    ) -> Unit,
) {
    var expandedState by rememberSaveable(expanded) { mutableStateOf(expanded) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (expandedState || collapsedMaxHeight == null) {
                    Modifier.wrapContentHeight()
                } else {
                    Modifier.heightIn(max = collapsedMaxHeight)
                },
            )
            .animateContentSize(),
    ) {
        content(expandedState) { expandedState = !expandedState }
    }
}
