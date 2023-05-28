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

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;

import ch.threema.base.utils.Base64;

public class OnPremConfigParserTest {
	private OnPremConfig config;

	@Before
	public void parseConfig() throws JSONException, IOException, ParseException, LicenseExpiredException {
		OnPremConfigParser parser = new OnPremConfigParser();
		this.config = parser.parse(new JSONObject(OnPremConfigTestData.TEST_GOOD_OPPF.substring(0, OnPremConfigTestData.TEST_GOOD_OPPF.lastIndexOf('\n'))));
	}

	@Test
	public void testExpiration() {
		OnPremConfigParser parser = new OnPremConfigParser();
		Assert.assertThrows(LicenseExpiredException.class, () -> {
			parser.parse(new JSONObject(OnPremConfigTestData.TEST_EXPIRED_OPPF.substring(0, OnPremConfigTestData.TEST_EXPIRED_OPPF.lastIndexOf('\n'))));
		});
	}

	@Test
	public void testRefresh() {
		Assert.assertEquals(86400, config.getRefresh());
	}

	@Test
	public void testChatConfig() throws IOException {
		OnPremConfigChat chatConfig = config.getChatConfig();
		Assert.assertEquals("chat.threemaonprem.initrode.com", chatConfig.getHostname());
		Assert.assertArrayEquals(new int[] {5222, 443}, chatConfig.getPorts());
		Assert.assertArrayEquals(Base64.decode("r9utIHN9ngo21q9OlZcotsQu1f2HwAW2Wi+u6Psp4Wc="), chatConfig.getPublicKey());
	}

	@Test
	public void testDirectoryConfig() {
		OnPremConfigDirectory directoryConfig = config.getDirectoryConfig();
		Assert.assertEquals("https://dir.threemaonprem.initrode.com/directory", directoryConfig.getUrl());
	}

	@Test
	public void testBlobConfig() {
		OnPremConfigBlob blobConfig = config.getBlobConfig();
		Assert.assertEquals("https://blob.threemaonprem.initrode.com/blob/upload", blobConfig.getUploadUrl());
		Assert.assertEquals("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}", blobConfig.getDownloadUrl());
		Assert.assertEquals("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done", blobConfig.getDoneUrl());
	}

	@Test
	public void testWorkConfig() {
		OnPremConfigWork workConfig = config.getWorkConfig();
		Assert.assertEquals("https://work.threemaonprem.initrode.com/", workConfig.getUrl());
	}

	@Test
	public void testAvatarConfig() {
		OnPremConfigAvatar avatarConfig = config.getAvatarConfig();
		Assert.assertEquals("https://avatar.threemaonprem.initrode.com/", avatarConfig.getUrl());
	}

	@Test
	public void testSafeConfig() {
		OnPremConfigSafe safeConfig = config.getSafeConfig();
		Assert.assertEquals("https://safe.threemaonprem.initrode.com/", safeConfig.getUrl());
	}

	@Test
	public void testWebConfig() {
		OnPremConfigWeb webConfig = config.getWebConfig();
		Assert.assertEquals("https://web.threemaonprem.initrode.com/", webConfig.getUrl());
	}

	@Test
	public void testMediatorConfig() {
		OnPremConfigMediator mediatorConfig = config.getMediatorConfig();
		Assert.assertEquals("https://mediator.threemaonprem.initrode.com/", mediatorConfig.getUrl());
		Assert.assertEquals("https://mediator.threemaonprem.initrode.com/blob/upload", mediatorConfig.getBlob().getUploadUrl());
		Assert.assertEquals("https://mediator.threemaonprem.initrode.com/blob/{blobId}", mediatorConfig.getBlob().getDownloadUrl());
		Assert.assertEquals("https://mediator.threemaonprem.initrode.com/blob/{blobId}/done", mediatorConfig.getBlob().getDoneUrl());
	}
}
