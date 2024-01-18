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

package ch.threema.app.activities.ballot;

import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.storage.models.ballot.BallotModel;

abstract class BallotDetailActivity extends ThreemaToolbarActivity {
	private BallotModel ballotModel = null;

	interface ServiceCall {
		boolean call(BallotService service);
	}

	protected boolean setBallotModel(final BallotModel ballotModel) {
		this.ballotModel = ballotModel;
		this.updateViewState();

		return this.ballotModel != null;
	}

	protected BallotModel getBallotModel() {
		return this.ballotModel;
	}

	protected Integer getBallotModelId() {
		if(this.ballotModel != null) {
			return this.ballotModel.getId();
		}

		return null;
	}

	private void updateViewState() {
		if(this.ballotModel != null) {
			this.callService(new ServiceCall() {
				@Override
				public boolean call(BallotService service) {
					service.viewingBallot(ballotModel, true);
					return true;
				}
			});
		}
	}
	@Override
	public void onResume() {
		super.onResume();
		if(this.ballotModel != null) {
			this.callService(new ServiceCall() {
				@Override
				public boolean call(BallotService service) {
					service.viewingBallot(ballotModel, true);
					return true;
				}
			});
		}
	}

	@Override
	public void onPause() {
		if(this.ballotModel != null) {
			this.callService(new ServiceCall() {
				@Override
				public boolean call(BallotService service) {
					service.viewingBallot(ballotModel, false);
					return true;
				}
			});
		}
		super.onPause();
	}

	private boolean callService(ServiceCall serviceCall) {
		if(serviceCall != null) {
			BallotService s = this.getBallotService();
			if (s != null) {
				return serviceCall.call(s);
			}
		}

		return false;
	}

	abstract BallotService getBallotService();
	abstract ContactService getContactService();
	abstract GroupService getGroupService();
	abstract String getIdentity();

}
