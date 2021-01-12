/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package androidx.core.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FixedJobIntentService extends JobIntentService {
	private static final Logger logger = LoggerFactory.getLogger(FixedJobIntentService.class);

	@Override
	GenericWorkItem dequeueWork() {
		// See https://medium.com/@mohamed.zak/workaround-to-solve-securityexception-caused-by-jobintentservice-1f4b0e688a26
		try {
			return super.dequeueWork();
		} catch (SecurityException exception) {
			logger.debug("Ignoring SecurityException", exception);
		}
		return null;
	}
}
