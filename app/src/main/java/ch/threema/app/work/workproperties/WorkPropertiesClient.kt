package ch.threema.app.work.workproperties

import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.libthreema.toLibthreemaClientInfo
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.models.WorkClientInfo
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.libthreema.WorkContext
import ch.threema.libthreema.WorkCredentials
import ch.threema.libthreema.WorkFlavor
import ch.threema.libthreema.WorkProperties
import ch.threema.libthreema.WorkPropertiesUpdateContext
import ch.threema.libthreema.WorkPropertiesUpdateException
import ch.threema.libthreema.WorkPropertiesUpdateLoop
import ch.threema.libthreema.WorkPropertiesUpdateTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

private val logger = getThreemaLogger("WorkPropertiesClient")

class WorkPropertiesClient(
    private val clientInfo: WorkClientInfo,
    private val httpClient: LibthreemaHttpClient,
    private val serverAddressProvider: ServerAddressProvider,
    private val userService: UserService,
    private val licenseService: LicenseService<*>,
) {

    suspend fun updateAvailabilityStatus(availabilityStatus: AvailabilityStatus): Result<Unit> =
        update(
            WorkProperties(
                availabilityStatus = availabilityStatus.toLibthreemaModel(),
            ),
        )

    private suspend fun update(workProperties: WorkProperties): Result<Unit> = coroutineScope {
        val workPropertiesUpdateContext = try {
            getWorkPropertiesUpdateContext()
        } catch (e: IllegalStateException) {
            logger.error("Failed to create work properties update context", e)
            return@coroutineScope Result.failure(e)
        }
        try {
            val task = WorkPropertiesUpdateTask(
                context = workPropertiesUpdateContext,
                workProperties = workProperties,
            )
            while (isActive) {
                when (val updateLoop = task.poll()) {
                    is WorkPropertiesUpdateLoop.Instruction -> {
                        ensureActive()
                        logger.info("Sending work properties update request")
                        task.response(httpClient.sendHttpsRequest(updateLoop.v1))
                    }
                    WorkPropertiesUpdateLoop.Done -> return@coroutineScope Result.success(Unit)
                }
            }
            return@coroutineScope Result.failure(
                CancellationException("Scope was cancelled before the instruction loop completed"),
            )
        } catch (e: WorkPropertiesUpdateException) {
            return@coroutineScope Result.failure(e)
        }
    }

    @Throws(IllegalStateException::class)
    private fun getWorkPropertiesUpdateContext(): WorkPropertiesUpdateContext {
        val userCredentials = licenseService.loadCredentials() as? UserCredentials
            ?: error("No user credentials found")
        return WorkPropertiesUpdateContext(
            clientInfo = clientInfo.toLibthreemaClientInfo(),
            workServerBaseUrl = serverAddressProvider
                .getWorkServerUrl()
                ?: error("No work server URL found"),
            workContext = WorkContext(
                credentials = WorkCredentials(
                    username = userCredentials.username,
                    password = userCredentials.password,
                ),
                flavor = when (clientInfo.workFlavor) {
                    WorkClientInfo.WorkFlavor.ON_PREM -> WorkFlavor.ON_PREM
                    WorkClientInfo.WorkFlavor.WORK -> WorkFlavor.WORK
                },
            ),
            userIdentity = userService.identity ?: error("No user identity found"),
            clientKey = userService.privateKey ?: error("No client key found"),
        )
    }
}
