/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import java.io.Serializable;

public class BackupRestoreDataConfig implements Serializable {
	private final String password;
	private Boolean backupIdentity = true;
	private Boolean backupContactAndMessages = true;
	private Boolean backupMedia = true;
	private Boolean backupAvatars = true;
	private Boolean backupThumbnails = false;
	private Boolean backupVideoAndFiles = false;

	public BackupRestoreDataConfig(String password) {
		this.password = password;
	}

	public String getPassword() {
		return this.password;
	}

	public Boolean backupIdentity() {
		return backupIdentity;
	}

	public BackupRestoreDataConfig setBackupIdentity(Boolean backupIdentity) {
		this.backupIdentity = backupIdentity;
		return this;
	}

	public Boolean backupContactAndMessages() {
		return this.backupContactAndMessages;
	}

	public Boolean backupGroupsAndMessages() {
		 return this.backupContactAndMessages();
	}

	public Boolean backupDistributionLists() {
		return this.backupContactAndMessages();
	}

	public Boolean backupBallots() {
		return  this.backupContactAndMessages();
	}

	public BackupRestoreDataConfig setBackupContactAndMessages(Boolean backupContactAndMessages) {
		this.backupContactAndMessages = backupContactAndMessages;
		return this;
	}

	public Boolean backupMedia() {
		return this.backupMedia;
	}

	public Boolean backupVideoAndFiles() {
		return this.backupVideoAndFiles;
	}

	public Boolean backupThumbnails() {
		return this.backupThumbnails;
	}

	public Boolean backupAvatars() {
		return this.backupAvatars;
	}

	public BackupRestoreDataConfig setBackupMedia(Boolean backupMedia) {
		this.backupMedia = backupMedia;
		return this;
	}

	public BackupRestoreDataConfig setBackupVideoAndFiles(Boolean backupVideoAndFiles) {
		this.backupVideoAndFiles = backupVideoAndFiles;
		return this;
	}

	public BackupRestoreDataConfig setBackupThumbnails(Boolean backupThumbnails) {
		this.backupThumbnails = backupThumbnails;
		return this;
	}

	public BackupRestoreDataConfig setBackupAvatars(Boolean backupAvatars) {
		this.backupAvatars = backupAvatars;
		return this;
	}
}
