/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

package ch.threema.app.fragments.wizard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ch.threema.app.R;

public class WizardFragment0 extends WizardFragment {
	public static final int PAGE_ID = 0;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = super.onCreateView(inflater, container, savedInstanceState);

		// inflate content layout
		contentViewStub.setLayoutResource(R.layout.fragment_wizard0);
		contentViewStub.inflate();

		TextView tv = rootView.findViewById(R.id.wizard_id_title);
		tv.setText(this.userService.getIdentity());

		return rootView;
	}

	@Override
	protected int getAdditionalInfoText() {
		return R.string.new_wizard_info_id;
	}
}
