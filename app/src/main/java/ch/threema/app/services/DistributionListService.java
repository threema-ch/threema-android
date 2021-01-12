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

package ch.threema.app.services;

import java.util.List;

import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;

public interface DistributionListService extends AvatarService<DistributionListModel> {
	interface DistributionListFilter {
		boolean sortingByDate();
		boolean sortingAscending();
	}

	DistributionListModel getById(int id);
	DistributionListModel createDistributionList(String name, String[] memberIdentities) throws ThreemaException;
	DistributionListModel updateDistributionList(DistributionListModel distributionListModel, String name, String[] memberIdentities) throws ThreemaException;

	boolean addMemberToDistributionList(DistributionListModel distributionListModel, String identity);

	boolean remove(DistributionListModel distributionListModel);
	boolean removeAll();

	String[] getDistributionListIdentities(DistributionListModel distributionListModel);
	List<DistributionListMemberModel> getDistributionListMembers(DistributionListModel distributionListModel);

	List<DistributionListModel> getAll();
	List<DistributionListModel> getAll(DistributionListFilter filter);

	List<ContactModel> getMembers(DistributionListModel distributionListModel);

	String getMembersString(DistributionListModel distributionListModel);

	DistributionListMessageReceiver createReceiver(DistributionListModel distributionListModel);

	String getUniqueIdString(DistributionListModel distributionListModel);

	int getPrimaryColor(DistributionListModel distributionListModel);

	void setIsArchived(DistributionListModel distributionListModel, boolean archived);
}
