/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.framework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.common.awaitAtLeastOneSubscriber
import ch.threema.common.awaitNonNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The base implementation for view models, very loosely following an MVI-like approach.
 *
 * The view model holds a singular view state, which is created and transformed by the view model and may be consumed by the view/UI layer.
 *
 * The view model may also emit events, e.g. to indicate to the view/UI-layer that specific one-off operations need to be performed.
 *
 * The view/UI layer may interact with the view model by calling methods on it. The naming scheme for these methods is that they use the "on" prefix
 * and describe the event that triggered them, NOT what the resulting action does. So e.g. if a "Submit" button is clicked in the UI,
 * the corresponding method might be called "onSubmitButtonClicked", not "submitForm" or anything else that indicates the details of the action.
 */
abstract class BaseViewModel<ViewState : Any, ViewEvent : Any> : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState?>(null)

    /**
     * The view model's view state. Will be `null` until the view model has completed its initialization.
     */
    val viewState = _viewState.asStateFlow()

    private val _events = MutableSharedFlow<ViewEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _viewState.subscriptionCount
                .mapLatest { count ->
                    if (count > 0) {
                        true
                    } else {
                        // When the last subscriber disappears, we wait a few seconds in case they come back, to avoid unnecessarily deactivating
                        // and reactivating the view model. This would primarily happen when an activity is recreated due to a configuration change.
                        delay(5.seconds)
                        false
                    }
                }
                .distinctUntilChanged()
                .collectLatest { isActive ->
                    if (isActive) {
                        if (_viewState.value == null) {
                            initialize()
                            awaitInitialized()
                        }
                        onActive()
                    }
                }
        }
    }

    /**
     * Subclasses need to implement this method to initialize the view model and its view state. To do so, the [runInitialization] method
     * needs to be called from the implementation.
     *
     * Within the lambda passed to [runInitialization], one-time operations may be launched, and at the end, the view model's initial view state
     * needs to be returned.
     *
     * The lambda may throw an exception or cancel its coroutine scope, e.g. in case of invalid parameters. In this case, it should emit an
     * event to ensure that the screen that hosts the view model is able to react to this invalid state, e.g. by displaying an error message or
     * by closing itself.
     */
    protected abstract fun initialize()

    private suspend fun awaitInitialized() {
        _viewState.awaitNonNull()
    }

    /**
     * Should be called from the implementation of [initialize].
     */
    protected fun runInitialization(init: suspend ViewModelInitScope<ViewEvent>.() -> ViewState) {
        viewModelScope.launch {
            require(_viewState.value == null)
            _viewState.value = baseViewModelScope.init()
        }
    }

    /**
     * Runs whenever the view model becomes active, i.e., when at least 1 subscriber is collecting its view state.
     * Guaranteed to run only once the view model is initialized.
     *
     * This can be used for operations that need to run every time the view model becomes active, or for long-running operations that should
     * remain active while the view model is active, such as collecting from other data sources or subscribing to listeners.
     *
     * Will be cancelled when the view model becomes inactive, i.e., when all subscribers have stopped collecting the view state, with a small
     * delay to avoid unnecessarily stopping and restarting.
     */
    protected open suspend fun onActive() {
        // empty by default, subclasses can override
    }

    /**
     * Performs an action, which may alter the view state or emit view events.
     * The action will suspend if the view model is not yet initialized.
     * By default, the action runs on the main thread.
     * If multiple actions are run, there is no guarantee about their execution order.
     */
    protected fun runAction(action: suspend ViewModelActionScope<ViewState, ViewEvent>.() -> Unit) {
        viewModelScope.launch {
            try {
                awaitInitialized()
                baseViewModelScope.action()
            } catch (_: EndAction) {
                // nothing to do here
            }
        }
    }

    private val baseViewModelScope = object : ViewModelScope<ViewState, ViewEvent> {
        override val currentViewState: ViewState
            get() = _viewState.value!!

        override suspend fun updateViewState(update: ViewState.() -> ViewState) {
            _viewState.update { it!!.update() }
        }

        override suspend fun emitEvent(event: ViewEvent) {
            _events.awaitAtLeastOneSubscriber()
            _events.emit(event)
        }

        override suspend fun endAction(): Nothing {
            throw EndAction()
        }
    }

    protected interface ViewModelBaseScope<ViewEvent : Any> {
        /**
         * Emits an event.
         * Will suspend if no subscribers are present to avoid losing events.
         */
        suspend fun emitEvent(event: ViewEvent)
    }

    protected interface ViewModelInitScope<ViewEvent : Any> : ViewModelBaseScope<ViewEvent>

    protected interface ViewModelActionScope<ViewState : Any, ViewEvent : Any> : ViewModelBaseScope<ViewEvent> {
        val currentViewState: ViewState

        /**
         * Updates the current view state. The previous view state is provided, such that the new view state can be derived from it, if needed.
         * Note that [update] is NOT allowed to have any side-effects, as it might be run multiple times
         */
        suspend fun updateViewState(update: ViewState.() -> ViewState)

        suspend fun endAction(): Nothing
    }

    protected interface ViewModelScope<ViewState : Any, ViewEvent : Any> : ViewModelInitScope<ViewEvent>, ViewModelActionScope<ViewState, ViewEvent>

    private class EndAction : Throwable()
}
