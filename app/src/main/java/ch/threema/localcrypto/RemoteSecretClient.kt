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

package ch.threema.localcrypto

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.libthreema.toLibthreemaClientInfo
import ch.threema.domain.models.WorkClientInfo
import ch.threema.libthreema.HttpsResult
import ch.threema.libthreema.RemoteSecretCreateLoop
import ch.threema.libthreema.RemoteSecretCreateTask
import ch.threema.libthreema.RemoteSecretDeleteLoop
import ch.threema.libthreema.RemoteSecretDeleteTask
import ch.threema.libthreema.RemoteSecretMonitorException as LibthreemaRemoteSecretMonitorException
import ch.threema.libthreema.RemoteSecretMonitorInstruction
import ch.threema.libthreema.RemoteSecretMonitorProtocol
import ch.threema.libthreema.RemoteSecretSetupContext
import ch.threema.libthreema.RemoteSecretSetupException
import ch.threema.libthreema.RemoteSecretVerifier
import ch.threema.libthreema.WorkContext
import ch.threema.libthreema.WorkCredentials
import ch.threema.libthreema.WorkFlavor
import ch.threema.libthreema.use
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretHash
import ch.threema.localcrypto.models.RemoteSecretParameters
import java.io.IOException
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

private val logger = LoggingUtil.getThreemaLogger("RemoteSecretClient")

class RemoteSecretClient(
    clientInfo: WorkClientInfo,
    private val httpClientWithOnPremCertPinning: LibthreemaHttpClient,
    private val httpClientWithoutOnPremCertPinning: LibthreemaHttpClient,
) {
    private val workFlavor = when (clientInfo.workFlavor) {
        WorkClientInfo.WorkFlavor.ON_PREM -> WorkFlavor.ON_PREM
        WorkClientInfo.WorkFlavor.WORK -> WorkFlavor.WORK
    }
    private val libthreemaClientInfo = clientInfo.toLibthreemaClientInfo()

    @Throws(RemoteSecretClientException::class, InvalidCredentialsException::class, IOException::class)
    suspend fun createRemoteSecret(
        parameters: RemoteSecretClientParameters,
    ): RemoteSecretCreationResult = coroutineScope {
        try {
            RemoteSecretCreateTask(parameters.toRemoteSecretSetupContext())
                .use { createTask ->
                    while (true) {
                        ensureActive()
                        when (val createLoop = createTask.poll()) {
                            is RemoteSecretCreateLoop.Instruction -> {
                                logger.info("Sending request to create remote secret")
                                createTask.response(httpClientWithOnPremCertPinning.sendHttpsRequest(createLoop.v1))
                            }
                            is RemoteSecretCreateLoop.Done -> {
                                val result = createLoop.v1
                                return@use RemoteSecretCreationResult(
                                    remoteSecret = RemoteSecret(result.remoteSecret),
                                    parameters = RemoteSecretParameters(
                                        authenticationToken = RemoteSecretAuthenticationToken(result.remoteSecretAuthenticationToken),
                                        remoteSecretHash = RemoteSecretHash(result.remoteSecretHash),
                                    ),
                                )
                            }
                        }
                    }
                    error("unreachable code unexpectedly reached")
                }
        } catch (e: RemoteSecretSetupException) {
            handleSetupException(e)
        }
    }

    private fun handleSetupException(e: RemoteSecretSetupException): Nothing {
        throw when (e) {
            is RemoteSecretSetupException.InvalidCredentials ->
                InvalidCredentialsException(e)
            is RemoteSecretSetupException.InvalidParameter,
            is RemoteSecretSetupException.InvalidState,
            -> RemoteSecretClientException(e)
            is RemoteSecretSetupException.NetworkException,
            is RemoteSecretSetupException.RateLimitExceeded,
            is RemoteSecretSetupException.ServerException,
            -> IOException(e)
        }
    }

    @Throws(RemoteSecretClientException::class, InvalidCredentialsException::class, IOException::class)
    suspend fun deleteRemoteSecret(
        parameters: RemoteSecretClientParameters,
        authenticationToken: RemoteSecretAuthenticationToken,
    ): Unit = coroutineScope {
        try {
            RemoteSecretDeleteTask(parameters.toRemoteSecretSetupContext(), authenticationToken.value)
                .use { deleteTask ->
                    while (true) {
                        ensureActive()
                        when (val deleteLoop = deleteTask.poll()) {
                            is RemoteSecretDeleteLoop.Instruction -> {
                                logger.info("Sending request to delete remote secret")
                                deleteTask.response(httpClientWithOnPremCertPinning.sendHttpsRequest(deleteLoop.v1))
                            }
                            is RemoteSecretDeleteLoop.Done -> break
                        }
                    }
                }
        } catch (e: RemoteSecretSetupException) {
            handleSetupException(e)
        }
    }

    fun createRemoteSecretLoop(baseUrl: String, parameters: RemoteSecretParameters): RemoteSecretLoop {
        val monitorProtocol = RemoteSecretMonitorProtocol(
            clientInfo = libthreemaClientInfo,
            workServerBaseUrl = baseUrl,
            remoteSecretAuthenticationToken = parameters.authenticationToken.value,
            remoteSecretVerifier = RemoteSecretVerifier.RemoteSecretHash(
                v1 = parameters.remoteSecretHash.value,
            ),
        )
        return object : RemoteSecretLoop {
            override val remoteSecret = CompletableDeferred<RemoteSecret>()

            @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
            override suspend fun run() {
                try {
                    // TODO(ANDR-4184): The calls to fetch and monitor the Remote Secret need to enforce certificate pinning
                    //  as specified by the OPPF. However, if these pins are incorrectly changed or the client fails to get an updated version
                    //  of the pins in time, we can end up in a situation where the client can no longer fetch the Remote Secret, leaving
                    //  the app in an unrecoverable state where the user can never access their data again.
                    //  Thus, to avoid this state, in the first iteration we disabled certificate pinning for the initial fetch request.
                    var usePinning = false

                    while (true) {
                        when (val instruction = monitorProtocol.poll()) {
                            is RemoteSecretMonitorInstruction.Request -> {
                                logger.info("Sending request to monitor remote secret")
                                val httpClient = if (usePinning) httpClientWithOnPremCertPinning else httpClientWithoutOnPremCertPinning
                                val result = httpClient.sendHttpsRequest(instruction.v1)
                                monitorProtocol.response(result)
                                if (!usePinning && result is HttpsResult.Response && result.v1.status.toInt() in 200..299) {
                                    usePinning = true
                                }
                            }
                            is RemoteSecretMonitorInstruction.Schedule -> {
                                instruction.remoteSecret?.let {
                                    remoteSecret.complete(RemoteSecret(it))
                                }
                                delay(instruction.timeout.toKotlinDuration())
                            }
                        }
                    }
                } catch (e: LibthreemaRemoteSecretMonitorException) {
                    throw when (e) {
                        is LibthreemaRemoteSecretMonitorException.Blocked,
                        is LibthreemaRemoteSecretMonitorException.NotFound,
                        is LibthreemaRemoteSecretMonitorException.Mismatch,
                        ->
                            BlockedByAdminException()
                        is LibthreemaRemoteSecretMonitorException.InvalidParameter,
                        is LibthreemaRemoteSecretMonitorException.InvalidState,
                        is LibthreemaRemoteSecretMonitorException.ServerException,
                        is LibthreemaRemoteSecretMonitorException.Timeout,
                        ->
                            RemoteSecretMonitorException(e)
                    }
                } finally {
                    remoteSecret.cancel()
                }
                error("unreachable code unexpectedly reached")
            }

            override fun close() {
                monitorProtocol.close()
            }
        }
    }

    private fun RemoteSecretClientParameters.toRemoteSecretSetupContext() =
        RemoteSecretSetupContext(
            workServerBaseUrl = workServerBaseUrl,
            workContext = WorkContext(
                credentials = WorkCredentials(
                    username = credentials.username,
                    password = credentials.password,
                ),
                flavor = workFlavor,
            ),
            userIdentity = userIdentity,
            clientKey = clientKey.value,
            clientInfo = libthreemaClientInfo,
        )

    class RemoteSecretClientException(cause: Throwable? = null) : Exception(cause)
}
