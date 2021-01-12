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

package ch.threema.app.services;

import android.graphics.Bitmap;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.utils.NameUtil;
import ch.threema.client.Base32;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListServiceImpl implements DistributionListService {

	private final CacheService cacheService;
	private final AvatarCacheService avatarCacheService;
	private final DatabaseServiceNew databaseServiceNew;
	private final ContactService contactService;

	public DistributionListServiceImpl(
			CacheService cacheService,
			AvatarCacheService avatarCacheService,
			DatabaseServiceNew databaseServiceNew,
			ContactService contactService
	)
	{
		this.cacheService = cacheService;
		this.avatarCacheService = avatarCacheService;
		this.databaseServiceNew = databaseServiceNew;
		this.contactService = contactService;
	}

	@Override
	public DistributionListModel getById(int id) {
		return this.databaseServiceNew.getDistributionListModelFactory().getById(
				id
		);
	}

	@Override
	public DistributionListModel createDistributionList(String name, String[] memberIdentities) {
		final DistributionListModel distributionListModel = new DistributionListModel();
		distributionListModel.setName(name);
		distributionListModel.setCreatedAt(new Date());

		//create
		this.databaseServiceNew.getDistributionListModelFactory().create(
				distributionListModel
		);


		for(String identity: memberIdentities) {
			this.addMemberToDistributionList(distributionListModel, identity);
		}

		ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
			@Override
			public void handle(DistributionListListener listener) {
				listener.onCreate(distributionListModel);
			}
		});
		return distributionListModel;
	}

	@Override
	public DistributionListModel updateDistributionList(final DistributionListModel distributionListModel, String name, String[] memberIdentities) {
		distributionListModel.setName(name);

		//create
		this.databaseServiceNew.getDistributionListModelFactory().update(
				distributionListModel
		);

		if(this.removeMembers(distributionListModel)) {
			for (String identity : memberIdentities) {
				this.addMemberToDistributionList(distributionListModel, identity);
			}
		}

		ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
			@Override
			public void handle(DistributionListListener listener) {
				listener.onModify(distributionListModel);
			}
		});
		return distributionListModel;
	}

	@Override
	@Nullable
	public Bitmap getCachedAvatar(DistributionListModel o) {
		if(o == null) {
			return null;
		}

		return this.avatarCacheService.getDistributionListAvatarLowFromCache(o);
	}

	@Override
	public Bitmap getAvatar(DistributionListModel model, boolean highResolution) {
		if (model == null) {
			return null;
		}

		int colors[] = this.cacheService.getDistributionListColors(model, false, new CacheService.CreateCachedColorList() {
			@Override
			public int[] create() {
				Collection<ContactModel> coloredMembers = Functional.filter(getMembers(model), new IPredicateNonNull<ContactModel>() {
					@Override
					public boolean apply(@NonNull ContactModel type) {
						return type.getColor() != 0;
					}
				});

				int[] colors = new int[coloredMembers.size()];
				int n = 0;
				for(ContactModel contactModel: coloredMembers) {
					colors[n++] = contactModel.getColor();
				}
				return colors;
			}
		});
		return this.avatarCacheService.getDistributionListAvatarLow(model, colors);
	}

	@Override
	public boolean addMemberToDistributionList(DistributionListModel distributionListModel, String identity) {
		DistributionListMemberModel distributionListMemberModel = this.databaseServiceNew.getDistributionListMemberModelFactory().getByDistributionListIdAndIdentity(
				distributionListModel.getId(),
				identity
		);
		if(distributionListMemberModel == null) {
			distributionListMemberModel = new DistributionListMemberModel();
		}
		distributionListMemberModel
				.setDistributionListId(distributionListModel.getId())
				.setIdentity(identity)
				.setActive(true);

		if(distributionListMemberModel.getId() > 0) {
			this.databaseServiceNew.getDistributionListMemberModelFactory().update(
					distributionListMemberModel
			);
		}
		else {
			this.databaseServiceNew.getDistributionListMemberModelFactory().create(
					distributionListMemberModel
			);
		}
		return true;
	}

	@Override
	public boolean remove(final DistributionListModel distributionListModel) {
		if(!this.removeMembers(distributionListModel)) {
			return false;
		}
		//remove list
		this.databaseServiceNew.getDistributionListModelFactory().delete(
				distributionListModel
		);

		ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
			@Override
			public void handle(DistributionListListener listener) {
				listener.onRemove(distributionListModel);
			}
		});

		return true;
	}

	private boolean removeMembers(DistributionListModel distributionListModel) {
		//remove all members first
		this.databaseServiceNew.getDistributionListMemberModelFactory().deleteByDistributionListId(
				distributionListModel.getId());

		return true;
	}

	@Override
	public boolean removeAll() {
		//remove all members first
		this.databaseServiceNew.getDistributionListMemberModelFactory().deleteAll();

		//...  messages
		this.databaseServiceNew.getDistributionListMessageModelFactory().deleteAll();

		//.. remove lists
		this.databaseServiceNew.getDistributionListModelFactory().deleteAll();

		return true;
	}

	@Override
	public String[] getDistributionListIdentities(DistributionListModel distributionListModel) {
		List<DistributionListMemberModel> memberModels = this.getDistributionListMembers(distributionListModel);
		if(memberModels != null) {
			String[] res = new String[memberModels.size()];
			for(int n = 0; n < res.length; n++) {
				res[n] = memberModels.get(n).getIdentity();
			}
			return res;
		}

		return null;
	}


	@Override
	public List<DistributionListMemberModel> getDistributionListMembers(DistributionListModel distributionListModel) {
		return this.databaseServiceNew.getDistributionListMemberModelFactory().getByDistributionListId(
				distributionListModel.getId()
		);
	}

	@Override
	public List<DistributionListModel> getAll() {
		return this.getAll(null);
	}

	@Override
	public List<DistributionListModel> getAll(DistributionListFilter filter) {
		return this.databaseServiceNew.getDistributionListModelFactory().filter(
				filter
		);
	}

	@Override
	public List<ContactModel> getMembers(DistributionListModel distributionListModel) {
		List<ContactModel> contactModels = new ArrayList<>();
		if (distributionListModel != null) {
			for (DistributionListMemberModel distributionListMemberModel : this.getDistributionListMembers(distributionListModel)) {
				ContactModel contactModel = this.contactService.getByIdentity(distributionListMemberModel.getIdentity());
				if (contactModel != null) {
					contactModels.add(contactModel);
				}
			}
		}
		return contactModels;
	}

	@Override
	public String getMembersString(DistributionListModel distributionListModel) {
		StringBuilder builder = new StringBuilder();
		for(ContactModel contactModel: this.getMembers(distributionListModel)) {
			if(builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(NameUtil.getDisplayNameOrNickname(contactModel, true));
		}
		return builder.toString();
	}

	@Override
	public DistributionListMessageReceiver createReceiver(DistributionListModel distributionListModel) {
		return new DistributionListMessageReceiver(
				this.databaseServiceNew,
				this.contactService,
				distributionListModel,
				this);
	}

	@Override
	public String getUniqueIdString(DistributionListModel distributionListModel) {
		if (distributionListModel != null) {
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				messageDigest.update(("d-" + String.valueOf(distributionListModel.getId())).getBytes());
				return Base32.encode(messageDigest.digest());
			} catch (NoSuchAlgorithmException e) {
				//
			}
		}
		return "";
	}

	@Override
	public int getPrimaryColor(DistributionListModel distributionListModel) {
		if(distributionListModel != null) {
			//get members
			List<ContactModel> contactModels = this.getMembers(distributionListModel);
			if(contactModels.size() > 0) {
				return contactModels.get(0).getColor();
			}
		}
		return 0;
	}

	@Override
	public void setIsArchived(DistributionListModel distributionListModel, boolean archived) {
		if (distributionListModel != null && distributionListModel.isArchived() != archived) {
			distributionListModel.setArchived(archived);
			save(distributionListModel);

			ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
				@Override
				public void handle(DistributionListListener listener) {
					listener.onModify(distributionListModel);
				}
			});
		}
	}

	private void save(DistributionListModel distributionListModel) {
		this.databaseServiceNew.getDistributionListModelFactory().createOrUpdate(
			distributionListModel
		);
	}
}
