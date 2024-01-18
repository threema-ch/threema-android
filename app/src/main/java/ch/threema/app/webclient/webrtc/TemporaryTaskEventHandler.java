/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.webclient.webrtc;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;

import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.events.MessageHandler;
import org.saltyrtc.tasks.webrtc.messages.Answer;
import org.saltyrtc.tasks.webrtc.messages.Candidate;
import org.saltyrtc.tasks.webrtc.messages.Offer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffers task events until they can be dispatched.
 */
@AnyThread
public class TemporaryTaskEventHandler implements MessageHandler {
	private static final Logger logger = LoggingUtil.getThreemaLogger("TemporaryTaskEventHandler");

	@NonNull final private List<Object> events = new ArrayList<>();
	@Nullable private MessageHandler handler;

	@Override
	public synchronized void onOffer(@NonNull final Offer offer) {
		if (this.handler != null) {
			this.handler.onOffer(offer);
		} else {
			this.events.add(offer);
		}
	}

	@Override
	public synchronized void onAnswer(@NonNull final Answer answer) {
		if (this.handler != null) {
			this.handler.onAnswer(answer);
		} else {
			this.events.add(answer);
		}
	}

	@Override
	public synchronized void onCandidates(@NonNull final Candidate[] candidates) {
		if (this.handler != null) {
			this.handler.onCandidates(candidates);
		} else {
			this.events.add(candidates);
		}
	}

	public synchronized void replace(@NonNull final WebRTCTask task, @NonNull final MessageHandler handler) {
		logger.debug("Flushing {} events", this.events.size());
		this.handler = handler;
		for (final Object event: this.events) {
			if (event instanceof Offer) {
				handler.onOffer((Offer) event);
			} else if (event instanceof Answer) {
				handler.onAnswer((Answer) event);
			} else if (event.getClass().isArray()) {
				handler.onCandidates((Candidate[]) event);
			} else {
				logger.error("Invalid buffered task event type: {}", event.getClass());
			}
		}

		logger.debug("Events flushed, replacing handler");
		this.events.clear();
		task.setMessageHandler(handler);
	}
}
