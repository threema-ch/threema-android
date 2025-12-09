/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.emojis;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.util.Pair;
import android.widget.TextView;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.MentionClickableSpan;
import ch.threema.app.ui.MentionSpan;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import kotlin.Lazy;

import static ch.threema.common.LazyKt.lazy;

public class EmojiMarkupUtil {

    private static final int LARGE_EMOJI_SCALE_FACTOR = 2;
    private static final int LARGE_EMOJI_THRESHOLD = 3;
    public static final String MENTION_INDICATOR = "@";
    protected static final String MENTION_REGEX = MENTION_INDICATOR + "\\[[0-9A-Z*@]{8}\\]";
    private final Pattern mention;

    @NonNull
    private static final Lazy<EmojiMarkupUtil> instance = lazy(EmojiMarkupUtil::new);

    @NonNull
    public static EmojiMarkupUtil getInstance() {
        return instance.getValue();
    }

    private EmojiMarkupUtil() {
        this.mention = Pattern.compile(MENTION_REGEX);

        // set emoji style based on preferences
        ConfigUtils.setEmojiStyle(ThreemaApplication.getAppContext(), -1);
    }

    // the ContactService may not yet exist when the instance is created (e.g. when masterkey is locked)
    private ContactService getContactService() {
        try {
            return ThreemaApplication.getServiceManager().getContactService();
        } catch (Exception e) {
            //
        }
        return null;
    }

    // the UserService may not yet exist when the instance is created (e.g. when masterkey is locked)
    private UserService getUserService() {
        try {
            return ThreemaApplication.getServiceManager().getUserService();
        } catch (Exception e) {
            //
        }
        return null;
    }


    public CharSequence addTextSpans(CharSequence text) {
        return addTextSpans(null, text, null, false, false);
    }

    public CharSequence addTextSpans(Context context, CharSequence text, TextView textView, boolean ignoreMarkup) {
        return addTextSpans(context, text, textView, ignoreMarkup, false);
    }

    public CharSequence addTextSpans(Context context, CharSequence text, TextView textView, boolean ignoreMarkup, boolean singleScale) {
        return addTextSpans(context, text, textView, ignoreMarkup, ignoreMarkup, singleScale, false);
    }

    /**
     * Add text spans to given CharSequence such as markup, mentions and emojis
     *
     * @param context                   A context
     * @param text                      CharSequence to add text spans to
     * @param textView                  The TextView where the text is going to be located (used for size calculations)
     * @param ignoreMarkup              Do not substitute markups with corresponding Spans
     * @param ignoreMentions            Do not substitute mentions with corresponding Spans
     * @param singleScale               Scale up single emojis in text containing emojis only
     * @param overrideEmojiStyleSetting Override the user's desired emoji style and always use the app supplied emoji set
     * @return CharSequence with spans applied, if any
     */
    @NonNull
    public CharSequence addTextSpans(
        @Nullable
        Context context,
        @Nullable
        CharSequence text,
        @Nullable
        TextView textView,
        boolean ignoreMarkup,
        boolean ignoreMentions,
        boolean singleScale,
        boolean overrideEmojiStyleSetting
    ) {
        if (text == null) {
            return "";
        }

        int length = text.length();

        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        if (length == 0) {
            return builder;
        }

        if (context != null && textView != null && (ConfigUtils.isDefaultEmojiStyle() || overrideEmojiStyleSetting || length <= 5)) {
            ArrayList<Pair<EmojiParser.ParseResult, Integer>> results = new ArrayList<>();
            boolean containsRegularText = false;

            for (int i = 0; i < length; i++) {
                // Try to find emoji at the specified index
                try {
                    final EmojiParser.ParseResult result = EmojiParser.parseAt(text, i);

                    if (result != null && result.length > 0) {
                        // An emoji was found!
                        results.add(new Pair<>(result, i));
                        i += result.length - 1;
                    } else {
                        containsRegularText = true;
                    }
                } catch (Exception e) {
                    containsRegularText = true;
                }
            }

            if (!results.isEmpty()) {
                int scaleFactor = singleScale && ConfigUtils.isBiggerSingleEmojis(context) && !containsRegularText && results.size() <= LARGE_EMOJI_THRESHOLD ? LARGE_EMOJI_SCALE_FACTOR : 1;

                if (ConfigUtils.isDefaultEmojiStyle() || overrideEmojiStyleSetting) {
                    for (Pair<EmojiParser.ParseResult, Integer> result : results) {
                        EmojiDrawable emojiDrawable = EmojiManager.getInstance(context).getEmojiDrawableAsync(result.first.coords);

                        if (emojiDrawable != null) {
                            builder.setSpan(new EmojiImageSpan(emojiDrawable, textView, scaleFactor),
                                result.second, result.second + result.first.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                } else if (scaleFactor != 1) {
                    for (Pair<EmojiParser.ParseResult, Integer> result : results) {
                        builder.setSpan(new RelativeSizeSpan(scaleFactor),
                            result.second, result.second + result.first.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }

        if (!ignoreMarkup) {
            MarkupParser.getInstance().markify(builder);
        }

        if (!ignoreMentions) {
            if (textView == null) {
                builder = new SpannableStringBuilder(applyTextMentionMarkup(text));
            } else {
                builder = applyMentionMarkup(context, builder);
            }
        }

        return builder;
    }

    private SpannableStringBuilder applyMentionMarkup(Context context, SpannableStringBuilder inputText) {
        return applyMentionMarkup(context, inputText, false);
    }

    private SpannableStringBuilder applyMentionMarkup(Context context, SpannableStringBuilder inputText, boolean isStrikeThrough) {
        int start, end;

        ArrayList<Pair<Integer, Integer>> matches = new ArrayList<>();

        Matcher matcher = this.mention.matcher(inputText);
        while (matcher.find()) {
            matches.add(new Pair<>(matcher.start(), matcher.end()));
        }

        if (matches.isEmpty()) {
            return inputText;
        }

        @ColorInt int mentionColor = context.getResources().getColor(R.color.mention_background);
        @ColorInt int invertedMentionColor = context.getResources().getColor(R.color.mention_background_inverted);
        @ColorInt int mentionTextColor = context.getResources().getColor(R.color.mention_text_color);
        @ColorInt int invertedMentionTextColor = context.getResources().getColor(R.color.mention_text_color_inverted);

        SpannableStringBuilder s = new SpannableStringBuilder(inputText);

        final StrikethroughSpan[] strikethroughSpans = s.getSpans(0, s.length(), StrikethroughSpan.class);
        final MentionSpan[] mentionSpans = s.getSpans(0, s.length(), MentionSpan.class);

        for (int i = matches.size() - 1; i >= 0; i--) {
            start = matches.get(i).first;
            end = matches.get(i).second;

            // Check if there is a strike through span surrounding this mention. If there is one,
            // then the line must be drawn explicitly as the mention draws over the text.
            boolean inStrikethroughSpan = isStrikeThrough;
            for (StrikethroughSpan sts : strikethroughSpans) {
                if (s.getSpanStart(sts) <= start && s.getSpanEnd(sts) >= end) {
                    inStrikethroughSpan = true;
                    break;
                }
            }

            MentionSpan mentionSpan = null;
            for (MentionSpan ms : mentionSpans) {
                if (s.getSpanStart(ms) == start) {
                    mentionSpan = ms;
                    break;
                }
            }

            // Create a new mention span if already available, otherwise update the existing span
            if (mentionSpan == null) {
                s.setSpan(new MentionSpan(mentionColor, invertedMentionColor, mentionTextColor, invertedMentionTextColor, inStrikethroughSpan), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                mentionSpan.setStrikeThrough(inStrikethroughSpan);
            }
            // hack: https://stackoverflow.com/questions/20069537/replacementspans-draw-method-isnt-called
            if (inputText.length() == end - start) {
                s.append(" ");
            }
            s.setSpan(new MentionClickableSpan(inputText.subSequence(start + 2, end - 1).toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /**
     * Replace mentions by text instead of spans (used where spans cannot be displayed, i.e. in notifications)
     *
     * @return ChatSequence where all mentions have been replaced by contact names or ids
     */
    private CharSequence applyTextMentionMarkup(CharSequence inputText) {
        String match, identity;
        CharSequence outputText = inputText;

        Matcher matcher = this.mention.matcher(inputText);
        while (matcher.find()) {
            match = matcher.group();
            identity = match.substring(2, match.length() - 1);
            String quoteName = NameUtil.getQuoteName(identity, getContactService(), getUserService());

            if (TestUtil.isEmptyOrNull(quoteName)) {
                // Note that the quote name is only empty if there went something wrong while
                // accessing the contact. If the contact is unknown, the quote name consists of its
                // threema id.
                outputText = TextUtils.replace(outputText, new String[]{match}, new CharSequence[]{""});
            } else {
                outputText = TextUtils.replace(outputText, new String[]{match}, new CharSequence[]{MENTION_INDICATOR +
                    quoteName});
            }
            matcher = this.mention.matcher(outputText);
        }

        return outputText;
    }

    public CharSequence addMarkup(Context context, CharSequence text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        MarkupParser.getInstance().markify(builder);
        builder = applyMentionMarkup(context, builder);

        return builder;
    }

    public CharSequence addMentionMarkup(Context context, CharSequence text) {
        return addMentionMarkup(context, text, false);
    }

    public CharSequence addMentionMarkup(Context context, CharSequence text, boolean isStrikeThrough) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        builder = applyMentionMarkup(context, builder, isStrikeThrough);

        return builder;
    }

    public String stripMentions(String inputText) {
        return inputText.replaceAll(MENTION_REGEX, "");
    }

    public CharSequence formatBodyTextString(Context context, String string, int maxLen) {
        if (string != null && !string.isEmpty())
            return addMarkup(context, string.substring(0, Math.min(maxLen, string.length())));
        else
            return "";
    }
}


