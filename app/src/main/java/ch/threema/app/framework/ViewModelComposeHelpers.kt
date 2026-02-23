package ch.threema.app.framework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
inline fun <ViewEvent : Any> EventHandler(viewModel: BaseViewModel<*, ViewEvent>, crossinline handler: suspend (ViewEvent) -> Unit) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            handler(event)
        }
    }
}

@Composable
inline fun <ViewState : Any> WithViewState(viewModel: BaseViewModel<ViewState, *>, content: @Composable (ViewState?) -> Unit) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    content(viewState)
}
