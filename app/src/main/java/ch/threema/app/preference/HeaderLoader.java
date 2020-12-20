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

/*
 * Copyright (c) 2018 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package ch.threema.app.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import ch.threema.app.R;

/**
 * @author [大前良介 (OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
class HeaderLoader {
    static void loadFromResource(
            @NonNull final Context context,
            @XmlRes final int resId,
            @NonNull final List<Header> target) {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getXml(resId);
            loadFromResource(context, parser, target);
        } catch (final XmlPullParserException e) {
            throw new RuntimeException("Error parsing headers", e);
        } catch (final IOException e) {
            throw new RuntimeException("Error parsing headers", e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private static void loadFromResource(
            @NonNull final Context context,
            @NonNull final XmlResourceParser parser,
            @NonNull final List<Header> target)
            throws IOException, XmlPullParserException {
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        while (true) {
            final int type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT ||
                    type == XmlPullParser.START_TAG) {
                break;
            }
        }
        String nodeName = parser.getName();
        if (!"preference-headers".equals(nodeName)) {
            throw new RuntimeException(
                    "XML document must start with <preference-headers> tag; found"
                            + nodeName + " at " + parser.getPositionDescription());
        }
        final int startDepth = parser.getDepth();
        while (true) {
            final int type = parser.next();
            if (reachToEnd(type, parser.getDepth(), startDepth)) {
                break;
            }
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            nodeName = parser.getName();
            if ("header".equals(nodeName)) {
                target.add(parseHeaderSection(context, parser, attrs));
            } else {
                skipCurrentTag(parser);
            }
        }
    }

    private static Header parseHeaderSection(
            @NonNull final Context context,
            @NonNull final XmlResourceParser parser,
            @NonNull final AttributeSet attrs)
            throws IOException, XmlPullParserException {
        final Header header = new Header();
        final TypedArray sa = context.obtainStyledAttributes(attrs, R.styleable.PreferenceHeader);
        header.id = PreferenceActivityCompatDelegate.HEADER_ID_UNDEFINED;
        setTitle(header, sa.peekValue(R.styleable.PreferenceHeader_title));
        setSummary(header, sa.peekValue(R.styleable.PreferenceHeader_summary));
        setBreadCrumbTitle(header, sa.peekValue(R.styleable.PreferenceHeader_breadCrumbTitle));
        header.iconRes = sa.getResourceId(R.styleable.PreferenceHeader_icon, 0);
        header.fragment = sa.getString(R.styleable.PreferenceHeader_fragment);
        sa.recycle();
        parseIntentSection(context, parser, attrs, header);
        return header;
    }

    private static void setTitle(
            @NonNull final Header header,
            @Nullable final TypedValue tv) {
        if (tv == null || tv.type != TypedValue.TYPE_STRING) {
            return;
        }
        if (tv.resourceId != 0) {
            header.titleRes = tv.resourceId;
        } else {
            header.title = tv.string;
        }
    }

    private static void setSummary(
            @NonNull final Header header,
            @Nullable final TypedValue tv) {
        if (tv == null || tv.type != TypedValue.TYPE_STRING) {
            return;
        }
        if (tv.resourceId != 0) {
            header.summaryRes = tv.resourceId;
        } else {
            header.summary = tv.string;
        }
    }

    private static void setBreadCrumbTitle(
            @NonNull final Header header,
            @Nullable final TypedValue tv) {
        if (tv == null || tv.type != TypedValue.TYPE_STRING) {
            return;
        }
        if (tv.resourceId != 0) {
            header.breadCrumbTitleRes = tv.resourceId;
        } else {
            header.breadCrumbTitle = tv.string;
        }
    }

    private static void parseIntentSection(
            @NonNull final Context context,
            @NonNull final XmlResourceParser parser,
            @NonNull final AttributeSet attrs,
            @NonNull final Header header)
            throws IOException, XmlPullParserException {
        final Bundle curBundle = new Bundle();
        final int startDepth = parser.getDepth();
        while (true) {
            final int type = parser.next();
            if (reachToEnd(type, parser.getDepth(), startDepth)) {
                break;
            }
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            final String innerNodeName = parser.getName();
            switch (innerNodeName) {
                case "extra":
                    context.getResources().parseBundleExtra("extra", attrs, curBundle);
                    skipCurrentTag(parser);
                    break;
                case "intent":
                    header.intent = Intent.parseIntent(context.getResources(), parser, attrs);
                    break;
                default:
                    skipCurrentTag(parser);
                    break;
            }
        }
        if (curBundle.size() > 0) {
            header.fragmentArguments = curBundle;
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static void skipCurrentTag(final XmlPullParser parser)
            throws IOException, XmlPullParserException {
        final int startDepth = parser.getDepth();
        while (!reachToEnd(parser.next(), parser.getDepth(), startDepth)) ;
    }

    @SuppressWarnings("RedundantIfStatement")
    private static boolean reachToEnd(
            final int type,
            final int currentDepth,
            final int startDepth) {
        if (type == XmlPullParser.END_DOCUMENT) {
            return true;
        }
        if (type == XmlPullParser.END_TAG && currentDepth <= startDepth) {
            return true;
        }
        return false;
    }
}
