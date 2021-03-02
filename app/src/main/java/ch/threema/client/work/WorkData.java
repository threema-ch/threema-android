/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.client.work;

import java.util.ArrayList;
import java.util.List;

public class WorkData {
	public final List<WorkContact> workContacts = new ArrayList<>();
	public final WorkMDMSettings mdm = new WorkMDMSettings();
	public final WorkDirectorySettings directory = new WorkDirectorySettings();
	public final WorkOrganization organization = new WorkOrganization();
	public String logoDark;
	public String logoLight;
	public String supportUrl;
	public int checkInterval;

}
