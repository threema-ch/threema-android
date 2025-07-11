/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.threema.app.utils;

import android.annotation.SuppressLint;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.text.util.Linkify.MatchFilter;
import android.text.util.Linkify.TransformFilter;
import android.widget.TextView;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.PatternsCompat;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.LocaleService;

/**
 * LinkifyCompatUtil is based on AOSPs LinkifyCompat ensuring consistent behaviour across different Android versions
 * and device manufacturers as well as fixing the lenient phone number handling on some devices
 */
public final class LinkifyCompatUtil {

    private static final Comparator<LinkSpec> COMPARATOR = (a, b) -> {
        if (a.start < b.start) {
            return -1;
        }

        if (a.start > b.start) {
            return 1;
        }

        return Integer.compare(b.end, a.end);

    };

    @IntDef(flag = true, value = {Linkify.WEB_URLS, Linkify.EMAIL_ADDRESSES, Linkify.PHONE_NUMBERS,
        Linkify.MAP_ADDRESSES, Linkify.ALL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LinkifyMask {
    }

    /**
     * Scans the text of the provided Spannable and turns all occurrences
     * of the link types indicated in the mask into clickable links.
     * If the mask is nonzero, it also removes any existing URLSpans
     * attached to the Spannable, to avoid problems if you call it
     * repeatedly on the same text.
     *
     * @param text Spannable whose text is to be marked-up with links
     * @param mask Mask to define which kinds of links will be searched.
     * @return True if at least one link is found and applied.
     */
    @SuppressLint("RestrictedApi")
    public static boolean addLinks(@NonNull Spannable text, @LinkifyMask int mask) {
        if (shouldAddLinksFallbackToFramework()) {
            return Linkify.addLinks(text, mask);
        }
        if (mask == 0) {
            return false;
        }

        URLSpan[] old = text.getSpans(0, text.length(), URLSpan.class);

        for (int i = old.length - 1; i >= 0; i--) {
            text.removeSpan(old[i]);
        }

        final ArrayList<LinkSpec> links = new ArrayList<>();

        if ((mask & Linkify.WEB_URLS) != 0) {
            gatherLinks(links, text, PatternsCompat.AUTOLINK_WEB_URL,
                new String[]{"http://", "https://", "rtsp://"},
                Linkify.sUrlMatchFilter, null);
        }

        if ((mask & Linkify.EMAIL_ADDRESSES) != 0) {
            gatherLinks(links, text, PatternsCompat.AUTOLINK_EMAIL_ADDRESS,
                new String[]{"mailto:"},
                null, null);
        }

        // Threema-added
        if ((mask & Linkify.PHONE_NUMBERS) != 0) {
            gatherTelLinks(links, text);
        }

        pruneOverlaps(links, text);

        if (links.isEmpty()) {
            return false;
        }

        for (LinkSpec link : links) {
            if (link.frameworkAddedSpan == null) {
                applyLink(link.url, link.start, link.end, text);
            }
        }

        return true;
    }

    /**
     * Scans the text of the provided TextView and turns all occurrences of
     * the link types indicated in the mask into clickable links.  If matches
     * are found the movement method for the TextView is set to
     * LinkMovementMethod.
     *
     * @param text TextView whose text is to be marked-up with links
     * @param mask Mask to define which kinds of links will be searched.
     * @return True if at least one link is found and applied.
     */
    public static boolean addLinks(@NonNull TextView text, @LinkifyMask int mask) {
        if (shouldAddLinksFallbackToFramework()) {
            return Linkify.addLinks(text, mask);
        }
        if (mask == 0) {
            return false;
        }

        CharSequence t = text.getText();

        if (t instanceof Spannable) {
            if (addLinks((Spannable) t, mask)) {
                addLinkMovementMethod(text);
                return true;
            }

            return false;
        } else {
            SpannableString s = SpannableString.valueOf(t);

            if (addLinks(s, mask)) {
                addLinkMovementMethod(text);
                text.setText(s);

                return true;
            }

            return false;
        }
    }

    private static boolean shouldAddLinksFallbackToFramework() {
        // Threema-added: Never use the system's linkify
        return false;
        // return Build.VERSION.SDK_INT >= 28;
    }

    private static void addLinkMovementMethod(@NonNull TextView t) {
        MovementMethod m = t.getMovementMethod();

        if (!(m instanceof LinkMovementMethod)) {
            if (t.getLinksClickable()) {
                t.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    private static String makeUrl(@NonNull String url, @NonNull String[] prefixes,
                                  Matcher matcher, @Nullable TransformFilter filter) {
        if (filter != null) {
            url = filter.transformUrl(matcher, url);
        }

        boolean hasPrefix = false;

        for (int i = 0; i < prefixes.length; i++) {
            if (url.regionMatches(true, 0, prefixes[i], 0, prefixes[i].length())) {
                hasPrefix = true;

                // Fix capitalization if necessary
                if (!url.regionMatches(false, 0, prefixes[i], 0, prefixes[i].length())) {
                    url = prefixes[i] + url.substring(prefixes[i].length());
                }

                break;
            }
        }

        if (!hasPrefix && prefixes.length > 0) {
            url = prefixes[0] + url;
        }

        return url;
    }

    private static void gatherLinks(ArrayList<LinkSpec> links,
                                    Spannable s, Pattern pattern, String[] schemes,
                                    MatchFilter matchFilter, TransformFilter transformFilter) {
        Matcher m = pattern.matcher(s);

        while (m.find()) {
            int start = m.start();
            int end = m.end();

            if (matchFilter == null || matchFilter.acceptMatch(s, start, end)) {
                LinkSpec spec = new LinkSpec();
                String url = makeUrl(m.group(0), schemes, m, transformFilter);

                spec.url = url;
                spec.start = start;
                spec.end = end;

                links.add(spec);
            }
        }
    }

    private static boolean gatherTelLinks(@NonNull ArrayList<LinkSpec> links, @NonNull Spannable s) {
        // Threema-added: try to get the current locale from LocaleService
        String countryCode;
        try {
            LocaleService localeService = ThreemaApplication.getServiceManager().getLocaleService();
            countryCode = localeService.getCountryIsoCode();
        } catch (Exception e) {
            countryCode = Locale.getDefault().getCountry();
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        // Threema-changed: only allow for valid phone numbers
        Iterable<PhoneNumberMatch> matches = phoneUtil.findNumbers(s.toString(), countryCode, PhoneNumberUtil.Leniency.VALID, Long.MAX_VALUE);
        for (PhoneNumberMatch match : matches) {
            LinkSpec spec = new LinkSpec();
            spec.url = "tel:" + PhoneNumberUtils.normalizeNumber(match.rawString());
            spec.start = match.start();
            spec.end = match.end();
            links.add(spec);
        }
        return !links.isEmpty();
    }

    private static void applyLink(String url, int start, int end, Spannable text) {
        URLSpan span = new URLSpan(url);

        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void pruneOverlaps(ArrayList<LinkSpec> links, Spannable text) {
        // Append spans added by framework
        URLSpan[] urlSpans = text.getSpans(0, text.length(), URLSpan.class);
        for (int i = 0; i < urlSpans.length; i++) {
            LinkSpec spec = new LinkSpec();
            spec.frameworkAddedSpan = urlSpans[i];
            spec.start = text.getSpanStart(urlSpans[i]);
            spec.end = text.getSpanEnd(urlSpans[i]);
            links.add(spec);
        }

        Collections.sort(links, COMPARATOR);

        int len = links.size();
        int i = 0;

        while (i < len - 1) {
            LinkSpec a = links.get(i);
            LinkSpec b = links.get(i + 1);
            int remove = -1;

            if ((a.start <= b.start) && (a.end > b.start)) {
                if (b.end <= a.end) {
                    remove = i + 1;
                } else if ((a.end - a.start) > (b.end - b.start)) {
                    remove = i + 1;
                } else if ((a.end - a.start) < (b.end - b.start)) {
                    remove = i;
                }

                if (remove != -1) {
                    URLSpan span = links.get(remove).frameworkAddedSpan;
                    if (span != null) {
                        text.removeSpan(span);
                    }
                    links.remove(remove);
                    len--;
                    continue;
                }

            }

            i++;
        }
    }

    /**
     * Do not create this static utility class.
     */
    private LinkifyCompatUtil() {
    }

    private static class LinkSpec {
        URLSpan frameworkAddedSpan;
        String url;
        int start;
        int end;

        LinkSpec() {
        }
    }
}
