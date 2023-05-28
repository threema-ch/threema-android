/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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

package ch.threema.app.ui;

import android.text.style.ClickableSpan;
import android.view.View;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.base.utils.LoggingUtil;

public class MentionClickableSpan extends ClickableSpan {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MentionClickableSpan");
	private String text;

	public MentionClickableSpan(String text){
		super();
		this.text = text;
	}

	public String getText() {
		return this.text;
	}

	@Override
	public void onClick(@NonNull View widget) {
		logger.debug("onClick");
	}
}
