/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.notifications;

import android.content.SharedPreferences;
import android.net.Uri;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.base.utils.Base32;

public class NotificationChannelSettings {
    private String prefix;
    private long seqNum;
    private int importance;
    private boolean showBadge;
    private long[] vibrationPattern;
    private Integer lightColor;
    private Uri sound;
    private final String channelGroupId;
    private final String groupName;
    private final String description;
    private final int visibility;

    public NotificationChannelSettings(String channelGroupId, @NonNull String prefix, SharedPreferences sharedPreferences, int importance, boolean showBadge, int visibility, String groupName, String description, String seqPrefKey) {
        this.prefix = prefix;
        this.importance = importance;
        this.showBadge = showBadge;
        this.visibility = visibility;

        this.channelGroupId = channelGroupId;
        this.groupName = groupName;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getChannelGroupId() {
        return channelGroupId;
    }

    public long getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(long seqNum) {
        this.seqNum = seqNum;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(@NonNull String prefix) {
        this.prefix = prefix;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = importance;
    }

    public boolean isShowBadge() {
        return showBadge;
    }

    public void setShowBadge(boolean showBadge) {
        this.showBadge = showBadge;
    }

    public long[] getVibrationPattern() {
        return vibrationPattern;
    }

    public void setVibrationPattern(long[] vibratePattern) {
        this.vibrationPattern = vibratePattern;
    }

    public Integer getLightColor() {
        return lightColor;
    }

    public void setLightColor(Integer lightColor) {
        this.lightColor = lightColor;
    }

    public Uri getSound() {
        return sound;
    }

    public void setSound(Uri ringtoneUri) {
        this.sound = ringtoneUri;
    }

    public int getVisibility() {
        return visibility;
    }
}
