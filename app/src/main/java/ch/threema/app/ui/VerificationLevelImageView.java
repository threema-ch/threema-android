/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.storage.models.ContactModel;

public class VerificationLevelImageView extends androidx.appcompat.widget.AppCompatImageView {

	private final Context context;

	public VerificationLevelImageView(Context context) {
		super(context);
		this.context = context;
	}

	public VerificationLevelImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}

	public VerificationLevelImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		this.context = context;
	}

	/**
	 * Sets the view to the provided verification levels.
	 */
	public void setVerificationLevel(
		@NonNull VerificationLevel verificationLevel,
		@NonNull WorkVerificationLevel workVerificationLevel
	) {
		setContentDescription(
			getVerificationLevelDescription(
				verificationLevel, workVerificationLevel
			)
		);
		setImageDrawable(
			ContactUtil.getVerificationDrawable(
				context,
				verificationLevel,
				workVerificationLevel
			)
		);
	}

	/**
	 * Get the verification level description from the given verification level. This also depends
	 * on the build and whether the contact is a work contact or not.
	 *
	 * @return String defined text in strings.xml for the according verification level
	 */
	private @NonNull String getVerificationLevelDescription(
		@NonNull VerificationLevel verificationLevel,
		@NonNull WorkVerificationLevel workVerificationLevel
	) {
		boolean isWorkVerifiedOnWorkBuild = ConfigUtils.isWorkBuild()
			&& workVerificationLevel == WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED;
		switch (verificationLevel) {
			case FULLY_VERIFIED:
				if (isWorkVerifiedOnWorkBuild) {
					return context.getString(R.string.verification_level3_work_explain);
				} else {
					return context.getString(R.string.verification_level3_explain);
				}
			case SERVER_VERIFIED:
				if (isWorkVerifiedOnWorkBuild) {
					return context.getString(R.string.verification_level2_work_explain);
				}
				return context.getString(R.string.verification_level2_explain);
			default:
				return context.getString(R.string.verification_level1_explain);
		}
	}

}
