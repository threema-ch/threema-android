/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import static ch.threema.app.preference.SettingsAdvancedOptionsFragment.THREEMA_SUPPORT_IDENTITY;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.receivers.SendTextToContactBroadcastReceiver;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;

public class BackgroundErrorNotification {

    public static final String EXTRA_TEXT_TO_SEND = "text";
    public static final String EXTRA_NOTIFICATION_ID = "notId";

    /**
     * Show a notification for an error that happened in the background.
     * <p>
     * If `sendToSupport` is set, tapping on the notification action will result
     * in a message being composed to the support.
     *
     * @param appContext    The application context.
     * @param title         Notification title.
     * @param text          Notification body (without instructions on how to contact support).
     * @param scope         The scope where the error occurred, e.g. "VoipCallService".
     * @param sendToSupport Whether a "send to support" action should be shown or not.
     * @param exception     An optional throwable. If set, a stack trace will be included in the support text.
     */
    @SuppressLint("MissingPermission")
    public static void showNotification(
        @NonNull Context appContext,
        @NonNull String title,
        @NonNull String text,
        @NonNull String scope,
        boolean sendToSupport,
        @Nullable Throwable exception
    ) {
        final NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(appContext);
        final NotificationCompat.Builder builder =
            new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setTicker(text)
                .setContentTitle(appContext.getString(R.string.error) + ": " + title)
                .setContentText(text)
                .setTimeoutAfter(30 * DateUtils.MINUTE_IN_MILLIS)
                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true);

        if (sendToSupport) {
            // When tapping on the notification, a conversation with *SUPPORT will be started.
            // The text should contain all the necessary information.
            final String separator = "\n------\n";
            final StringBuilder supportChannelText = new StringBuilder();
            supportChannelText.append("Hello Threema Support!\n\nAn error occurred in ").append(scope).append(":");
            supportChannelText.append(separator).append(text).append(separator);
            if (exception != null) {
                supportChannelText.append(exception).append(separator);
            }
            supportChannelText.append("My phone model: ")
                .append(ConfigUtils.getSupportDeviceInfo());
            supportChannelText.append("\nMy app version: ")
                .append(ConfigUtils.getAppVersion());
            supportChannelText.append("\nMy app language: ")
                .append(LocaleUtil.getAppLanguage());

            // Reuse the AppLinksActivity class that handles universal links
            Intent replyIntent = new Intent(appContext, SendTextToContactBroadcastReceiver.class);
            replyIntent.putExtra(EXTRA_TEXT_TO_SEND, supportChannelText.toString());
            replyIntent.putExtra(EXTRA_NOTIFICATION_ID, NotificationIDs.BACKGROUND_ERROR);

            replyIntent.putExtra(AppConstants.INTENT_DATA_CONTACT, THREEMA_SUPPORT_IDENTITY);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                appContext,
                (int) System.nanoTime(),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);

            // The intent should be triggered by tapping on a notification action
            final NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                // NOTE: Do *not* use a vector drawable here, it crashes on Android 4!
                R.drawable.ic_send_grey600_24dp,
                appContext.getString(R.string.send_to_support),
                pendingIntent
            ).build();

            builder.addAction(action);
        }

        notificationManagerCompat.notify(NotificationIDs.BACKGROUND_ERROR, builder.build());
    }

    /**
     * Like {@link #showNotification(Context, String, String, String, boolean, Throwable)},
     * but accepts string resource references instead of strings.
     */
    public static void showNotification(
        @NonNull Context appContext,
        @StringRes int title,
        @StringRes int text,
        @NonNull String scope,
        boolean sendToSupport,
        @Nullable Throwable throwable
    ) {
        showNotification(
            appContext,
            appContext.getString(title),
            appContext.getString(text),
            scope,
            sendToSupport,
            throwable
        );
    }

}
