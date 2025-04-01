/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.ConfigurationCompat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.Locale;

import androidx.core.os.LocaleListCompat;

public class LocaleUtil {
    public static final String UTF8_ENCODING = "UTF-8";

    private LocaleUtil() {
        // Forbidden being instantiated.
    }

    @NonNull
    public static String getLanguage() {
        try {
            return URLEncoder.encode(Locale.getDefault().getLanguage(), UTF8_ENCODING);
        } catch (UnsupportedEncodingException ignored) {
        }
        return "en";
    }

    @NonNull
    public static String getAppLanguage() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            Locale appLocale = locales.get(0);

            if (appLocale != null && !TestUtil.isEmptyOrNull(appLocale.getLanguage())) {
                return appLocale.getLanguage();
            }
        }
        return LocaleUtil.getLanguage();
    }

    /**
     * Return the current locale.
     */
    public static Locale getCurrentLocale(@NonNull Context context) {
        return ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
    }

    public static String formatTimeStampStringAbsolute(Context context, long when) {
        return DateUtils.formatDateTime(context, when, DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL);
    }

    public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
        String time = DateUtils.formatDateTime(context, when, DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_TIME);

        if (DateUtils.isToday(when)) {
            return time;
        }

        if (fullFormat) {
            return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, 0).toString() + ", " + time;
        }

        return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL).toString();
    }

    public static String formatDateRelative(long when) {
        return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_SHOW_DATE).toString();
    }

    public static String formatTimerText(long elapsedTime, boolean showMs) {
        int minutes = (int) (elapsedTime / 60000);
        int seconds = (int) ((elapsedTime % 60000) / 1000);

        if (showMs) {
            int milliseconds = (int) ((elapsedTime % 60000) % 1000);

            if (elapsedTime > DateUtils.HOUR_IN_MILLIS) {
                return String.format(Locale.US, "%d:%02d:%02d.%02d",
                    minutes / 60,
                    minutes % 60,
                    seconds,
                    milliseconds / 10);
            } else {
                return String.format(Locale.US, "%01d:%02d.%02d", minutes, seconds, milliseconds / 10);
            }
        }
        return String.format(Locale.US, "%01d:%02d", minutes, seconds);
    }

    /**
     * Normalize and uppercase a string for comparison, removing all diacritical marks
     *
     * @param input non-normalized string
     * @return normalized string or original string if error occurs
     */
    public static @NonNull String normalize(@NonNull String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        if (!TestUtil.isEmptyOrNull(normalized)) {
            String replaced = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            if (!TestUtil.isEmptyOrNull(replaced)) {
                return replaced.toUpperCase();
            }
        }
        return input;
    }

    /**
     * Map the given target locale to the corresponding notation of the given locales array.
     *
     * @param target  the locale that should be mapped to the best matching locale
     * @param locales the selection of possible locales
     * @return the locale of the given locales that best fits the target, the empty string if no
     * match is possible
     */
    @NonNull
    public static String mapLocaleToPredefinedLocales(@Nullable Locale target, @NonNull String[] locales) {
        if (target == null || "".equals(target.getLanguage()) || target.getLanguage().length() < 2) {
            return "";
        }

        // Special case for chinese: select region based on script (as android does)
        if ("zh".equals(target.getLanguage())) {
            switch (target.getScript().toLowerCase()) {
                case "hant":
                    // Traditional chinese is used in TW (independent of region)
                    return "zh-hant-TW";
                case "hans":
                default:
                    // Simplified chinese (default) is used in CN (independent of region)
                    return "zh-hans-CN";
            }
        }

        // Find our language string based on language, region, and variant
        String exactMatch = findMatch(target, locales);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Find our language string based on the language and region (ignoring the variant)
        Locale languageCountryTarget = new Locale(
            target.getLanguage(),
            target.getCountry()
        );
        String languageCountryMatch = findMatch(languageCountryTarget, locales);
        if (languageCountryMatch != null) {
            return languageCountryMatch;
        }

        // Find our language string based only on the language (ignoring the region and variant)
        Locale languageTarget = new Locale(
            target.getLanguage()
        );
        for (String locale : locales) {
            Locale onlyLanguage = new Locale(Locale.forLanguageTag(locale).getLanguage());
            if (onlyLanguage.equals(languageTarget)) {
                return locale;
            }
        }

        // We could not map the language to one of our supported languages, so we use the system
        // default
        return "";
    }

    @Nullable
    private static String findMatch(@NonNull Locale target, @NonNull String[] locales) {
        for (String locale : locales) {
            if (Locale.forLanguageTag(locale).equals(target)) {
                return locale;
            }
        }
        return null;
    }

}
