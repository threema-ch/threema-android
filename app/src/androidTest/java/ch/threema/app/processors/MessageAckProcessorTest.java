/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.app.processors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.MessageService;
import ch.threema.app.testutils.CaptureLogcatOnTestFailureRule;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MessageAckProcessorTest {
	// Test rules
	@Rule public CaptureLogcatOnTestFailureRule captureLogcatOnTestFailureRule = new CaptureLogcatOnTestFailureRule();

	// Services
	private MessageService messageService;

	// Message ack processor
	private MessageAckProcessor messageAckProcessor;

	@Before
	public void setUp() throws Exception {
		// Load services
		final ServiceManager serviceManager = Objects.requireNonNull(ThreemaApplication.getServiceManager());
		this.messageService = serviceManager.getMessageService();

		// Create processor
		this.messageAckProcessor = new MessageAckProcessor();
		this.messageAckProcessor.setMessageService(this.messageService);
	}

	/**
	 * Ensure that {@link MessageAckProcessor#wasRecentlyAcked(MessageId)} works.
	 */
	@Test
	public void wasRecentlyAcked() {
		final QueueMessageId queueMessageId = new QueueMessageId(new MessageId(), "09BNNVR2");
		Assert.assertFalse(this.messageAckProcessor.wasRecentlyAcked(queueMessageId.getMessageId()));
		this.messageAckProcessor.processAck(queueMessageId);
		Assert.assertTrue(this.messageAckProcessor.wasRecentlyAcked(queueMessageId.getMessageId()));
	}
}
