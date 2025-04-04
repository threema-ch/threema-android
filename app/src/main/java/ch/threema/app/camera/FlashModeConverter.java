/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.threema.app.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageCapture.FlashMode;

import static androidx.camera.core.ImageCapture.FLASH_MODE_AUTO;
import static androidx.camera.core.ImageCapture.FLASH_MODE_OFF;
import static androidx.camera.core.ImageCapture.FLASH_MODE_ON;

/**
 * Helper class that defines certain enum-like methods for {@link FlashMode}
 */
final class FlashModeConverter {

    private FlashModeConverter() {
    }

    /**
     * Returns the {@link FlashMode} constant for the specified name
     *
     * @param name The name of the {@link FlashMode} to return
     * @return The {@link FlashMode} constant for the specified name
     */
    @FlashMode
    public static int valueOf(@Nullable final String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        switch (name) {
            case "AUTO":
                return FLASH_MODE_AUTO;
            case "ON":
                return FLASH_MODE_ON;
            case "OFF":
                return FLASH_MODE_OFF;
            default:
                throw new IllegalArgumentException("Unknown flash mode name " + name);
        }
    }

    /**
     * Returns the name of the {@link FlashMode} constant, exactly as it is declared.
     *
     * @param flashMode A {@link FlashMode} constant
     * @return The name of the {@link FlashMode} constant.
     */
    @NonNull
    public static String nameOf(@FlashMode final int flashMode) {
        switch (flashMode) {
            case FLASH_MODE_AUTO:
                return "AUTO";
            case FLASH_MODE_ON:
                return "ON";
            case FLASH_MODE_OFF:
                return "OFF";
            default:
                throw new IllegalArgumentException("Unknown flash mode " + flashMode);
        }
    }
}
