/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

package ch.threema.app.activities.wizard;

import static ch.threema.app.ThreemaApplication.PHONE_LINKED_PLACEHOLDER;

import android.Manifest;
import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.LifecycleOwner;
import androidx.viewpager.widget.ViewPager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.slf4j.Logger;

import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.fragments.wizard.WizardFragment0;
import ch.threema.app.fragments.wizard.WizardFragment1;
import ch.threema.app.fragments.wizard.WizardFragment2;
import ch.threema.app.fragments.wizard.WizardFragment3;
import ch.threema.app.fragments.wizard.WizardFragment4;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.UserService;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.threemasafe.ThreemaSafeService;
import ch.threema.app.ui.ParallaxViewPager;
import ch.threema.app.ui.StepPagerStrip;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.app.workers.IdentityStatesWorker;
import ch.threema.app.workers.WorkSyncWorker;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.LinkEmailException;
import ch.threema.domain.protocol.api.LinkMobileNoException;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

public class WizardBaseActivity extends ThreemaAppCompatActivity implements
		LifecycleOwner,
		ViewPager.OnPageChangeListener,
		View.OnClickListener,
		WizardFragment1.OnSettingsChangedListener,
		WizardFragment2.OnSettingsChangedListener,
		WizardFragment3.OnSettingsChangedListener,
		WizardFragment4.SettingsInterface,
		WizardDialog.WizardDialogCallback {

	private static final Logger logger = LoggingUtil.getThreemaLogger("WizardBaseActivity");

	public static final String EXTRA_NEW_IDENTITY_CREATED = "newIdentity";
	private static final String DIALOG_TAG_USE_ID_AS_NICKNAME = "nd";
	private static final String DIALOG_TAG_INVALID_ENTRY = "ie";
	private static final String DIALOG_TAG_USE_ANONYMOUSLY = "ano";
	private static final String DIALOG_TAG_THREEMA_SAFE = "sd";
	private static final String DIALOG_TAG_PASSWORD_BAD = "pwb";
	private static final String DIALOG_TAG_SYNC_CONTACTS_ENABLE = "scen";
	private static final String DIALOG_TAG_SYNC_CONTACTS_MDM_ENABLE_RATIONALE = "scmer";

	private static final int PERMISSION_REQUEST_READ_CONTACTS = 2;
	private static final int NUM_PAGES = 5;
	private static final long FINISH_DELAY = 3 * 1000;
	private static final long DIALOG_DELAY = 200;

	public static final boolean DEFAULT_SYNC_CONTACTS = false;
	private static final String DIALOG_TAG_WORK_SYNC = "workSync";

	private static int lastPage = 0;
	private ParallaxViewPager viewPager;
	private MaterialButton prevButton, nextButton;
	private Button finishButton;
	private StepPagerStrip stepPagerStrip;
	private String nickname, email, number, prefix, presetMobile, presetEmail, safePassword;
	private ThreemaSafeServerInfo safeServerInfo = new ThreemaSafeServerInfo();
	private boolean isSyncContacts = DEFAULT_SYNC_CONTACTS, userCannotChangeContactSync = false, skipWizard = false, readOnlyProfile = false;
	private ThreemaSafeMDMConfig safeConfig;
	private ServiceManager serviceManager;
	private UserService userService;
	private LocaleService localeService;
	private PreferenceService preferenceService;
	private ThreemaSafeService threemaSafeService;
	private boolean errorRaised = false, isNewIdentity = false;
	private WizardFragment4 fragment4;

	private final Handler finishHandler = new Handler();
	private final Handler dialogHandler = new Handler();

	private final Runnable finishTask = new Runnable() {
		@Override
		public void run() {
		 	RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					fragment4.setContactsSyncInProgress(false, null);
					prepareThreemaSafe();
				}
			});
		}
	};

	private Runnable showDialogDelayedTask(final int current, final int previous) {
		return () -> {
			RuntimeUtil.runOnUiThread(() -> {
				if (current == WizardFragment2.PAGE_ID && previous == WizardFragment1.PAGE_ID && TestUtil.empty(getSafePassword())) {
					if (safeConfig.isBackupForced()) {
						setPage(WizardFragment1.PAGE_ID);
					} else if (!isReadOnlyProfile()) {
						WizardDialog wizardDialog = WizardDialog.newInstance(R.string.safe_disable_confirm, R.string.yes, R.string.no, WizardDialog.Highlight.NEGATIVE);
						wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_THREEMA_SAFE);
					}
				}

				if (current == WizardFragment4.PAGE_ID && previous == WizardFragment3.PAGE_ID) {
					if (!isReadOnlyProfile()) {
						if ((!TestUtil.empty(number) && TestUtil.empty(presetMobile) && !localeService.validatePhoneNumber(getPhone())) ||
								((!TestUtil.empty(email) && TestUtil.empty(presetEmail) && !Patterns.EMAIL_ADDRESS.matcher(email).matches()))) {
							WizardDialog wizardDialog = WizardDialog.newInstance(ConfigUtils.isWorkBuild() ?
									R.string.new_wizard_phone_email_invalid :
									R.string.new_wizard_phone_invalid,
									R.string.ok);
							wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_INVALID_ENTRY);
						}
					}
				}

				if (current == WizardFragment4.PAGE_ID && previous == WizardFragment3.PAGE_ID) {
					if (!isReadOnlyProfile()) {
						boolean needConfirm;
						if (ConfigUtils.isWorkBuild()) {
							needConfirm = TestUtil.empty(number) && TestUtil.empty(email) && TestUtil.empty(getPresetEmail()) && TestUtil.empty(getPresetPhone());
						} else {
							if (ConfigUtils.isOnPremBuild()) {
								needConfirm = false;
							} else {
								needConfirm = TestUtil.empty(number) && TestUtil.empty(getPresetPhone());
							}
						}
						if (needConfirm) {
							WizardDialog wizardDialog = WizardDialog.newInstance(
									ConfigUtils.isWorkBuild() ?
											R.string.new_wizard_anonymous_confirm :
											R.string.new_wizard_anonymous_confirm_phone_only,
									R.string.yes, R.string.no, WizardDialog.Highlight.NEGATIVE);
							wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_USE_ANONYMOUSLY);
						}
					}
				}
			});
		};
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				userService = serviceManager.getUserService();
				localeService = serviceManager.getLocaleService();
				preferenceService = serviceManager.getPreferenceService();
				threemaSafeService = serviceManager.getThreemaSafeService();
			}
		} catch (Exception e) {
			logger.error("Exception", e);
			finish();
			return;
		}
		if (userService == null || localeService == null || preferenceService == null) {
			logger.error("Required services not available.");
			finish();
			return;
		}

		setContentView(R.layout.activity_wizard);

		nextButton = findViewById(R.id.next_page_button);
		nextButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				nextPage();
			}
		});

		prevButton = findViewById(R.id.prev_page_button);
		prevButton.setVisibility(View.GONE);
		prevButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				prevPage();
			}
		});

		stepPagerStrip = findViewById(R.id.strip);
		stepPagerStrip.setPageCount(NUM_PAGES);
		stepPagerStrip.setCurrentPage(WizardFragment0.PAGE_ID);

		viewPager = findViewById(R.id.pager);
		viewPager.addLayer(findViewById(R.id.layer0));
		viewPager.addLayer(findViewById(R.id.layer1));

		Intent intent = getIntent();
		if (intent != null) {
			isNewIdentity = intent.getBooleanExtra(EXTRA_NEW_IDENTITY_CREATED, false);
		}

		if (ConfigUtils.isWorkBuild()) {
			performWorkSync();
		} else {
			setupConfig();
		}
	}

	private void setupConfig() {
		safeConfig = ThreemaSafeMDMConfig.getInstance();

		if (ConfigUtils.isWorkRestricted()) {
			if (isSafeEnabled()) {
				safePassword = safeConfig.getPassword();
				safeServerInfo = safeConfig.getServerInfo();
			}

			String stringPreset;
			Boolean booleanPreset;

			stringPreset = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__linked_email));
			if (stringPreset != null) {
				email = stringPreset;
			}
			stringPreset = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__linked_phone));
			if (stringPreset != null) {
				splitMobile(stringPreset);
			}
			stringPreset = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__nickname));
			if (stringPreset != null) {
				nickname = stringPreset;
			} else {
				nickname = userService.getIdentity();
			}
			booleanPreset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__contact_sync));
			if (booleanPreset != null) {
				isSyncContacts = booleanPreset;
				userCannotChangeContactSync = true;
			}
			booleanPreset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__readonly_profile));
			if (booleanPreset != null) {
				readOnlyProfile = booleanPreset;
			}
			booleanPreset = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__skip_wizard));
			if (booleanPreset != null) {
				if (booleanPreset) {
					skipWizard = true;
					viewPager.setCurrentItem(WizardFragment4.PAGE_ID);
				}
			}
		} else {
			// ignore backup presets in restricted mode
			if (!TestUtil.empty(presetMobile)) {
				splitMobile(presetMobile);
			}
			if (!TestUtil.empty(presetEmail)) {
				email = presetEmail;
			}

		}

		// if the app is running in a restricted user profile, it s not possible to add accounts
		if (SynchronizeContactsUtil.isRestrictedProfile(this)) {
			userCannotChangeContactSync = true;
			isSyncContacts = false;
		}

		viewPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
		viewPager.addOnPageChangeListener(this);

		presetMobile = this.userService.getLinkedMobile();
		presetEmail = this.userService.getLinkedEmail();
	}

	/**
	 * Perform an early synchronous fetch2. In case of failure due to rate-limiting, do not allow user to continue
	 */
	private void performWorkSync() {
		GenericProgressDialog.newInstance(R.string.work_data_sync_desc,
			R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_WORK_SYNC);

		WorkSyncWorker.Companion.performOneTimeWorkSync(
			this,
			() -> {
				// On success
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_WORK_SYNC, true);
				setupConfig();
			},
			() -> {
				// On fail
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_WORK_SYNC, true);
				RuntimeUtil.runOnUiThread(() -> Toast.makeText(WizardBaseActivity.this, R.string.unable_to_fetch_configuration, Toast.LENGTH_LONG).show());
				logger.info("Unable to post work request for fetch2");
				try {
					userService.removeIdentity();
				} catch (Exception e) {
					logger.error("Unable to remove identity", e);
				}
				finishAndRemoveTask();
			});
	}

	private void splitMobile(String phoneNumber) {
		if (PHONE_LINKED_PLACEHOLDER.equals(phoneNumber)) {
			prefix = "";
			number = PHONE_LINKED_PLACEHOLDER;
		} else {
			try {
				PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
				Phonenumber.PhoneNumber numberProto = null;

				numberProto = phoneNumberUtil.parse(phoneNumber, "");
				prefix = "+" + numberProto.getCountryCode();
				number = String.valueOf(numberProto.getNationalNumber());
			} catch (NumberParseException e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	protected void onDestroy() {
		viewPager.removeOnPageChangeListener(this);

		super.onDestroy();
	}

	/**
	 * This method will be invoked when the current page is scrolled, either as part
	 * of a programmatically initiated smooth scroll or a user initiated touch scroll.
	 *
	 * @param position             Position index of the first page currently being displayed.
	 *                             Page position+1 will be visible if positionOffset is nonzero.
	 * @param positionOffset       Value from [0, 1) indicating the offset from the page at position.
	 * @param positionOffsetPixels Value in pixels indicating the offset from position.
	 */
	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

	}

	/**
	 * This method will be invoked when a new page becomes selected. Animation is not
	 * necessarily complete.
	 *
	 * @param position Position index of the new selected page.
	 */
	@SuppressLint("StaticFieldLeak")
	@Override
	public void onPageSelected(int position) {
		prevButton.setVisibility(position == WizardFragment0.PAGE_ID ? View.GONE : View.VISIBLE);
		nextButton.setVisibility(position == NUM_PAGES - 1 ? View.GONE : View.VISIBLE);

		stepPagerStrip.setCurrentPage(position);

		if (position == WizardFragment1.PAGE_ID && safeConfig.isSkipBackupPasswordEntry()) {
			if (lastPage == WizardFragment0.PAGE_ID) {
				nextPage();
			} else {
				prevPage();
			}
			return;
		}

		if (position == WizardFragment2.PAGE_ID && lastPage == WizardFragment1.PAGE_ID) {
			if (!TextUtils.isEmpty(safePassword)) {
				new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(Void... voids) {
						return TextUtil.checkBadPassword(getApplicationContext(), safePassword);
					}

					@Override
					protected void onPostExecute(Boolean isBad) {
						if (isBad) {
							Context context = WizardBaseActivity.this;
							if (AppRestrictionUtil.isSafePasswordPatternSet(context)) {
								WizardDialog wizardDialog = WizardDialog.newInstance(AppRestrictionUtil.getSafePasswordMessage(context), R.string.try_again);
								wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_PASSWORD_BAD);
							} else {
								WizardDialog wizardDialog = WizardDialog.newInstance(R.string.password_bad_explain, R.string.continue_anyway, R.string.try_again, WizardDialog.Highlight.NEGATIVE);
								wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_PASSWORD_BAD);
							}
						}
					}
				}.execute();
			}
		}

		if (position > lastPage && position >= WizardFragment2.PAGE_ID && position <= WizardFragment4.PAGE_ID) {
			// we delay dialogs for a few milliseconds to prevent stuttering of the page change animation
			dialogHandler.removeCallbacks(showDialogDelayedTask(position, lastPage));
			dialogHandler.postDelayed(showDialogDelayedTask(position, lastPage), DIALOG_DELAY);
		}

		lastPage = position;
	}

	/**
	 * Called when the scroll state changes. Useful for discovering when the user
	 * begins dragging, when the pager is automatically settling to the current page,
	 * or when it is fully stopped/idle.
	 *
	 * @param state The new scroll state.
	 * @see ViewPager#SCROLL_STATE_IDLE
	 * @see ViewPager#SCROLL_STATE_DRAGGING
	 * @see ViewPager#SCROLL_STATE_SETTLING
	 */
	@Override
	public void onPageScrollStateChanged(int state) { }

	/**
	 * Called when a view has been clicked.
	 *
	 * @param v The view that was clicked.
	 */
	@Override
	public void onClick(View v) {
		if (v.equals(nextButton)) {
			nextPage();
		} else if (v.equals(prevButton)) {
			prevPage();
		}
	}

	@Override
	public void onWizardFinished(WizardFragment4 fragment, Button finishButton) {
		errorRaised = false;
		fragment4 = fragment;

		viewPager.lock(true);
		this.finishButton = finishButton;

		prevButton.setVisibility(View.GONE);
		if (finishButton != null) {
			finishButton.setEnabled(false);
		}

		userService.setPublicNickname(this.nickname);

		askUserForContactSync();
	}

	private void askUserForContactSync() {
		/* trigger a connection now - as application lifecycle was set to resumed state when there was no identity yet */
		serviceManager.getLifetimeService().ensureConnection();

		if (this.userCannotChangeContactSync) {
			if (this.isSyncContacts) {
				if (ConfigUtils.isPermissionGranted(this, Manifest.permission.READ_CONTACTS)) {
					// Permission already granted, therefore continue by linking the phone
					linkPhone();
				} else {
					// If permission is not yet granted, show a dialog to inform that contact sync
					// has been force enabled by the administrator
					WizardDialog wizardDialog = WizardDialog.newInstance(R.string.contact_sync_mdm_rationale, R.string.ok);
					wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_SYNC_CONTACTS_MDM_ENABLE_RATIONALE);
				}
			} else {
				linkPhone();
			}
		} else {
			WizardDialog wizardDialog = WizardDialog.newInstance(R.string.new_wizard_info_sync_contacts_dialog, R.string.yes, R.string.no, null);
			wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_SYNC_CONTACTS_ENABLE);
		}
	}

	private void requestContactSyncPermission() {
		if (ConfigUtils.requestContactPermissions(this, null, PERMISSION_REQUEST_READ_CONTACTS)) {
			// permission is already granted
			this.isSyncContacts = true;
			preferenceService.setSyncContacts(this.isSyncContacts);
			linkPhone();
		}
		// continue to onRequestPermissionsResult
	}

	@Override
	public void onNicknameSet(String nickname) {
		this.nickname = nickname;
	}

	@Override
	public void onPhoneSet(String phoneNumber) {
		this.number = phoneNumber;
	}

	@Override
	public void onPrefixSet(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public void onEmailSet(String email) {
		this.email = email;
	}

	@Override
	public void onSafePasswordSet(final String password) {
		safePassword = password;
	}

	@Override
	public void onSafeServerInfoSet(ThreemaSafeServerInfo safeServerInfo) {
		this.safeServerInfo = safeServerInfo;
	}

	@Override
	public String getNickname() {
		return this.nickname;
	}

	@Override
	public String getPhone() {
		if (PHONE_LINKED_PLACEHOLDER.equals(this.number)) {
			return this.number;
		}

		String phone = this.prefix + this.number;

		if (localeService.validatePhoneNumber(phone)) {
			return serviceManager.getLocaleService().getNormalizedPhoneNumber(phone);
		}
		return "";
	}

	@Override
	public String getNumber() {
		return this.number;
	}

	@Override
	public String getPrefix() {
		return this.prefix;
	}

	@Override
	public String getEmail() {
		return (this.email != null && this.email.length() > 4) ? this.email : "";
	}

	@Override
	public String getPresetPhone() {
		return this.presetMobile;
	}

	@Override
	public String getPresetEmail() {
		return this.presetEmail;
	}

	@Override
	public boolean getSafeForcePasswordEntry() {
		return safeConfig.isBackupForced();
	}

	@Override
	public boolean getSafeSkipBackupPasswordEntry() {
		return safeConfig.isSkipBackupPasswordEntry();
	}

	@Override
	public boolean isSafeEnabled() {
		return !safeConfig.isBackupDisabled();
	}

	@Override
	public String getSafePassword() {
		return this.safePassword;
	}

	@Override
	public ThreemaSafeServerInfo getSafeServerInfo() {
		return this.safeServerInfo;
	}

	@Override
	public boolean getSyncContacts() {
		return this.isSyncContacts;
	}

	@Override
	public boolean isReadOnlyProfile() {
		return this.readOnlyProfile;
	}

	@Override
	public boolean isSkipWizard() {
		return this.skipWizard;
	}

	/**
	 * Return wether the identity was just created
	 * @return true if it's a new identity, false if the identity was restored
	 */
	public boolean isNewIdentity() {
		return isNewIdentity;
	}

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_USE_ID_AS_NICKNAME:
				this.nickname = this.userService.getIdentity();
				break;
			case DIALOG_TAG_INVALID_ENTRY:
				prevPage();
				break;
			case DIALOG_TAG_PASSWORD_BAD:
			case DIALOG_TAG_THREEMA_SAFE:
				break;
			case DIALOG_TAG_SYNC_CONTACTS_ENABLE:
			case DIALOG_TAG_SYNC_CONTACTS_MDM_ENABLE_RATIONALE:
				requestContactSyncPermission();
				break;
		}
	}

	@Override
	public void onNo(String tag) {
		switch (tag) {
			case DIALOG_TAG_USE_ID_AS_NICKNAME:
				prevPage();
				break;
			case DIALOG_TAG_USE_ANONYMOUSLY:
				setPage(WizardFragment3.PAGE_ID);
				break;
			case DIALOG_TAG_THREEMA_SAFE:
				prevPage();
				break;
			case DIALOG_TAG_PASSWORD_BAD:
				setPage(WizardFragment1.PAGE_ID);
				break;
			case DIALOG_TAG_SYNC_CONTACTS_ENABLE:
				isSyncContacts = false;
				this.serviceManager.getPreferenceService().setSyncContacts(false);
				linkPhone();
				break;
		}
	}

	@Override
	public void onBackPressed() {
		if (prevButton != null && prevButton.getVisibility() == View.VISIBLE) {
			prevPage();
		}
	}

	private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
		public ScreenSlidePagerAdapter(FragmentManager fm) {
			super(fm, FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
		}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
				case WizardFragment0.PAGE_ID:
					return new WizardFragment0();
				case WizardFragment1.PAGE_ID:
					return new WizardFragment1();
				case WizardFragment2.PAGE_ID:
					return new WizardFragment2();
				case WizardFragment3.PAGE_ID:
					return new WizardFragment3();
				case WizardFragment4.PAGE_ID:
					return new WizardFragment4();
				default:
					break;
			}
			return null;
		}

		@Override
		public int getCount() {
			return NUM_PAGES;
		}
	}

	public void nextPage() {
		int currentItem = viewPager.getCurrentItem() + 1;
		if (currentItem < NUM_PAGES) {
			viewPager.setCurrentItem(currentItem);
		}
	}

	public void prevPage() {
		int currentItem = viewPager.getCurrentItem();
		if (currentItem != 0) {
			viewPager.setCurrentItem(currentItem - 1);
		}
	}

	public void setPage(int page) {
		viewPager.setCurrentItem(page);
	}

	@SuppressLint("StaticFieldLeak")
	private void linkEmail(final WizardFragment4 fragment) {
		final String newEmail = getEmail();
		if (TestUtil.empty(newEmail)) {
			initSyncAndFinish();
			return;
		}

		boolean isNewEmail = (!(presetEmail != null && presetEmail.equals(newEmail)));

		if ((userService.getEmailLinkingState() != UserService.LinkingState_LINKED) && isNewEmail) {
			new AsyncTask<Void, Void, String>() {
				@Override
				protected void onPreExecute() {
					fragment.setEmailLinkingInProgress(true);
				}

				@Override
				protected String doInBackground(Void... params) {
					try {
						userService.linkWithEmail(email);
					} catch (LinkEmailException e) {
						logger.error("Exception", e);
						return e.getMessage();
					} catch (Exception e) {
						logger.error("Exception", e);
						return getString(R.string.internet_connection_required);
					}
					return null;
				}

				@Override
				protected void onPostExecute(String result) {
					if (result != null) {
						fragment.setEmailLinkingAlert(result);
						errorRaised = true;
					} else {
						fragment.setEmailLinkingInProgress(false);
					}
					initSyncAndFinish();
				}
			}.execute();
		} else {
			initSyncAndFinish();
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void linkPhone() {
		final String phone = getPhone();
		if (TestUtil.empty(phone)) {
			linkEmail(fragment4);
			return;
		}

		boolean isNewPhoneNumber = (presetMobile == null || !presetMobile.equals(phone));

		// start linking activity only if not already linked
		if ((userService.getMobileLinkingState() != UserService.LinkingState_LINKED) && isNewPhoneNumber) {
			new AsyncTask<Void, Void, String>() {
				@Override
				protected void onPreExecute() {
					fragment4.setMobileLinkingInProgress(true);
				}

				@Override
				protected String doInBackground(Void... params) {
					try {
						userService.linkWithMobileNumber(phone);
					} catch (LinkMobileNoException e) {
						logger.error("Exception", e);
						return e.getMessage();
					} catch (Exception e) {
						logger.error("Exception", e);
						return getString(R.string.internet_connection_required);
					}
					return null;
				}

				@Override
				protected void onPostExecute(String result) {
					if (result != null) {
						fragment4.setMobileLinkingAlert(result);
						errorRaised = true;
					} else {
						fragment4.setMobileLinkingInProgress(false);
					}
					linkEmail(fragment4);
				}
			}.execute();
		} else {
			linkEmail(fragment4);
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void addUser(final String id, final String first, final String last) {
		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				try {
					ContactModel newUser = serviceManager.getContactService()
							.createContactByIdentity(id, true);

					if (newUser != null) {
						newUser.setFirstName(first);
						newUser.setLastName(last);
						serviceManager.getContactService().save(newUser);
					}

				} catch (InvalidEntryException | MasterKeyLockedException | FileSystemNotPresentException e) {
					logger.error("Exception", e);
					//should not happen, ignore
				} catch (EntryAlreadyExistsException | PolicyViolationException e) {
					//ok, id already exists or adding IDs is prohibited, do nothing
				}
				return null;
			}
		}.execute();
	}

	private void finishAndRestart() {
		preferenceService.setWizardRunning(false);
		preferenceService.setLatestVersion(this);

		addUser(ThreemaApplication.ECHO_USER_IDENTITY, "Echo", "Test");

		// flush conversation cache (after a restore)
		try {
			ConversationService conversationService = serviceManager.getConversationService();
			if (conversationService != null) {
				conversationService.reset();
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		ConfigUtils.recreateActivity(this);
	}

	private void ensureMasterKeyWrite() {
		// Write master key now if no passphrase has been set - don't leave it up to the MainActivity
		if (!ThreemaApplication.getMasterKey().isProtected()) {
			try {
				ThreemaApplication.getMasterKey().setPassphrase(null);
			} catch (Exception e) {
				// better die if something went wrong as the master key may not have been saved
				throw new RuntimeException(e);
			}
		}
	}

	@SuppressLint({"StaticFieldLeak", "MissingPermission"})
	private void reallySyncContactsAndFinish() {
		ensureMasterKeyWrite();

		if (preferenceService.isSyncContacts()) {
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected void onPreExecute() {
					fragment4.setContactsSyncInProgress(true, getString(R.string.wizard1_sync_contacts));
				}

				@SuppressLint("MissingPermission")
				@Override
				protected Void doInBackground(Void... params) {
					try {
						final Account account = userService.getAccount(true);
						//disable
						userService.enableAccountAutoSync(false);

						SynchronizeContactsService synchronizeContactsService = serviceManager.getSynchronizeContactsService();
						SynchronizeContactsRoutine routine = synchronizeContactsService.instantiateSynchronization(account);

						routine.setOnStatusUpdate(new SynchronizeContactsRoutine.OnStatusUpdate() {
							@Override
							public void newStatus(final long percent, final String message) {
							 	RuntimeUtil.runOnUiThread(() -> fragment4.setContactsSyncInProgress(true, message));
							}

							@Override
							public void error(final Exception x) {
							 	RuntimeUtil.runOnUiThread(() -> fragment4.setContactsSyncInProgress(false, x.getMessage()));
							}
						});

						//on finished, close the dialog
						routine.addOnFinished(new SynchronizeContactsRoutine.OnFinished() {
							@Override
							public void finished(boolean success, long modifiedAccounts, List<ContactModel> createdContacts, long deletedAccounts) {
								userService.enableAccountAutoSync(true);
							}
						});

						routine.run();
					} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
						logger.error("Exception", e);
					}
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					startIdentityStatesSync();

					finishHandler.removeCallbacks(finishTask);
					finishHandler.postDelayed(finishTask, FINISH_DELAY);
				}
			}.execute();
		} else {
			userService.removeAccount();
			prepareThreemaSafe();
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void prepareThreemaSafe() {
		if (!TestUtil.empty(getSafePassword())) {
			new AsyncTask<Void, Void, byte[]>() {
				@Override
				protected void onPreExecute() {
					fragment4.setThreemaSafeInProgress(true, getString(R.string.preparing_threema_safe));
				}

				@Override
				protected byte[] doInBackground(Void... voids) {
					return threemaSafeService.deriveMasterKey(getSafePassword(), userService.getIdentity());
				}

				@Override
				protected void onPostExecute(byte[] masterkey) {
					fragment4.setThreemaSafeInProgress(false, getString(R.string.menu_done));

					if (masterkey != null) {
						threemaSafeService.storeMasterKey(masterkey);
						preferenceService.setThreemaSafeServerInfo(safeServerInfo);
						threemaSafeService.setEnabled(true);
						threemaSafeService.uploadNow(true);
					} else {
						Toast.makeText(WizardBaseActivity.this, R.string.safe_error_preparing, Toast.LENGTH_LONG).show();
					}

					finishAndRestart();
				}
			}.execute();
		} else {
			// no password was set
			// do not save mdm settings if backup is forced and no password was set - this will cause a password prompt later
			if (!(ConfigUtils.isWorkRestricted() && ThreemaSafeMDMConfig.getInstance().isBackupForced())) {
				threemaSafeService.storeMasterKey(new byte[0]);
			}
			finishAndRestart();
		}
	}

	private void initSyncAndFinish() {
		if (!errorRaised || ConfigUtils.isWorkRestricted()) {
			syncContactsAndFinish();
		} else {
			resetUi();
		}
	}

	private void resetUi() {
		// unlock UI to try again
		viewPager.lock(false);
		prevButton.setVisibility(View.VISIBLE);
		if (finishButton != null) {
			finishButton.setEnabled(true);
		}
	}

	private void startIdentityStatesSync() {
		OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(IdentityStatesWorker.class)
				.build();

		WorkManager.getInstance(this).enqueue(workRequest);
	}

	private void syncContactsAndFinish() {
		/* trigger a connection now - as application lifecycle was set to resumed state when there was no identity yet */
		serviceManager.getLifetimeService().ensureConnection();

		if (this.isSyncContacts) {
			preferenceService.setSyncContacts(true);
			reallySyncContactsAndFinish();
		} else {
			preferenceService.setSyncContacts(false);
			startIdentityStatesSync();
			prepareThreemaSafe();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST_READ_CONTACTS) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				this.isSyncContacts = true;
				linkPhone();
			} else if (userCannotChangeContactSync) {
				ConfigUtils.showPermissionRationale(this, (View) viewPager.getParent(), R.string.permission_contacts_sync_required);
				resetUi();
			} else {
				this.isSyncContacts = false;
				linkPhone();
			}
		}
	}
}
