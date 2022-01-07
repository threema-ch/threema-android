/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.dialogs.GenericProgressDialog;

public class LoadingUtil {
	private static final Logger logger = LoggerFactory.getLogger(LocaleUtil.class);

	private static String DIALOG_TAG_PROGRESS_LOADINGUTIL = "lou";

	/**
	 * Run a {screen} in a thread and show a loading alert with {subjectId} and {textId}
	 * @param fragmentManager
	 * @param subjectId
	 * @param textId
	 * @param script
	 * @return
	 */
	public static Thread runInAlert(FragmentManager fragmentManager, int subjectId, int textId, final Runnable script) {
		GenericProgressDialog.newInstance(subjectId, textId).show(fragmentManager, DIALOG_TAG_PROGRESS_LOADINGUTIL);

		Thread t = new Thread(() -> {
			try {
				script.run();
			}
			catch (Exception x) {
				logger.error("Exception", x);
			}
			finally {
				RuntimeUtil.runOnUiThread(() -> DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_PROGRESS_LOADINGUTIL, true));
			}

		});

		t.start();
		return t;
	}
}
