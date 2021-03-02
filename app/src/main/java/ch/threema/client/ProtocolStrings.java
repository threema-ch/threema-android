/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

/**
 * This class holds constants that are required for the protocol.
 */
public class ProtocolStrings {

	public static final String USER_AGENT = "Threema";

	public static final String API_SERVER_URL = "https://apip.threema.ch/";
	public static final String API_SERVER_URL_SANDBOX = "";
	public static final String API_SERVER_URL_IPV6 = "https://ds-apip.threema.ch/";
	public static final String API_SERVER_URL_SANDBOX_IPV6 = "";

	public static final String WORK_SERVER_URL = "https://apip-work.threema.ch/";
	public static final String WORK_SERVER_URL_SANDBOX = "";
	public static final String WORK_SERVER_URL_IPV6 = "https://ds-apip-work.threema.ch/";
	public static final String WORK_SERVER_URL_SANDBOX_IPV6 = "";

	public static final String BLOB_UPLOAD_URL = "https://blobp-upload.threema.ch/upload";
	public static final String BLOB_UPLOAD_URL_IPV6 = "https://blobp-upload.threema.ch/upload";
	public static final String BLOB_URL_PATTERN = "https://blobp-%s.threema.ch/%s";
	public static final String BLOB_URL_PATTERN_IPV6 = "https://ds-blobp-%s.threema.ch/%s";
	public static final String BLOB_DONE_PATTERN = "https://blobp-%s.threema.ch/%s/done";
	public static final String BLOB_DONE_PATTERN_IPV6 = "https://ds-blobp-%s.threema.ch/%s/done";
}
