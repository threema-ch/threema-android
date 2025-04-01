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

package ch.threema.app.services;

import java.sql.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface UpdateSystemService {
    /**
     * Every system update must implement this interface.
     */
    interface SystemUpdate {
        /**
         * This method will be run synchronously when opening the database.
         * <p>
         * If you want to do database schema updates, this is where you should implement them!
         * <p>
         * Note: All system updates are processed sequentially, i.e. "SystemUpdateToVersion56"
         * will be processed before "SystemUpdateToVersion57".
         */
        boolean runDirectly() throws SQLException;

        /**
         * This method will be run asynchronously when opening the
         * {@link ch.threema.app.activities.HomeActivity}.
         * <p>
         * WARNING: Not currently guaranteed to run! See ANDR-2736.
         */
        boolean runAsync();

        /**
         * A string that describes this update.
         * <p>
         * Usually it should contain something like "version 42".
         */
        String getText();
    }

    /**
     * Register a system update.
     * <p>
     * The {@link SystemUpdate#runDirectly} method will be run directly (and synchronously).
     */
    void addUpdate(@NonNull SystemUpdate systemUpdate);

    interface OnSystemUpdateRun {
        /**
         * This method will be called before running the ({@link SystemUpdate#runAsync} method of
         * the specified {@link SystemUpdate}.
         */
        void onStart(@NonNull SystemUpdate systemUpdate);

        /**
         * This method will be called after running the ({@link SystemUpdate#runAsync} method of
         * the specified {@link SystemUpdate}.
         */
        void onFinished(@NonNull SystemUpdate systemUpdate, boolean success);
    }

    /**
     * Run async updates.
     */
    void update(@Nullable OnSystemUpdateRun onSystemUpdateRun);

    /**
     * Return whether or not there are updates.
     */
    boolean hasUpdates();
}
