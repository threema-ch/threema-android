/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import java.util.LinkedList;
import java.util.Queue;

public class UpdateSystemServiceImpl implements UpdateSystemService {
	private Queue<SystemUpdate> systemUpdates  = new LinkedList<SystemUpdate>();

	@Override
	public void addUpdate(SystemUpdate systemUpdate) {
		//run directly
		try {
			systemUpdate.runDirectly();
		} catch (SQLException e) {
			throw new RuntimeException();
		}

		//add to queue to run a sync in a queue
		this.systemUpdates.add(systemUpdate);
	}

	@Override
	public void update(OnSystemUpdateRun onSystemUpdateRun) {
			while(this.systemUpdates.size() > 0) {
				SystemUpdate update = this.systemUpdates.poll();

				if(onSystemUpdateRun != null) {
					onSystemUpdateRun.onStart(update);
				}

				boolean success = update.runASync();

				if(onSystemUpdateRun != null) {
					onSystemUpdateRun.onFinished(update, success);
				}
		}

	}


	@Override
	public void update() {
		this.update(null);
	}

	@Override
	public boolean hasUpdates() {
		return this.systemUpdates.size() > 0;
	}

	@Override
	public void prepareForTest() {
		this.systemUpdates.clear();

		for(int i = 0; i < 10; i++) {
			final String name = "test script " + String.valueOf(i);
			this.addUpdate(new SystemUpdate() {
				@Override
				public boolean runASync() {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						//do nothing
					}
					return true;
				}

				@Override
				public boolean runDirectly() {
					return true;
				}

				@Override
				public String getText() {
					return name;
				}
			});
		}
	}
}
