/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

	public static final String BACKUP_DIR = "backup/";
	public static final String KEY_BACKUP_PATH = BACKUP_DIR + "keybackup.bin";

	private static String buildBallotChoiceUid(int apiChoiceId) {
		return String.valueOf(apiChoiceId);
	}

	private static String buildBallotUid(String apiId, String creator) {
		return apiId + "-" + creator;
	}

	public static String buildGroupUid(String apiId, String creator) {
		return apiId + "-" + creator;
	}

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
}
