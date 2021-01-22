/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.Context;
import android.net.Uri;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.utils.ConfigUtils.AppTheme;
import ch.threema.client.work.WorkDirectoryCategory;
import ch.threema.client.work.WorkOrganization;

public interface PreferenceService {

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({ImageScale_DEFAULT, ImageScale_SMALL, ImageScale_MEDIUM, ImageScale_LARGE, ImageScale_XLARGE, ImageScale_ORIGINAL, ImageScale_SEND_AS_FILE})
	@interface ImageScale {}
	int ImageScale_DEFAULT = -1;
	int ImageScale_SMALL = 0;
	int ImageScale_MEDIUM = 1;
	int ImageScale_LARGE = 2;
	int ImageScale_XLARGE = 3;
	int ImageScale_ORIGINAL = 4;
	int ImageScale_SEND_AS_FILE = 5;

	@IntDef({VideoSize_DEFAULT, VideoSize_SMALL, VideoSize_MEDIUM, VideoSize_ORIGINAL})
	@interface VideoSize {}
	int VideoSize_DEFAULT = -1;
	int VideoSize_SMALL = 0;
	int VideoSize_MEDIUM = 1;
	int VideoSize_ORIGINAL = 2;

	int Theme_LIGHT = 0;
	int Theme_DARK = 1;

	int EmojiStyle_DEFAULT = 0;
	int EmojiStyle_ANDROID = 1;

	String LockingMech_NONE = "none";
	String LockingMech_PIN = "pin";
	String LockingMech_SYSTEM = "system";
	String LockingMech_BIOMETRIC = "biometric";

	int PROFILEPIC_RELEASE_NOBODY = 0;
	int PROFILEPIC_RELEASE_EVERYONE = 1;
	int PROFILEPIC_RELEASE_SOME = 2;

	int PRIVACY_POLICY_ACCEPT_NONE = 0;
	int PRIVACY_POLICY_ACCEPT_EXCPLICIT = 1;
	int PRIVACY_POLICY_ACCEPT_IMPLICIT = 2;
	int PRIVACY_POLICY_ACCEPT_UPDATE = 3;

	String VIDEO_CODEC_HW = "hw";
	String VIDEO_CODEC_NO_VP8 = "no-vp8";
	String VIDEO_CODEC_NO_H264HIP = "no-h264hip";
	String VIDEO_CODEC_SW = "sw";

	boolean isReadReceipts();
	void setReadReceipts(boolean value);

	boolean isSyncContacts();

	void setSyncContacts(boolean setting);

	boolean isBlockUnknown();

	void setBlockUnknown(boolean value);

	boolean isTypingIndicator();
	void setTypingIndicator(boolean value);

	Uri getNotificationSound();

	Uri getGroupNotificationSound();

	Uri getVoiceCallSound();

	boolean isVoiceCallVibrate();

	void setNotificationSound(Uri uri);

	void setGroupNotificationSound(Uri uri);

	void setVoiceCallSound(Uri uri);

	boolean isVibrate();

	boolean isGroupVibrate();

	String getNotificationLight();

	String getGroupNotificationLight();

	HashMap<String, String> getRingtones();

	void setRingtones(HashMap<String, String> ringtones);

	boolean isCustomWallpaperEnabled();

	void setCustomWallpaperEnabled(boolean enabled);

	boolean isEnterToSend();

	boolean isFullscreenIme();

	boolean isInAppSounds();

	boolean isInAppVibrate();

	boolean isShowMessagePreview();

	@ImageScale int getImageScale();

	int getVideoSize();

	String getSerialNumber();

	void setSerialNumber(String serialNumber);

	String getLicenseUsername();

	void setLicenseUsername(String username);

	String getLicensePassword();

	void setLicensePassword(String password);

	LinkedList<Integer> getRecentEmojis();

	LinkedList<String> getRecentEmojis2();

	void setRecentEmojis(LinkedList<Integer> list);

	void setRecentEmojis2(LinkedList<String> list);

	boolean isPolling();

	public void setPolling(boolean value);

	public boolean isSaveMedia();

	boolean isMasterKeyNewMessageNotifications();

	boolean isPinSet();

	boolean setPin(String newCode);

	boolean isPinCodeCorrect(String pinCode);

	int getTransmittedFeatureLevel();

	void setTransmittedFeatureLevel(int featureLevel);

	String[] getList(String listName);

	void setList(String listName, String[] identities);

	HashMap<Integer, String> getHashMap(String listName, boolean encrypted);

	void setHashMap(String listName, HashMap<Integer, String> hashMap);

	HashMap<String, String> getStringHashMap(String listName, boolean encrypted);

	void setStringHashMap(String listName, HashMap<String, String> hashMap);

	/**
	 * value in seconds!
	 *
	 * @return
	 */
	int getPinLockGraceTime();

	int getIDBackupCount();

	void incrementIDBackupCount();

	void resetIDBackupCount();

	Date getLastIDBackupReminderDate();

	void setLastIDBackupReminderDate(Date lastIDBackupReminderDate);

	String getContactListSorting();

	boolean isContactListSortingFirstName();

	String getContactFormat();

	boolean isContactFormatFirstNameLastName();

	boolean isDefaultContactPictureColored();

	boolean isDisableScreenshots();

	int getFontStyle();

	void clear();

	public List<String[]> write();

	boolean read(List<String[]> values);

	Integer getRoutineInterval(String key);

	void setRoutineInterval(String key, Integer intervalSeconds);

	boolean showInactiveContacts();

	boolean getLastOnlineStatus();

	void setLastOnlineStatus(boolean online);

	boolean isLatestVersion(Context context);

	int getLatestVersion();

	void setLatestVersion(Context context);

	boolean getFileSendInfoShown();

	void setFileSendInfoShown(boolean shown);

	int getTheme();

	int getEmojiStyle();

	/**
	 * Return the polling interval in milliseconds.
	 */
	long getPollingInterval();

	/**
	 * Return the timestamp of the last successful polling in milliseconds.
	 */
	Long getLastSuccessfulPollTimestamp();

	/**
	 * Set the timestamp of the last successful polling in milliseconds.
	 */
	void setLastSuccessfulPollTimestamp(long timestamp);

	void setLockoutDeadline(long deadline);

	void setLockoutTimeout(long timeout);

	long getLockoutDeadline();

	long getLockoutTimeout();

	void setWizardRunning(boolean running);

	boolean getWizardRunning();

	boolean isGifAutoplay();

	boolean isUseProximitySensor();

	void setBlockUnkown(Boolean booleanPreset);

	void setAppLogoExpiresAt(Date expiresAt, int theme);

	Date getAppLogoExpiresAt(int theme);

	boolean isPrivateChatsHidden();

	void setPrivateChatsHidden(boolean hidden);

	String getLockMechanism();

	/**
	 * Check if app UI lock is enabled
	 * @return true if UI lock is enabled, false otherwise
	 */
	boolean isAppLockEnabled();

	void setAppLockEnabled(boolean enabled);

	void setSaveToGallery(Boolean booleanPreset);

	void setDisableScreenshots(Boolean booleanPreset);

	void setLockMechanism(String lockingMech);

	boolean isShowImageAttachPreviewsEnabled();

	void setImageAttachPreviewsEnabled(boolean enable);

	boolean isDirectShare();

	void setMessageDrafts(HashMap<String, String> messageDrafts);

	HashMap<String,String> getMessageDrafts();

	void setAppLogo(@NonNull String url, @AppTheme int theme);
	void clearAppLogo(@AppTheme int theme);
	void clearAppLogos();
	@Nullable String getAppLogo(@AppTheme int theme);

	void setCustomSupportUrl(String supportUrl);
	String getCustomSupportUrl();

	String getLocaleOverride();

	HashMap<String, String> getDiverseEmojiPrefs2();

	void setDiverseEmojiPrefs2(HashMap<String, String> diverseEmojis);

	boolean isWebClientEnabled();
	void setWebClientEnabled(boolean enabled);

	void setPushToken(String gcmToken);
	String getPushToken();

	int getProfilePicRelease();

	void setProfilePicRelease(int value);

	Date getProfilePicLastUpdate();

	void setProfilePicLastUpdate(Date date);

	long getProfilePicUploadDate();

	void setProfilePicUploadDate(Date date);

	void setProfilePicUploadData(ContactServiceImpl.ContactPhotoUploadResult result);

	ContactServiceImpl.ContactPhotoUploadResult getProfilePicUploadData(ContactServiceImpl.ContactPhotoUploadResult result);

	boolean getProfilePicReceive();

	@NonNull String getAECMode();

	@NonNull String getVideoCodec();

	boolean getForceTURN();
	void setForceTURN(boolean value);

	boolean isVoipEnabled();
	void setVoipEnabled(boolean value);

	boolean isRejectMobileCalls();

	void setRejectMobileCalls(boolean value);

	boolean allowWebrtcIpv6();

	int getNotificationPriority();

	void setNotificationPriority(int value);

	Set<String> getMobileAutoDownload();

	Set<String> getWifiAutoDownload();

	void setRandomRatingRef(String ref);
	String getRandomRatingRef();

	String getRatingReviewText();
	void setRatingReviewText(String review);

	void setPrivacyPolicyAccepted(Date date, int source);
	Date getPrivacyPolicyAccepted();
	void clearPrivacyPolicyAccepted();

	float getPrivacyPolicyAcceptedVersion();
	void  setPrivacyPolicyAcceptedVersion(float version);

	boolean getIsVideoCallTooltipShown();
	void setVideoCallTooltipShown(boolean shown);

	boolean getIsWorkHintTooltipShown();
	void setIsWorkHintTooltipShown(boolean shown);

	void setThreemaSafeEnabled(boolean value);
	boolean getThreemaSafeEnabled();

	void setThreemaSafeMasterKey(byte[] masterKey);
	byte[] getThreemaSafeMasterKey();

	void setThreemaSafeServerInfo(ThreemaSafeServerInfo serverInfo);
	ThreemaSafeServerInfo getThreemaSafeServerInfo();

	void setThreemaSafeUploadDate(Date date);
	Date getThreemaSafeUploadDate();

	void setIncognitoKeyboard(boolean enabled);
	boolean getIncognitoKeyboard();

	boolean getShowUnreadBadge();

	void setThreemaSafeErrorCode(int code);
	int getThreemaSafeErrorCode();

	void setThreemaSafeServerMaxUploadSize(long maxBackupBytes);
	long getThreemaSafeServerMaxUploadSize();

	void setThreemaSafeServerRetention(int days);
	int getThreemaSafeServerRetention();

	void setThreemaSafeBackupSize(int size);
	int getThreemaSafeBackupSize();

	void setThreemaSafeHashString(String hashString);
	String getThreemaSafeHashString();

	void setThreemaSafeBackupDate(Date date);
	Date getThreemaSafeBackupDate();

	void setWorkSyncCheckInterval(int checkInterval);
	int getWorkSyncCheckInterval();

	boolean getIsExportIdTooltipShown();

	void setThreemaSafeMDMConfig(String mdmConfigHash);
	String getThreemaSafeMDMConfig();

	void setWorkDirectoryEnabled(boolean enabled);
	boolean getWorkDirectoryEnabled();

	void setWorkDirectoryCategories(List<WorkDirectoryCategory> categories);

	List<WorkDirectoryCategory> getWorkDirectoryCategories();

	void setWorkOrganization(WorkOrganization organization);

	WorkOrganization getWorkOrganization();

	void setLicensedStatus(boolean licensed);
	boolean getLicensedStatus();

	void setShowDeveloperMenu(boolean show);
	boolean showDeveloperMenu();

	Uri getDataBackupUri();
	void setDataBackupUri(Uri newUri);

	Date getLastDataBackupDate();
	void setLastDataBackupDate(Date date);

	String getMatchToken();
	void setMatchToken(String matchToken);

	boolean isAfterWorkDNDEnabled();
	void setAfterWorkDNDEnabled(boolean enabled);

	void setCameraFlashMode(int flashMode);
	int getCameraFlashMode();

	void setCameraLensFacing(int lensFacing);
	int getCameraLensFacing();

	void setPipPosition(int pipPosition);
	int getPipPosition();

	boolean isVideoCallsEnabled();
	@Nullable String getVideoCallsProfile();

	void setBallotOverviewHidden(boolean hidden);
	boolean getBallotOverviewHidden();

	int getVideoCallToggleTooltipCount();
	void incremenetVideoCallToggleTooltipCount();

	boolean getCameraPermissionRequestShown();
	void setCameraPermissionRequestShown(boolean shown);

	boolean getDisableSmartReplies();

	@Nullable String getPoiServerHostOverride();

	void setLastSyncadapterRun(long timestampOfLastSync);
	long getLastSyncAdapterRun();

	boolean getIsImageLabelingTooltipShown();
	void setIsImageLabelingTooltipShown(boolean shown);

	boolean getIsImageResolutionTooltipShown();
	void setIsImageResolutionTooltipShown(boolean shown);
}
