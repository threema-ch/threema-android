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

package ch.threema.app.startup

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ch.threema.app.AppConstants
import ch.threema.app.GlobalAppState
import ch.threema.app.di.awaitSessionScopeReady
import ch.threema.app.di.getOrNull
import ch.threema.app.services.LifetimeService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val logger = getThreemaLogger("AppProcessLifecycleObserver")

class AppProcessLifecycleObserver(
    private val reloadAppRestrictions: () -> Unit,
    dispatcherProvider: DispatcherProvider,
) : DefaultLifecycleObserver, KoinComponent {

    /**
     * Note that this object follows a last one wins approach regarding connection acquisition and release. Intermediate connection acquisitions and
     * releases may be skipped.
     */
    private val connectionHolder = object {
        private val coroutineScope = CoroutineScope(dispatcherProvider.worker)
        private var lifetimeServiceAcquisitionJob: Job? = null
        private var lifetimeServiceReleaseJob: Job? = null

        /**
         * Acquire a permanent connection.
         */
        fun acquire() {
            lifetimeServiceAcquisitionJob = coroutineScope.launch {
                // Wait until release job is complete (or cancelled) to ensure it is acquired afterwards
                lifetimeServiceReleaseJob?.cancelAndAwaitForCancellation()

                awaitSessionScopeReady()
                get<LifetimeService>().acquireConnection(AppConstants.ACTIVITY_CONNECTION_TAG)
                logger.info("Connection now acquired")
            }
        }

        /**
         * Release the permanent connection.
         */
        fun release() {
            lifetimeServiceReleaseJob = coroutineScope.launch {
                // Wait until acquisition is complete (or cancelled) to ensure it is released afterwards
                lifetimeServiceAcquisitionJob?.cancelAndAwaitForCancellation()

                val lifetimeService = getOrNull<LifetimeService>()
                if (lifetimeService != null) {
                    lifetimeService.releaseConnectionLinger(
                        AppConstants.ACTIVITY_CONNECTION_TAG,
                        AppConstants.ACTIVITY_CONNECTION_LIFETIME,
                    )
                    logger.info("Connection linger released")
                } else {
                    logger.warn("Could not release connection linger ")
                }
            }
        }

        private suspend fun Job.cancelAndAwaitForCancellation() {
            try {
                cancelAndJoin()
            } catch (_: CancellationException) {
                // Nothing to do
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now created")
    }

    override fun onStart(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now visible")
    }

    override fun onResume(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now resumed")
        GlobalAppState.isAppResumed = true

        connectionHolder.acquire()

        reloadAppRestrictions()
    }

    override fun onPause(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now paused")
        GlobalAppState.isAppResumed = false

        connectionHolder.release()
    }

    override fun onStop(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now stopped")
    }
}
