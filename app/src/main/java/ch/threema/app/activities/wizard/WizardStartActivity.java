/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.activities.wizard;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.widget.ImageView;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Pair;
import ch.threema.app.R;
import ch.threema.app.ui.AnimationDrawableCallback;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.backuprestore.csv.RestoreService.RESTORE_COMPLETION_NOTIFICATION_ID;

public class WizardStartActivity extends WizardBackgroundActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardStartActivity");
    boolean nextActivityLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wizard_start);

        NotificationManagerCompat.from(this).cancel(RESTORE_COMPLETION_NOTIFICATION_ID);

        final ImageView imageView = findViewById(R.id.wizard_animation);
        final AnimationDrawable frameAnimation = getAnimationDrawable(imageView);

        if (!RuntimeUtil.isInTest() && !ConfigUtils.isWorkRestricted()) {
            imageView.setOnClickListener(v -> {
                ((AnimationDrawable) v.getBackground()).stop();
                launchNextActivity(null);
            });
            imageView.getRootView().getViewTreeObserver().addOnGlobalLayoutListener(frameAnimation::start);
            imageView.postDelayed(() -> {
                if (frameAnimation.isRunning()) {
                    // stop animation if it's still running after 5 seconds
                    frameAnimation.stop();
                    launchNextActivity(null);
                }
            }, 5000);
        } else {
            launchNextActivity(null);
        }
    }

    @NonNull
    private AnimationDrawable getAnimationDrawable(ImageView imageView) {
        final AnimationDrawable frameAnimation = (AnimationDrawable) imageView.getBackground();
        frameAnimation.setOneShot(true);
        frameAnimation.setCallback(new AnimationDrawableCallback(frameAnimation, imageView) {
            @Override
            public void onAnimationAdvanced(int currentFrame, int totalFrames) {
            }

            @Override
            public void onAnimationCompleted() {
                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    // the context of the activity
                    WizardStartActivity.this,

                    new Pair<>(findViewById(R.id.wizard_animation),
                        getString(R.string.transition_name_dots)),
                    new Pair<>(findViewById(R.id.wizard_footer),
                        getString(R.string.transition_name_logo))
                );
                launchNextActivity(options);
            }
        });
        return frameAnimation;
    }

    private synchronized void launchNextActivity(ActivityOptionsCompat options) {
        if (nextActivityLaunched) {
            // If the next activity already has been launched, we can just return here.
            return;
        }

        Intent intent;

        if (userService != null && userService.hasIdentity()) {
            intent = new Intent(this, WizardBaseActivity.class);
            options = null;
        } else {
            intent = new Intent(this, WizardIntroActivity.class);
        }

        if (options != null) {
            try {
                // can potentially cause a memory leak. still not fixed in the Android framework to this date
                // https://issuetracker.google.com/issues/37042900
                startActivity(intent, options.toBundle());
            } catch (Exception e) {
                // http://stackoverflow.com/questions/31026745/rjava-lang-illegalargumentexception-on-startactivityintent-bundle-animantion
                logger.error("Exception", e);
                startActivity(intent);
            }
        } else {
            startActivity(intent);
            overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
        }
        nextActivityLaunched = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (nextActivityLaunched) {
            finish();
        }
    }
}
