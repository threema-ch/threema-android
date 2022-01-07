/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.storage.models.ServerMessageModel;

public class ServerMessageActivity extends ThreemaActivity {
	ServerMessageModel serverMessageModel;

	public void onCreate(Bundle savedInstanceState) {
		ConfigUtils.configureActivityTheme(this);

		super.onCreate(savedInstanceState);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.warning);
		}

		setContentView(R.layout.activity_server_message);
		this.serverMessageModel = IntentDataUtil.getServerMessageModel(this.getIntent());

		String message = this.serverMessageModel.getMessage();

		if (message == null) {
			finish();
			return;
		}

		if (message.startsWith("Another connection")) {
			message = getString(R.string.another_connection_instructions, getString(R.string.app_name));
		}

		((TextView)findViewById(R.id.server_message_text)).setText(message);
		findViewById(R.id.close_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}

}
