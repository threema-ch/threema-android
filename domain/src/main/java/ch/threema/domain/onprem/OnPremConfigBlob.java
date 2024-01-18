/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.domain.onprem;

public class OnPremConfigBlob {
	// Note: these are Strings instead of URLs so that they can include placeholders
	private final String uploadUrl;
	private final String downloadUrl;
	private final String doneUrl;

	public OnPremConfigBlob(String uploadUrl, String downloadUrl, String doneUrl) {
		this.uploadUrl = uploadUrl;
		this.downloadUrl = downloadUrl;
		this.doneUrl = doneUrl;
	}

	public String getUploadUrl() {
		return uploadUrl;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public String getDoneUrl() {
		return doneUrl;
	}
}
