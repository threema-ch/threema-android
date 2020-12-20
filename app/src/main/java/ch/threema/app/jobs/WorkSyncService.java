/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

package ch.threema.app.jobs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.UpdateAppLogoRoutine;
import ch.threema.app.routines.UpdateWorkInfoRoutine;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.VerificationLevel;
import ch.threema.client.APIConnector;
import ch.threema.client.work.WorkContact;
import ch.threema.client.work.WorkData;
import ch.threema.storage.models.ContactModel;

public class WorkSyncService extends FixedJobIntentService {
	private static final Logger logger = LoggerFactory.getLogger(WorkSyncService.class);

	private static final int JOB_ID = 2004;
	public static final String EXTRA_WORK_UPDATE_RESTRICTIONS_ONLY = "reon";

	private static boolean isRunning;
	private static boolean forceUpdate = false;

	private ServiceManager serviceManager;
	private ContactService contactService;
	private PreferenceService preferenceService;
	private FileService fileService;
	private LicenseService licenseService;
	private APIConnector apiConnector;
	private NotificationService notificationService;
	private UserService userService;
	private IdentityStore identityStore;

	@Override
	public void onCreate() {
		super.onCreate();

		isRunning = true;

		try {
			serviceManager = ThreemaApplication.getServiceManager();
			contactService = serviceManager.getContactService();
			preferenceService = serviceManager.getPreferenceService();
			licenseService = serviceManager.getLicenseService();
			fileService = serviceManager.getFileService();
			notificationService = serviceManager.getNotificationService();
			userService = serviceManager.getUserService();
			apiConnector = serviceManager.getAPIConnector();
			identityStore = serviceManager.getIdentityStore();
		} catch (Exception e) {
			//
		}
	}

	@Override
	public void onDestroy() {
		isRunning = false;

		super.onDestroy();
	}

	/**
	 * Convenience method for enqueuing work in to this service.
	 */
	public static void enqueueWork(Context context, Intent work, boolean force) {
		logger.trace("enqueueWork");
		if (isRunning()) return;

		forceUpdate = force;
		logger.trace("forceUpdate = " + forceUpdate);

		enqueueWork(context, WorkSyncService.class, JOB_ID, work);
	}

	public static boolean isRunning() {
		return isRunning;
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		logger.trace("onHandleWork");

		if(!ConfigUtils.isWorkBuild()) {
			logger.error("Not allowed to run routine in a non work environment");
			return;
		}

		if (this.licenseService == null) {
			logger.trace("license service not available");
			return;
		}

		LicenseService.Credentials credentials = this.licenseService.loadCredentials();
		if(!(credentials instanceof UserCredentials)) {
			return;
		}

		logger.trace("showNotification");
		notificationService.showWorkSyncProgress();

		if (!intent.getBooleanExtra(EXTRA_WORK_UPDATE_RESTRICTIONS_ONLY, false)) {
			WorkData workData = null;
			try {
				List<ContactModel> allContacts = this.contactService.getAll(true, true);
				String[] identities = new String[allContacts.size()];
				for (int n = 0; n < allContacts.size(); n++) {
					identities[n] = allContacts.get(n).getIdentity();
				}
				workData = this.apiConnector
					.fetchWorkData(((UserCredentials) credentials).username,
						((UserCredentials) credentials).password,
						identities);
			} catch (Exception e) {
				logger.error("Exception", e);
				notificationService.cancelWorkSyncProgress();
				return;
			}

			if (workData != null) {
/*				logger.trace("workData contacts size = " + workData.workContacts.size());

				logger.debug("data found");
				logger.debug("checkInterval: " + workData.checkInterval);
				logger.debug("supportUrl: " + (null != workData.supportUrl ? "yes" : "no"));
				logger.debug("logos: " + ((null != workData.logoDark ? 1 : 0) + (null != workData.logoLight ? 1 : 0)));
				//get all saved work contacts
				logger.debug("contacts: " + workData.workContacts.size());
*/				List<ContactModel> existingWorkContacts = this.contactService.getIsWork();

				boolean requireCheckMultipleIdentities = false;
				for (WorkContact workContact : workData.workContacts) {

					ContactModel existingContact = this.contactService.getByIdentity(workContact.threemaId);

					if (existingContact == null) {
						existingContact = new ContactModel(workContact.threemaId, workContact.publicKey);
						requireCheckMultipleIdentities = true;
					} else {
						//try to remove
						for (int x = 0; x < existingWorkContacts.size(); x++) {
							if (existingWorkContacts.get(x).getIdentity().equals(workContact.threemaId)) {
								existingWorkContacts.remove(x);
								break;
							}
						}
					}

					if (!ContactUtil.isLinked(existingContact)
						&& (workContact.firstName != null
						|| workContact.lastName != null)) {
						existingContact.setFirstName(workContact.firstName);
						existingContact.setLastName(workContact.lastName);
					}
					existingContact.setIsWork(true);
					existingContact.setIsHidden(false);
					if (existingContact.getVerificationLevel() != VerificationLevel.FULLY_VERIFIED) {
						existingContact.setVerificationLevel(VerificationLevel.SERVER_VERIFIED);
					}
					this.contactService.save(existingContact);
				}

				//downgrade work contacts
				for (int x = 0; x < existingWorkContacts.size(); x++) {
					//remove isWork flag
					ContactModel c = existingWorkContacts.get(x);
					c.setIsWork(false);
					if (c.getVerificationLevel() != VerificationLevel.FULLY_VERIFIED) {
						c.setVerificationLevel(VerificationLevel.UNVERIFIED);
					}
					this.contactService.save(c);
				}

				// update applogos
				// start a new thread to lazy download the app icons
				logger.trace("start update app icon routine");
				new Thread(new UpdateAppLogoRoutine(
					this.fileService,
					this.preferenceService,
					workData.logoLight,
					workData.logoDark,
					forceUpdate
				), "UpdateAppIcon").start();

				this.preferenceService.setCustomSupportUrl(workData.supportUrl);

				if (workData.mdm.parameters != null) {
					// Save the Mini-MDM Parameters to a local file
					AppRestrictionService.getInstance()
						.storeWorkMDMSettings(workData.mdm);
				}

				// Check identity for type and state (only if new contacts added)
				if (requireCheckMultipleIdentities) {
					// force run
					logger.debug("force run CheckIdentityStatesRoutine");
					// TODO
//					CheckIdentityStatesRoutine.start(true);
				}

				// update work info
				new UpdateWorkInfoRoutine(
					this,
					this.apiConnector,
					this.identityStore,
					null,
					this.licenseService
				).run();

				this.preferenceService.setWorkDirectoryEnabled(workData.directory.enabled);
				this.preferenceService.setWorkDirectoryCategories(workData.directory.categories);
				this.preferenceService.setWorkOrganization(workData.organization);

				logger.trace("workData checkInterval = " + workData.checkInterval);

				if (workData.checkInterval > 0) {
					//schedule next interval
					this.preferenceService.setWorkSyncCheckInterval(workData.checkInterval);
				}
			} else {
				logger.trace("workData == null");

				this.preferenceService.clearAppLogos();
			}
		}

		resetRestrictions();

		notificationService.cancelWorkSyncProgress();
		logger.trace("deleteNotification");

	}

	private void resetRestrictions() {
		/* note that PreferenceService may not be available at this time */
		logger.debug("resetRestrictions");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (sharedPreferences != null) {
			SharedPreferences.Editor editor = sharedPreferences.edit();

			if (editor != null) {
				Boolean preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__block_unknown));
				if (preset != null) {
					editor.putBoolean(getString(R.string.preferences__block_unknown), preset);
				}
				preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_screenshots));
				if (preset != null) {
					editor.putBoolean(getString(R.string.preferences__hide_screenshots), preset);
				}
				preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_save_to_gallery));
				if (preset != null) {
					editor.putBoolean(getString(R.string.preferences__save_media), !preset);
				}
				preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_message_preview));
				if (preset != null) {
					editor.putBoolean(getString(R.string.preferences__notification_preview), !preset);
				}
				preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_send_profile_picture));
				if (preset != null) {
					editor.putInt(getString(R.string.preferences__profile_pic_release), preset ? PreferenceService.PROFILEPIC_RELEASE_NOBODY : PreferenceService.PROFILEPIC_RELEASE_EVERYONE);
				}
				preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_calls));
				if (preset != null) {
					editor.putBoolean(getString(R.string.preferences__voip_enable), !preset);
				}
				preset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__hide_inactive_ids));
				if (preset != null) {
					editor.putBoolean(getString(R.string.preferences__show_inactive_contacts), !preset);
				}
				editor.apply();

				String sPreset = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__nickname));
				if (sPreset != null && userService != null) {
					if (!TestUtil.compare(userService.getPublicNickname(), sPreset)) {
						userService.setPublicNickname(sPreset);
					}
				}
			}
		}
	}

}
