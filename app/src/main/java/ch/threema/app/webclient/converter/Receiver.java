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

import androidx.annotation.AnyThread;

import java.util.List;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

@AnyThread
public class Receiver extends Converter {
	public final static String TYPE = "type";
	public final static String ID = "id";
	public final static String DISPLAY_NAME = "displayName";
	public final static String COLOR = "color";
	public final static String DISABLED = "disabled";
	public final static String ACCESS = "access";
	public final static String CAN_DELETE= "canDelete";
	public final static String LOCKED = "locked";
	public final static String VISIBLE = "visible";

	/**
	 * Assembles and converts a list of contacts, groups and distribution lists.
	 */
	public static MsgpackObjectBuilder convert(List<ContactModel> contacts,
	                                           List<GroupModel> groups,
	                                           List<DistributionListModel> distributionLists)
			throws ConversionException {
		MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		try {
			builder.put(Type.CONTACT, Contact.convert(contacts));
			builder.put(Type.GROUP, Group.convert(groups));
			builder.put(Type.DISTRIBUTION_LIST, DistributionList.convert(distributionLists));
		} catch (NullPointerException e) {
			throw new ConversionException(e);
		}
		return builder;
	}

	public static Utils.ModelWrapper getModel(String type, String id) throws ConversionException {
		return new Utils.ModelWrapper(type, id);
	}

	public static MessageReceiver getReceiver(String type, String id) throws ConversionException {
		return getModel(type, id).getReceiver();
	}

	public static MsgpackObjectBuilder getArguments(MessageReceiver receiver) throws ConversionException {
		Utils.ModelWrapper model = Utils.ModelWrapper.getModel(receiver);
		return getArguments(model);
	}

	public static MsgpackObjectBuilder getArguments(Utils.ModelWrapper model) throws ConversionException {
		MsgpackObjectBuilder args = new MsgpackObjectBuilder();
		args.put(TYPE, model.getType());
		args.put(ID, model.getId());
		return args;
	}

	public static MsgpackObjectBuilder getArguments(String type) {
		MsgpackObjectBuilder args = new MsgpackObjectBuilder();
		args.put(TYPE, type);
		return args;
	}

	public class Type {
		public final static String CONTACT = "contact";
		public final static String GROUP = "group";
		public final static String DISTRIBUTION_LIST = "distributionList";
	}
}
