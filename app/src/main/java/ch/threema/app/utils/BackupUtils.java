/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.utils;

import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;

public class BackupUtils {

    private static String buildBallotChoiceUid(int apiChoiceId) {
        return String.valueOf(apiChoiceId);
    }

    private static String buildBallotUid(String apiId, String creator) {
        return apiId + "-" + creator;
    }

    @Deprecated
    public static String buildGroupUid(String apiId, String creator) {
        return apiId + "-" + creator;
    }

    @Deprecated
    public static String buildGroupUid(GroupModel groupModel) {
        return buildGroupUid(groupModel.getApiGroupId().toString(), groupModel.getCreatorIdentity());
    }

    public static String buildBallotUid(BallotModel ballotModel) {
        return buildBallotUid(ballotModel.getApiBallotId(), ballotModel.getCreatorIdentity());
    }

    public static String buildBallotChoiceUid(BallotChoiceModel ballotChoiceModel) {
        return buildBallotChoiceUid(ballotChoiceModel.getApiBallotChoiceId());
    }

    public static String buildDistributionListUid(DistributionListModel distributionListModel) {
        return String.valueOf(distributionListModel.getId());
    }

    /**
     * Calculate the count of nonces that are not yet respected in the progress calculation.
     * <p>
     * This calculation is based on the assumption that per noncesPerChunk of processed nonces
     * the steps are calculated like `steps = noncesInChunk / noncesPerStep`. It is assumed that every
     * chunk except the last contains noncesPerChunk.
     * Therefore for every processed chunk there may remain some nonces that are not yet respected.
     *
     * @return The number of nonces not yet respected for step calculation
     */
    public static int calcRemainingNoncesProgress(
        final int noncesPerChunk,
        final int noncesPerStep,
        final int nonceCount
    ) {
        int fullChunks = nonceCount / noncesPerChunk;
        int lastChunkCount = nonceCount - (noncesPerChunk * fullChunks);

        int remainingPerFullChunk = noncesPerChunk % noncesPerStep;
        int remainingInLastChunk = lastChunkCount % noncesPerStep;
        return remainingPerFullChunk * fullChunks + remainingInLastChunk;
    }
}
