/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.activities.ballot;

import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ballot.BallotModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class BallotWizardFragment0 extends BallotWizardFragment implements BallotWizardActivity.BallotWizardCallback {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BallotWizardFragment0");

    private EditText editText;
    private TextInputLayout textInputLayout;
    private CheckBox secretCheckbox;
    private CheckBox typeCheckbox;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        ViewGroup rootView = (ViewGroup) inflater.inflate(
            R.layout.fragment_ballot_wizard0, container, false);

        this.editText = rootView.findViewById(R.id.wizard_edittext);
        this.editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == getResources().getInteger(R.integer.ime_wizard_next) || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (getBallotActivity() != null) {
                        getBallotActivity().nextPage();
                    }
                }
                return false;
            }
        });
        this.editText.addTextChangedListener(new SimpleTextWatcher() {
            public void afterTextChanged(@NonNull Editable editable) {
                if (getBallotActivity() != null) {
                    getBallotActivity().setBallotDescription(editText.getText().toString());
                }
                if (editable.length() > 0) {
                    textInputLayout.setError(null);
                }
            }
        });

        this.textInputLayout = rootView.findViewById(R.id.wizard_edittext_layout);

        this.typeCheckbox = rootView.findViewById(R.id.type);
        this.typeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getBallotActivity() != null) {
                    getBallotActivity().setBallotAssessment(
                        isChecked ? BallotModel.Assessment.MULTIPLE_CHOICE : BallotModel.Assessment.SINGLE_CHOICE
                    );
                }
            }
        });
        this.secretCheckbox = rootView.findViewById(R.id.visibility);
        this.secretCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getBallotActivity() != null) {
                    getBallotActivity().setBallotType(
                        isChecked ? BallotModel.Type.INTERMEDIATE : BallotModel.Type.RESULT_ON_CLOSE
                    );
                }
            }
        });

        this.updateView();
        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public void updateView() {
        if (getBallotActivity() != null) {
            ViewUtil.showAndSet(this.editText,
                this.getBallotActivity().getBallotDescription());
        }
        if (this.getBallotActivity() != null) {
            ViewUtil.showAndSet(this.typeCheckbox,
                this.getBallotActivity().getBallotAssessment() == BallotModel.Assessment.MULTIPLE_CHOICE);

            ViewUtil.showAndSet(this.secretCheckbox,
                this.getBallotActivity().getBallotType() == BallotModel.Type.INTERMEDIATE);
        }
    }

    @Override
    public void onMissingTitle() {
        this.textInputLayout.setError(getString(R.string.title_cannot_be_empty));
        this.editText.setFocusableInTouchMode(true);
        this.editText.setFocusable(true);
        this.editText.requestFocus();
    }

    @Override
    public void onPageSelected(int page) {
        if (page == 1) {
            this.editText.clearFocus();
            this.editText.setFocusableInTouchMode(false);
            this.editText.setFocusable(false);
        } else {
            this.editText.setFocusableInTouchMode(true);
            this.editText.setFocusable(true);
        }
    }
}
