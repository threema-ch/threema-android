/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.testutils;

import android.app.Activity;

import com.azimolabs.conditionwatcher.Instruction;

import androidx.test.InstrumentationRegistry;
import ch.threema.app.TestApplication;

public class InstructionUtil {
    private InstructionUtil() {
    }

    public static Instruction waitForView(final int resourceId) {
        return new Instruction() {
            @Override
            public String getDescription() {
                return "wait for " + resourceId;
            }

            @Override
            public boolean checkCondition() {
                Activity activity = ((TestApplication)
                    InstrumentationRegistry.getTargetContext().getApplicationContext()).getCurrentActivity();
                if (activity == null) {
                    return false;
                }

                return activity.findViewById(resourceId) != null;
            }
        };
    }

}
