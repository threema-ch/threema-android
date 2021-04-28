/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.backuprestore;

import android.content.Context;
import android.text.format.DateUtils;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ListIterator;

import ch.threema.app.R;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ZipUtil;
import ch.threema.app.voicemessage.VoiceRecorderActivity;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.VideoDataModel;

public class BackupChatServiceImpl implements BackupChatService {
	private static final Logger logger = LoggerFactory.getLogger(BackupChatServiceImpl.class);

	private final Context context;
	private final FileService fileService;
	private final MessageService messageService;
	private final ContactService contactService;
	private boolean isCanceled;

	public BackupChatServiceImpl(Context context, FileService fileService, MessageService messageService, ContactService contactService) {
		this.context = context;
		this.fileService = fileService;
		this.messageService = messageService;
		this.contactService = contactService;
	}

	private boolean buildThread(ConversationModel conversationModel, ZipOutputStream zipOutputStream, StringBuilder messageBody, String password, boolean includeMedia) {
		AbstractMessageModel m;

		isCanceled = false;

		List<AbstractMessageModel> messages = messageService.getMessagesForReceiver(conversationModel.getReceiver());
		ListIterator<AbstractMessageModel> listIter = messages.listIterator(messages.size());
		while (listIter.hasPrevious()) {
			m = listIter.previous();

			if (isCanceled) {
				break;
			}

			if (m.isStatusMessage()) {
				continue;
			}

			String filename = "";
			String messageLine = "";

			if (!conversationModel.isGroupConversation() || MessageType.TEXT == m.getType()) {
				messageLine = m.isOutbox() ? this.context.getString(R.string.me_myself_and_i) : NameUtil.getDisplayNameOrNickname(this.contactService.getByIdentity(m.getIdentity()), true);
				messageLine += ": ";
			}

			messageLine += messageService.getMessageString(m, 0).getMessage();

			// add media file to zip
			try {
				boolean saveMedia = false;
				String extension = "";

				switch (m.getType()) {
					case IMAGE:
						saveMedia = true;
						extension = ".jpg";
						break;
					case VIDEO:
						VideoDataModel videoDataModel = m.getVideoData();
						saveMedia = videoDataModel != null && videoDataModel.isDownloaded();
						extension = ".mp4";
						break;
					case VOICEMESSAGE:
						AudioDataModel audioDataModel = m.getAudioData();
						saveMedia = audioDataModel != null && audioDataModel.isDownloaded();
						extension = VoiceRecorderActivity.VOICEMESSAGE_FILE_EXTENSION;
						break;
					case FILE:
						FileDataModel fileDataModel = m.getFileData();
						saveMedia = fileDataModel.isDownloaded();
						filename = TestUtil.empty(fileDataModel.getFileName()) ?
							FileUtil.getDefaultFilename(fileDataModel.getMimeType()) :
							m.getApiMessageId() + "-" + fileDataModel.getFileName();
						extension = "";
						break;
					case LOCATION:
						messageLine += " <" + GeoLocationUtil.getLocationUri(m) + ">";
						break;
					default:
				}

				if (saveMedia) {
					if (TestUtil.empty(filename)) {
						filename = m.getUid() + extension;
					}

					if (includeMedia) {
						try (InputStream is = fileService.getDecryptedMessageStream(m)) {
							if (is != null) {
								ZipUtil.addZipStream(zipOutputStream, is, filename);
							} else {
								// if media is missing, try thumbnail
								try (InputStream tis = fileService.getDecryptedMessageThumbnailStream(m)) {
									if (tis != null) {
										ZipUtil.addZipStream(zipOutputStream, tis, filename);
									}
								}
							}
						}
					}
				}
			} catch (Exception e) {
				//do not abort, its only a media :-)
				logger.error("Exception", e);
			}

			if (!TestUtil.empty(filename)) {
				messageLine += " <" + filename + ">";
			}

			String messageDate = DateUtils.formatDateTime(context, m.getPostedAt().getTime(),
					DateUtils.FORMAT_ABBREV_ALL |
							DateUtils.FORMAT_SHOW_YEAR |
							DateUtils.FORMAT_SHOW_DATE |
							DateUtils.FORMAT_NUMERIC_DATE |
							DateUtils.FORMAT_SHOW_TIME);
			if (!TestUtil.empty(messageLine)) {
				messageBody.append("[");
				messageBody.append(messageDate);
				messageBody.append("] ");
				messageBody.append(messageLine);
				messageBody.append("\n");
			}
		}
		return !isCanceled;
	}

	@Override
	public boolean backupChatToZip(final ConversationModel conversationModel, final File outputFile, final String password, boolean includeMedia) {
		StringBuilder messageBody = new StringBuilder();

		try(final ZipOutputStream zipOutputStream = ZipUtil.initializeZipOutputStream(outputFile, password)) {
			if (buildThread(conversationModel, zipOutputStream, messageBody, password, includeMedia)) {
				ZipUtil.addZipStream(zipOutputStream, IOUtils.toInputStream(messageBody, StandardCharsets.UTF_8), "messages.txt");
			}
			return true;

		} catch (Exception e) {
			logger.error("Exception", e);
		} finally {
			if (isCanceled) {
				FileUtil.deleteFileOrWarn(outputFile, "output file", logger);
			}
		}
		return false;
	}

	@Override
	public void cancel() {
		isCanceled = true;
	}
}
