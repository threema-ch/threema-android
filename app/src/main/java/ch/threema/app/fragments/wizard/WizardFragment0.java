/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import java.util.Objects;

import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardBaseActivity;

public class WizardFragment0 extends WizardFragment {
    public static final int PAGE_ID = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = Objects.requireNonNull(super.onCreateView(inflater, container, savedInstanceState));

        TextView title = rootView.findViewById(R.id.wizard_title);

        // inflate content layout
        contentViewStub.setLayoutResource(R.layout.fragment_wizard0);
        contentViewStub.inflate();

        TextView idTitle = rootView.findViewById(R.id.wizard_id_title);
        idTitle.setText(this.userService.getIdentity());

        if (((WizardBaseActivity) getActivity()).isNewIdentity()) {
            title.setText(R.string.new_wizard_welcome);
        } else {
            title.setText(R.string.welcome_back);
            ((TextView) rootView.findViewById(R.id.scooter)).setText(R.string.id_restored_successfully);
            rootView.findViewById(R.id.wizard_id_explain).setVisibility(View.GONE);
        }

        return rootView;
    }

    @Override
    protected int getAdditionalInfoText() {
        return R.string.new_wizard_info_id;
    }
}
