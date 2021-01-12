/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.backuprestore.csv;

import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.client.ThreemaConnection;
import ch.threema.storage.DatabaseServiceNew;

public class Helper {
	private DatabaseServiceNew databaseServiceNew;
	private final UserService userService;
	private final ContactService contactService;
	private final GroupService groupService;
	private final BallotService ballotService;
	private final FileService fileService;
	private final DistributionListService distributionListService;
	private final PreferenceService preferenceService;
	private final ThreemaConnection connection;

	public Helper(DatabaseServiceNew databaseServiceNew,
	              UserService userService,
	              ContactService contactService,
	              GroupService groupService,
	              BallotService ballotService,
	              FileService fileService,
	              DistributionListService distributionListService,
	              PreferenceService preferenceService,
	              ThreemaConnection threemaConnection) {

		this.databaseServiceNew = databaseServiceNew;
		this.userService = userService;
		this.contactService = contactService;
		this.groupService = groupService;
		this.ballotService = ballotService;
		this.fileService = fileService;
		this.distributionListService = distributionListService;
		this.preferenceService = preferenceService;
		this.connection = threemaConnection;
	}

	public UserService getUserService() {
		return this.userService;
	}

	public FileService getFileService() {
		return this.fileService;
	}

	public PreferenceService getPreferenceService() {
		return this.preferenceService;
	}

	public ContactService getContactService() {
		return this.contactService;
	}

	public DistributionListService getDistributionListService() {
		return this.distributionListService;
	}


	public GroupService getGroupService() {
		return this.groupService;
	}

	public BallotService getBallotService() {
		return ballotService;
	}

	public ThreemaConnection getThreemaConnection() {
		return this.connection;
	}

	public DatabaseServiceNew getDatabaseServiceNew() {
		return databaseServiceNew;
	}
}
