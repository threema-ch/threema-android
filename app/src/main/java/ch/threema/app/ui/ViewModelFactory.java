/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import ch.threema.app.grouplinks.GroupLinkViewModel;
import ch.threema.app.grouplinks.IncomingGroupRequestViewModel;
import ch.threema.domain.models.GroupId;

public class ViewModelFactory extends ViewModelProvider.NewInstanceFactory {
	private Object[] params;

	public ViewModelFactory(Object... params) {
		this.params = params;
	}

	@Override
	public <T extends ViewModel> T create(Class<T> modelClass) {
		if (modelClass == GroupLinkViewModel.class) {
			return (T) new GroupLinkViewModel((GroupId) this.params[0]);
		} // extend for more view model types
		else if (modelClass == IncomingGroupRequestViewModel.class) {
			return (T) new IncomingGroupRequestViewModel((GroupId)this.params[0]);
		}
		 else {
			return super.create(modelClass);
		}
	}
}
