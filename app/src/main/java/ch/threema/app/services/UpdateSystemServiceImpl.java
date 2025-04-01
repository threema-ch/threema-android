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

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;

public class UpdateSystemServiceImpl implements UpdateSystemService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("UpdateSystemServiceImpl");
    private final Queue<SystemUpdate> systemUpdates = new LinkedList<>();

    @Override
    public void addUpdate(@NonNull SystemUpdate systemUpdate) {
        //run directly
        try {
            logger.info("Run direct system update to {}", systemUpdate.getText());
            systemUpdate.runDirectly();
        } catch (SQLException e) {
            throw new RuntimeException();
        }

        //add to queue to run a sync in a queue
        this.systemUpdates.add(systemUpdate);
    }

    @Override
    public void update(@Nullable OnSystemUpdateRun onSystemUpdateRun) {
        while (!this.systemUpdates.isEmpty()) {
            final SystemUpdate update = this.systemUpdates.remove();

            if (onSystemUpdateRun != null) {
                onSystemUpdateRun.onStart(update);
            }

            boolean success = update.runAsync();

            if (onSystemUpdateRun != null) {
                onSystemUpdateRun.onFinished(update, success);
            }
        }

    }

    @Override
    public boolean hasUpdates() {
        return !this.systemUpdates.isEmpty();
    }
}
