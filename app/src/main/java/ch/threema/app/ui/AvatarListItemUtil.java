/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.AvatarService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ReceiverModel;

public class AvatarListItemUtil {
	private static final Logger logger = LoggerFactory.getLogger(AvatarListItemUtil.class);

	public static void loadAvatar(
			final int position,
			final ConversationModel conversationModel,
			final Bitmap defaultImage,
			final Bitmap defaultGroupImage,
			final Bitmap defaultDistributionListImage,
			final ContactService contactService,
			final GroupService groupService,
			final DistributionListService distributionListService,
			AvatarListItemHolder holder) {

//		long time = System.currentTimeMillis();

		final boolean isWork;

		//do nothing
		if(holder.avatarLoadingAsyncTask != null) {
			//cancel async task
			holder.avatarLoadingAsyncTask.cancel(false);
			holder.avatarLoadingAsyncTask = null;
		}

		final AvatarService avatarService;
		final ReceiverModel avatarObject;
		if(conversationModel.isContactConversation()) {
			avatarService = contactService;
			avatarObject = conversationModel.getContact();
			isWork = contactService.showBadge(conversationModel.getContact());
			holder.avatarView.setContentDescription(
					ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
					ThreemaApplication.getAppContext().getString(R.string.mime_contact),
					NameUtil.getDisplayNameOrNickname(conversationModel.getContact(), true)));
		}
		else if(conversationModel.isGroupConversation()) {
			avatarService = groupService;
			avatarObject = conversationModel.getGroup();
			isWork = false;
			holder.avatarView.setContentDescription(
					ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
					ThreemaApplication.getAppContext().getString(R.string.group),
					NameUtil.getDisplayName(conversationModel.getGroup(), groupService)));
		}
		else if(conversationModel.isDistributionListConversation()) {
			avatarService = distributionListService;
			avatarObject = conversationModel.getDistributionList();
			isWork = false;
			holder.avatarView.setContentDescription(
					ThreemaApplication.getAppContext().getString(R.string.edit_type_content_description,
					ThreemaApplication.getAppContext().getString(R.string.distribution_list),
					NameUtil.getDisplayName(conversationModel.getDistributionList(), distributionListService)));
		}
		else {
			return;
		}

		if (!TestUtil.required(avatarService, avatarObject, holder)) {
			return;
		}

		//check the cache for existing avatar to avoid async task call
		if(show(holder, avatarService.getCachedAvatar(avatarObject))) {
			holder.avatarView.setBadgeVisible(isWork);

//			logger.debug("### cached avatar " + (System.currentTimeMillis() - time));

			return;
		}

		try {
			holder.avatarLoadingAsyncTask = new AsyncTask<AvatarListItemHolder, Void, Bitmap>() {
				private AvatarListItemHolder holder;

				@Override
				protected void onCancelled(Bitmap bitmap) {
					super.onCancelled(bitmap);
//				logger.debug("### IS CANCELLED");
				}

				@Override
				protected Bitmap doInBackground(AvatarListItemHolder... params) {
					this.holder = params[0];
					if (!isCancelled()) {
						return avatarService.getAvatar(avatarObject, false);
					}
					return null;
				}

				@Override
				protected void onPostExecute(Bitmap avatar) {
					//fix flickering
					if (!isCancelled()) {
						if (position == holder.position) {
							if (avatar == null) {
								if (conversationModel.isGroupConversation()) {
									avatar = defaultGroupImage;
								} else if (conversationModel.isDistributionListConversation()) {
									avatar = defaultDistributionListImage;
								} else {
									avatar = defaultImage;
								}
							}
							show(this.holder, avatar);
							holder.avatarView.setBadgeVisible(isWork);
							holder.avatarLoadingAsyncTask = null;

//						logger.debug("### new avatar " + (System.currentTimeMillis() - time));
						}
					}
				}
			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, prepare(position, holder, defaultImage));
		} catch (RejectedExecutionException e) {
			//thread pool is full - load by non thread
			Bitmap avatar = avatarService.getAvatar(avatarObject, false);
			if (avatar != null) {
				show(holder, avatar);
				holder.avatarView.setBadgeVisible(isWork);
			}
		}
	}

	private static <M extends ReceiverModel> void loadAvatarAbstract(
		final int position,
	    final M model,
	    final Bitmap defaultImage,
	    final AvatarService avatarService,
	    AvatarListItemHolder holder
	) {

		//do nothing
		if (!TestUtil.required(model, avatarService, holder) || holder.avatarView == null) {
			return;
		}

		if(holder.avatarLoadingAsyncTask != null) {
			holder.avatarLoadingAsyncTask.cancel(true);
		}

		if (model instanceof ContactModel) {
			holder.avatarView.setBadgeVisible(((ContactService) avatarService).showBadge((ContactModel) model));
		} else {
			holder.avatarView.setBadgeVisible(false);
		}

		//check the cache for existing avatar to avoid async task call
		if(show(holder, avatarService.getCachedAvatar(model))) {
			return;
		}

		holder.avatarLoadingAsyncTask = new AsyncTask<AvatarListItemHolder, Void, Bitmap>() {
			private AvatarListItemHolder holder;

			@Override
			protected Bitmap doInBackground(AvatarListItemHolder... params) {
				this.holder = params[0];

				return avatarService.getAvatar(model, false);
			}

			@Override
			protected void onPostExecute(Bitmap avatar) {
				if (position == holder.position) {
					if (avatar == null) {
						avatar = defaultImage;

					}
					show(this.holder, avatar);
				}
			}
		}.execute(prepare(position, holder, defaultImage));
	}
	public static void loadAvatar(final int position,
	                              final ContactModel contactModel,
	                              final Bitmap defaultImage,
	                              final ContactService contactService,
	                              AvatarListItemHolder holder) {
		loadAvatarAbstract(position, contactModel, defaultImage, contactService, holder);
	}

	public static void loadAvatar(final int position,
	                              final GroupModel groupModel,
	                              final Bitmap defaultImage,
	                              final GroupService groupService,
	                              AvatarListItemHolder holder) {
		loadAvatarAbstract(position, groupModel, defaultImage, groupService, holder);
	}

	public static void loadAvatar(final int position,
	                              final DistributionListModel distributionListModel,
	                              final Bitmap defaultImage,
	                              final DistributionListService distributionListService,
	                              AvatarListItemHolder holder) {
		loadAvatarAbstract(position, distributionListModel, defaultImage, distributionListService, holder);
	}

	private static boolean show(final AvatarListItemHolder holder, final Bitmap avatar) {
		if (avatar == null || avatar.isRecycled()) {
			return false;
		}

		holder.avatarView.setImageBitmap(avatar);
		holder.avatarView.setVisibility(View.VISIBLE);
		return true;
	}

	private static AvatarListItemHolder prepare(int position, AvatarListItemHolder holder, Bitmap defaultImage) {
		holder.position = position;
		show(holder, defaultImage);
		return holder;
	}

}
