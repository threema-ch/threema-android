package ch.threema.app.usecases.groups

import ch.threema.app.compose.conversation.models.GroupCallUiModel
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.common.DispatcherProvider
import ch.threema.domain.types.TimestampUTC
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class WatchGroupCallsUseCase(
    private val groupCallManager: GroupCallManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  Watches all updates to running calls from the [groupCallManager].
     *
     *  On every update from the [groupCallManager] it is checked if the group call feature is still enabled. If it is disabled, an empty set will be
     *  emitted.
     *
     *  ##### Direct emit promise
     *  The underlying [Flow] from [GroupCallManager.watchRunningCalls] fulfills this promise, so we do too.
     *
     *  ##### Overflow strategy
     *  Strategy from [GroupCallManager.watchRunningCalls]
     */
    fun call(): Flow<Set<GroupCallUiModel>> =
        groupCallManager
            .watchRunningCalls()
            .map { groupCallsMap: Map<CallId, GroupCallDescription> ->
                if (ConfigUtils.isGroupCallsEnabled()) {
                    mapToUiModels(groupCallsMap)
                } else {
                    emptySet()
                }
            }
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.io)

    /**
     *  Note that values where [GroupCallDescription.startedAt] or [GroupCallDescription.processedAt] exceed the boundaries of a [Long] will be
     *  filtered out.
     */
    private fun mapToUiModels(groupCallsMap: Map<CallId, GroupCallDescription>): Set<GroupCallUiModel> =
        groupCallsMap.mapNotNull { mapEntry: Map.Entry<CallId, GroupCallDescription> ->
            val groupCallDescription: GroupCallDescription = mapEntry.value
            val startedAt: TimestampUTC = runCatching { groupCallDescription.startedAt.toLong() }.getOrElse { return@mapNotNull null }
            val processedAt: TimestampUTC = runCatching { groupCallDescription.processedAt.toLong() }.getOrElse { return@mapNotNull null }
            GroupCallUiModel(
                id = mapEntry.value.callId,
                groupId = groupCallDescription.groupId,
                startedAt = startedAt,
                processedAt = processedAt,
                isJoined = groupCallManager.isJoinedCall(
                    callId = mapEntry.key,
                ),
            )
        }.toSet()
}
