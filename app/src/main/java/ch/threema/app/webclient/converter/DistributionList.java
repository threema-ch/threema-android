/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.webclient.converter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;

@AnyThread
public class DistributionList extends Converter {
	private final static String MEMBERS = "members";
	private final static String CAN_CHANGE_MEMBERS = "canChangeMembers";

	/**
	 * Converts multiple distribution list models to MsgpackObjectBuilder instances.
	 */
	public static List<MsgpackBuilder> convert(List<DistributionListModel> distributionLists) throws ConversionException {
		List<MsgpackBuilder> list = new ArrayList<>();
		for (DistributionListModel distributionList : distributionLists) {
			list.add(convert(distributionList));
		}
		return list;
	}

	/**
	 * Converts a distribution list model to a MsgpackObjectBuilder instance.
	 */
	public static MsgpackObjectBuilder convert(DistributionListModel distributionList) throws ConversionException {
		final DistributionListService distributionListService = getDistributionListService();

		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		try {
			builder.put(Receiver.ID, getId(distributionList));
			builder.put(Receiver.DISPLAY_NAME, getName(distributionList));
			builder.put(Receiver.COLOR, getColor(distributionList));
			builder.put(Receiver.ACCESS, (new MsgpackObjectBuilder())
					.put(Receiver.CAN_DELETE, true)
					.put(CAN_CHANGE_MEMBERS, true));

			final boolean isSecretChat = getHiddenChatListService().has(distributionListService.getUniqueIdString(distributionList));
			final boolean isVisible = !isSecretChat || !getPreferenceService().isPrivateChatsHidden();
			builder.put(Receiver.LOCKED, isSecretChat);
			builder.put(Receiver.VISIBLE, isVisible);

			final MsgpackArrayBuilder memberBuilder = new MsgpackArrayBuilder();
			for (ContactModel contactModel: distributionListService.getMembers(distributionList)) {
				memberBuilder.put(contactModel.getIdentity());
			}
			builder.put(MEMBERS, memberBuilder);
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
		return builder;
	}

	public static String getId(DistributionListModel distributionList) throws ConversionException {
		try {
			return String.valueOf(distributionList.getId());
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}

	public static String getName(DistributionListModel distributionList) throws ConversionException {
		try {
			return NameUtil.getDisplayName(distributionList, getDistributionListService());
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}

	public static String getColor(DistributionListModel distributionList) throws ConversionException {
		try {
			return String.format("#%06X", (0xFFFFFF & distributionList.getColorLight()));
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}

}
