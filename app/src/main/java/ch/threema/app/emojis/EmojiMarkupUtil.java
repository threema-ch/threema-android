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

package ch.threema.app.emojis;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Pair;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.MentionClickableSpan;
import ch.threema.app.ui.MentionSpan;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.NameUtil;

public class EmojiMarkupUtil {
	private static final Logger logger = LoggerFactory.getLogger(EmojiMarkupUtil.class);

	private static final int LARGE_EMOJI_SCALE_FACTOR = 2;
	private static final int LARGE_EMOJI_THRESHOLD = 3;
	public static final String MENTION_INDICATOR = "@";
	protected static final String MENTION_REGEX = MENTION_INDICATOR + "\\[[0-9A-Z*@]{8}\\]";
	private final Pattern mention;

	// Singleton stuff
	private static EmojiMarkupUtil sInstance = null;

	public static synchronized EmojiMarkupUtil getInstance() {
		if (sInstance == null) {
			sInstance = new EmojiMarkupUtil();
		}
		return sInstance;
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
		return addTextSpans(context, text, textView, ignoreMarkup, ignoreMarkup, singleScale);
	}

	public CharSequence addTextSpans(Context context, CharSequence text, TextView textView, boolean ignoreMarkup, boolean ignoreMentions, boolean singleScale) {
		if (text == null) {
			return "";
		}

		int length = text.length();

		SpannableStringBuilder builder = new SpannableStringBuilder(text);

		if (length == 0) {
			return builder;
		}

		if (context != null && textView != null && (ConfigUtils.isDefaultEmojiStyle() || length <= 5)) {
			ArrayList<Pair<EmojiParser.ParseResult, Integer>> results = new ArrayList<>();
			boolean containsRegularText = false;

			for (int i = 0; i < length; i++) {
				// Try to find emoji at the specified index
				final EmojiParser.ParseResult result = EmojiParser.parseAt(text, i);

				if (result != null && result.length > 0) {
					// An emoji was found!
					results.add(new Pair<>(result, i));
					i += result.length - 1;
				} else {
					containsRegularText = true;
				}
			}

			if (results.size() > 0) {
				int scaleFactor = singleScale && ConfigUtils.isBiggerSingleEmojis(context) && !containsRegularText && results.size() <= LARGE_EMOJI_THRESHOLD ? LARGE_EMOJI_SCALE_FACTOR : 1;

				if (ConfigUtils.isDefaultEmojiStyle()) {
					for (Pair<EmojiParser.ParseResult, Integer> result : results) {
						Drawable drawable = EmojiManager.getInstance(context).getEmojiDrawable(result.first.coords);

						if (drawable != null) {
							builder.setSpan(new EmojiImageSpan(drawable, textView, scaleFactor),
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

		if (!ignoreMentions) {
			if (textView == null) {
				builder = new SpannableStringBuilder(applyTextMentionMarkup(text));
			} else {
				builder = applyMentionMarkup(context, builder);
			}
		}

		if (!ignoreMarkup) {
			MarkupParser.getInstance().markify(builder);
		}

		return builder;
	}

	private SpannableStringBuilder applyMentionMarkup(Context context, SpannableStringBuilder inputText) {
		int start, end;

		ArrayList<Pair<Integer, Integer>> matches = new ArrayList<>();

		Matcher matcher = this.mention.matcher(inputText);
		while (matcher.find()) {
			matches.add(new Pair<>(matcher.start(), matcher.end()));
		}

		if (matches.size() < 1) {
			return inputText;
		}

		@ColorInt int mentionColor = ConfigUtils.getColorFromAttribute(context, R.attr.mention_background);
		@ColorInt int invertedMentionColor = ConfigUtils.getColorFromAttribute(context, R.attr.mention_background_inverted);
		@ColorInt int mentionTextColor = ConfigUtils.getColorFromAttribute(context, R.attr.mention_text_color);
		@ColorInt int invertedMentionTextColor = ConfigUtils.getColorFromAttribute(context, R.attr.mention_text_color_inverted);

		SpannableStringBuilder s = new SpannableStringBuilder(inputText);

		for (int i = matches.size() - 1; i >= 0; i--) {
			start = matches.get(i).first;
			end = matches.get(i).second;

			s.setSpan(new MentionSpan(mentionColor, invertedMentionColor, mentionTextColor, invertedMentionTextColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
	 * @param inputText
	 * @return ChatSequence where all mentions have been replaced by contact names
	 */
	private CharSequence applyTextMentionMarkup(CharSequence inputText) {
		String match, identity;
		CharSequence outputText = inputText;

		Matcher matcher = this.mention.matcher(inputText);
		while (matcher.find()) {
			match = matcher.group();
			identity = match.substring(2, match.length() - 1);
			outputText = TextUtils.replace(outputText, new String[] {match}, new CharSequence[] {MENTION_INDICATOR +
					NameUtil.getQuoteName(identity, getContactService(), getUserService())});
			matcher = this.mention.matcher(outputText);
		}

		return outputText;
	}

	public CharSequence addMarkup(Context context, CharSequence text) {
		SpannableStringBuilder builder = new SpannableStringBuilder(text);

		builder = applyMentionMarkup(context, builder);
		MarkupParser.getInstance().markify(builder);

		return builder;
	}

	public CharSequence addMentionMarkup(Context context, CharSequence text) {
		SpannableStringBuilder builder = new SpannableStringBuilder(text);

		builder = applyMentionMarkup(context, builder);

		return builder;
	}

	public String stripMentions(String inputText) {
		return inputText.replaceAll(MENTION_REGEX, "");
	}

	public CharSequence formatBodyTextString(Context context, String string, int maxLen) {
		if (string != null && string.length() > 0)
			return addMarkup(context, string.substring(0, Math.min(maxLen, string.length())));
		else
			return "";
	}
}


