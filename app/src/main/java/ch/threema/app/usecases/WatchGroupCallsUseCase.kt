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

package ch.threema.app.usecases

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

/**
 *  Watches all updates to running calls from the [ch.threema.app.voip.groupcall.GroupCallManager].
 *
 *  - On every update from the [groupCallManager] it is checked if the group call feature is still enabled. If it is disabled, an empty set will be
 *  emitted.
 *  - Maps all group calls from [groupCallManager] into a UI model and adds the information if the user is currently part of that call
 *  ([GroupCallUiModel.isJoined]).
 */
class WatchGroupCallsUseCase(
    private val groupCallManager: GroupCallManager,
    private val dispatcherProvider: DispatcherProvider,
) {

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
