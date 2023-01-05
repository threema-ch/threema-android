/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

import androidx.annotation.Nullable;

public class OnPremConfig {

	private final int refresh;
	private final OnPremLicense license;
	private final OnPremConfigChat chatConfig;
	private final OnPremConfigDirectory directoryConfig;
	private final OnPremConfigBlob blobConfig;
	private final OnPremConfigWork workConfig;
	private final OnPremConfigAvatar avatarConfig;
	private final OnPremConfigSafe safeConfig;
	private final OnPremConfigWeb webConfig;
	private final OnPremConfigMediator mediatorConfig;

	public OnPremConfig(int refresh,
	                    OnPremLicense license,
	                    OnPremConfigChat chatConfig,
	                    OnPremConfigDirectory directoryConfig,
	                    OnPremConfigBlob blobConfig,
	                    OnPremConfigWork workConfig,
	                    OnPremConfigAvatar avatarConfig,
	                    OnPremConfigSafe safeConfig,
	                    @Nullable OnPremConfigWeb webConfig,
	                    @Nullable OnPremConfigMediator mediatorConfig) {
		this.refresh = refresh;
		this.chatConfig = chatConfig;
		this.license = license;
		this.directoryConfig = directoryConfig;
		this.blobConfig = blobConfig;
		this.workConfig = workConfig;
		this.avatarConfig = avatarConfig;
		this.safeConfig = safeConfig;
		this.webConfig = webConfig;
		this.mediatorConfig = mediatorConfig;
	}

	public int getRefresh() {
		return refresh;
	}

	public OnPremLicense getLicense() {
		return license;
	}

	public OnPremConfigChat getChatConfig() {
		return chatConfig;
	}

	public OnPremConfigDirectory getDirectoryConfig() {
		return directoryConfig;
	}

	public OnPremConfigBlob getBlobConfig() {
		return blobConfig;
	}

	public OnPremConfigWork getWorkConfig() {
		return workConfig;
	}

	public OnPremConfigAvatar getAvatarConfig() {
		return avatarConfig;
	}

	public OnPremConfigSafe getSafeConfig() {
		return safeConfig;
	}

	public @Nullable OnPremConfigWeb getWebConfig() {
		return webConfig;
	}

	public @Nullable OnPremConfigMediator getMediatorConfig() {
		return mediatorConfig;
	}
}
