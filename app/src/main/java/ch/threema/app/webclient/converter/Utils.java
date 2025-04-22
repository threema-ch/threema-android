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

package ch.threema.app.webclient.converter;

import android.graphics.Bitmap;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import ch.threema.annotation.SameThread;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.utils.ThumbnailUtils;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ReceiverModel;

@AnyThread
public class Utils extends Converter {

    /**
     * This class dirtily wraps a model and proxies functions to the underlying
     * model instance.
     * <p/>
     * Should not be created outside the webclient package.
     */
    @SameThread
    public static class ModelWrapper {

        private final String id;
        private final int receiverType;
        private final ReceiverModel model;

        public ModelWrapper(ContactModel contactModel) {
            this.model = contactModel;
            this.receiverType = MessageReceiver.Type_CONTACT;
            this.id = contactModel.getIdentity();
        }

        public ModelWrapper(GroupModel groupModel) {
            this.model = groupModel;
            this.receiverType = MessageReceiver.Type_GROUP;
            this.id = String.valueOf(groupModel.getId());
        }

        /**
         * Use this constructor only when a group has been removed as the group model does not
         * exist anymore.
         */
        public ModelWrapper(long groupDbId) {
            this.model = null;
            this.receiverType = MessageReceiver.Type_GROUP;
            this.id = String.valueOf(groupDbId);
        }

        public ModelWrapper(DistributionListModel distributionListModel) {
            this.model = distributionListModel;
            this.receiverType = MessageReceiver.Type_DISTRIBUTION_LIST;
            this.id = String.valueOf(distributionListModel.getId());
        }

        public ModelWrapper(String type, String id) throws ConversionException {
            this.id = id;

            switch (type) {
                case Receiver.Type.CONTACT:
                    this.model = getContactModel(id);
                    this.receiverType = MessageReceiver.Type_CONTACT;
                    break;
                case Receiver.Type.GROUP:
                    this.model = getGroupModel(id);
                    this.receiverType = MessageReceiver.Type_GROUP;
                    break;
                case Receiver.Type.DISTRIBUTION_LIST:
                    this.model = getDistributionListModel(id);
                    this.receiverType = MessageReceiver.Type_DISTRIBUTION_LIST;
                    break;
                default:
                    throw new ConversionException("Unknown type: " + type);
            }
        }

        public static ModelWrapper getModel(ConversationModel conversationModel) throws ConversionException {
            return getModel(conversationModel.getReceiver());
        }

        public static ModelWrapper getModel(MessageReceiver receiver) throws ConversionException {
            try {
                switch (receiver.getType()) {
                    case MessageReceiver.Type_CONTACT:
                        return new ModelWrapper(((ContactMessageReceiver) receiver).getContact());
                    case MessageReceiver.Type_GROUP:
                        return new ModelWrapper(((GroupMessageReceiver) receiver).getGroup());
                    case MessageReceiver.Type_DISTRIBUTION_LIST:
                        return new ModelWrapper(((DistributionListMessageReceiver) receiver).getDistributionList());
                    default:
                        throw typeException(String.valueOf(receiver.getType()));
                }
            } catch (NullPointerException e) {
                throw new ConversionException(e);
            }
        }

        private static ConversionException typeException(String type) {
            return new ConversionException("Unknown receiver type: " + type);
        }

        public String getId() {
            return id;
        }

        public String getType() throws ConversionException {
            switch (this.receiverType) {
                case MessageReceiver.Type_CONTACT:
                    return Receiver.Type.CONTACT;
                case MessageReceiver.Type_GROUP:
                    return Receiver.Type.GROUP;
                case MessageReceiver.Type_DISTRIBUTION_LIST:
                    return Receiver.Type.DISTRIBUTION_LIST;
                default:
                    throw typeException();
            }
        }

        public MessageReceiver getReceiver() throws ConversionException {
            MessageReceiver receiver;

            // Get instance
            try {
                switch (this.receiverType) {
                    case MessageReceiver.Type_CONTACT:
                        receiver = getContactService().createReceiver((ContactModel) this.model);
                        break;
                    case MessageReceiver.Type_GROUP:
                        receiver = getGroupService().createReceiver((GroupModel) this.model);
                        break;
                    case MessageReceiver.Type_DISTRIBUTION_LIST:
                        receiver = getDistributionListService().createReceiver((DistributionListModel) this.model);
                        break;
                    default:
                        throw typeException();
                }
            } catch (NullPointerException e) {
                throw new ConversionException(e);
            }

            // Check for null
            if (receiver == null) {
                throw new ConversionException("Identity '" + this.id + "' for receiver type '" + this.receiverType + "' not found");
            }
            return receiver;
        }

        public int getColor() throws ConversionException {
            try {
                switch (this.receiverType) {
                    case MessageReceiver.Type_CONTACT:
                        return ((ContactModel) this.model).getColorLight();
                    case MessageReceiver.Type_GROUP:
                        return ((GroupModel) this.model).getColorLight();
                    case MessageReceiver.Type_DISTRIBUTION_LIST:
                        return ((DistributionListModel) this.model).getColorLight();
                    default:
                        throw typeException();
                }
            } catch (NullPointerException e) {
                throw new ConversionException(e);
            }
        }

        public byte[] getAvatar(boolean highResolution) throws ConversionException {
            return getAvatar(highResolution, null);
        }

        public byte[] getAvatar(boolean highResolution, Integer maxSize) throws ConversionException {
            try {
                Bitmap bitmap;
                if (!highResolution) {
                    bitmap = this.getReceiver().getNotificationAvatar();
                    if (bitmap == null) {
                        // This can happen e.g. for distribution lists.
                        return null;
                    }
                    bitmap = ThumbnailUtils.resize(bitmap, maxSize);
                } else {
                    switch (this.receiverType) {
                        case MessageReceiver.Type_CONTACT:
                            bitmap = getContactService().getAvatar((ContactModel) this.model, highResolution, false);
                            break;
                        case MessageReceiver.Type_GROUP:
                            bitmap = getGroupService().getAvatar((GroupModel) this.model, highResolution);
                            break;
                        case MessageReceiver.Type_DISTRIBUTION_LIST:
                            bitmap = getDistributionListService().getAvatar((DistributionListModel) this.model, highResolution);
                            break;
                        default:
                            throw typeException();
                    }
                }

                if (bitmap != null) {
                    final int quality = highResolution ? Protocol.QUALITY_AVATAR_HIRES : Protocol.QUALITY_AVATAR_LORES;
                    return BitmapUtil.bitmapToByteArray(bitmap, Protocol.FORMAT_AVATAR, quality);
                }

                return null;
            } catch (NullPointerException e) {
                throw new ConversionException(e);
            }
        }

        private ContactModel getContactModel(String id) throws ConversionException {
            // Get service and convert model to receiver
            ContactService contactService = getContactService();
            return contactService.getByIdentity(id);
        }

        private GroupModel getGroupModel(String stringId) throws ConversionException {
            try {
                // Convert id
                int id = Integer.parseInt(stringId);

                // Get service and convert model to receiver
                GroupService groupService = getGroupService();
                return groupService.getById(id);
            } catch (NumberFormatException e) {
                throw new ConversionException(e);
            }
        }

        private DistributionListModel getDistributionListModel(String stringId) throws ConversionException {
            try {
                // Convert id
                long id = Long.parseLong(stringId);

                // Get service and convert model to receiver
                DistributionListService distributionListService = getDistributionListService();
                return distributionListService.getById(id);
            } catch (NumberFormatException e) {
                throw new ConversionException(e);
            }
        }

        private ConversionException typeException() throws ConversionException {
            return typeException(String.valueOf(this.receiverType));
        }
    }

    /**
     * If the value is null or an empty string, return null.
     * Return the original value otherwise.
     */
    @Nullable
    public static String nullIfEmpty(@Nullable String value) {
        if ("".equals(value)) {
            return null;
        }
        return value;
    }
}
