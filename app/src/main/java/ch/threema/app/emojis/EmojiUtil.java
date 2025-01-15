/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.emojis.EmojiSpritemap.emojiCategories;

public class EmojiUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("EmojiUtil");

	public static final String REPLACEMENT_CHARACTER = "\uFFFD";
	public static final String THUMBS_UP_SEQUENCE = "\uD83D\uDC4D";
	public static final String THUMBS_DOWN_SEQUENCE = "\uD83D\uDC4E";

	private static final Set<String> THUMBS_UP_VARIANTS = Set.of(
		"\ud83d\udc4d",
		"\ud83d\udc4d\ud83c\udffb",
		"\ud83d\udc4d\ud83c\udffc",
		"\ud83d\udc4d\ud83c\udffd",
		"\ud83d\udc4d\ud83c\udffe",
		"\ud83d\udc4d\ud83c\udfff"
	);

	private static final Set<String> THUMBS_DOWN_VARIANTS = Set.of(
		"\ud83d\udc4e",
		"\ud83d\udc4e\ud83c\udffb",
		"\ud83d\udc4e\ud83c\udffc",
		"\ud83d\udc4e\ud83c\udffd",
		"\ud83d\udc4e\ud83c\udffe",
		"\ud83d\udc4e\ud83c\udfff"
	);

    private static WeakReference<Set<String>> FULLY_QUALIFIED_EMOJI = new WeakReference<>(null);

	@Nullable
	public static EmojiInfo getEmojiInfo(String emojiSequence) {
		for (EmojiCategory emojiCategory : emojiCategories) {
			for (EmojiInfo emojiInfo : emojiCategory.emojiInfos) {
				if (emojiInfo.emojiSequence.equals(emojiSequence)) {
					return emojiInfo;
				}
			}
		}
		return null;
	}

    public static boolean isFullyQualifiedEmoji(@Nullable CharSequence emojiSequence) {
        if (emojiSequence == null) {
            return false;
        }
        return getFullyQualifiedEmoji().contains(emojiSequence.toString());
    }

    @NonNull
    private static synchronized Set<String> getFullyQualifiedEmoji() {
        Set<String> fullyQualifiedEmoji = FULLY_QUALIFIED_EMOJI.get();
        if (fullyQualifiedEmoji == null) {
            logger.debug("Prepare set of fully qualified emoji");
            fullyQualifiedEmoji = emojiCategories.stream()
                .flatMap(category -> category.emojiInfos.stream())
                .map(info -> info.emojiSequence)
                .collect(Collectors.toSet());
            FULLY_QUALIFIED_EMOJI = new WeakReference<>(fullyQualifiedEmoji);
        }
        return fullyQualifiedEmoji;
    }

	/**
	 * Checks if the given emoji sequence is a thumbs up emoji regardless of color
	 * @param emojiSequence The unicode sequence to check
	 * @return true if the emoji sequence is a thumbs up emoji, false otherwise
	 */
	public static boolean isThumbsUpEmoji(String emojiSequence) {
		return EmojiUtil.THUMBS_UP_VARIANTS.contains(emojiSequence);
	}

	/**
	 * Checks if the given emoji sequence is a thumbs down emoji regardless of color
	 * @param emojiSequence The unicode sequence to check
	 * @return true if the emoji sequence is a thumbs down emoji, false otherwise
	 */
	public static boolean isThumbsDownEmoji(String emojiSequence) {
		return EmojiUtil.THUMBS_DOWN_VARIANTS.contains(emojiSequence);
	}

    public static boolean isThumbsUpOrDownEmoji(@NonNull String emojiSequence) {
        return isThumbsUpEmoji(emojiSequence) || isThumbsDownEmoji(emojiSequence);
    }
}
