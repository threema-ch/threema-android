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

package ch.threema.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import java.util.regex.Pattern;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupModel;

public class ComposeEditText extends EmojiEditText implements MentionSelectorPopup.MentionSelectorListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ComposeEditText");

    private static final int CONTEXT_MENU_BOLD = 700;
    private static final int CONTEXT_MENU_ITALIC = 701;
    private static final int CONTEXT_MENU_STRIKETHRU = 702;
    private static final int CONTEXT_MENU_GROUP = 22100;

    private Context context;
    private boolean isLocked = false;
    private MentionTextWatcher mentionTextWatcher = null;
    private MentionPopupData mentionPopupData;
    private TextInputLayout mentionPopupBoundary;
    private MentionSelectorPopup mentionPopup;

    private final android.view.ActionMode.Callback textSelectionCallback = new android.view.ActionMode.Callback() {
        private final Pattern pattern = Pattern.compile("\\B");

        @Override
        public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
            menu.removeGroup(CONTEXT_MENU_GROUP);

            if (getText() != null) {
                String text = getText().toString();
                if (text.length() > 1) {
                    int start = getSelectionStart();
                    int end = getSelectionEnd();

                    try {
                        if ((start <= 0 || pattern.matcher(text.substring(start - 1, start)).find()) &&
                            (end >= text.length() || pattern.matcher(text.substring(end, end + 1)).find()) &&
                            !text.substring(start, end).contains("\n")) {
                            menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_BOLD, 200, R.string.bold);
                            menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_ITALIC, 201, R.string.italic);
                            menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_STRIKETHRU, 203, R.string.strikethrough);
                        }
                    } catch (Exception e) {
                        // do not add menus if an error occurs
                    }
                }
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case CONTEXT_MENU_BOLD:
                    addMarkup("*");
                    break;
                case CONTEXT_MENU_ITALIC:
                    addMarkup("_");
                    break;
                case CONTEXT_MENU_STRIKETHRU:
                    addMarkup("~");
                    break;
                default:
                    return false;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(android.view.ActionMode mode) {
            // nothing to do here
        }

        private void addMarkup(String string) {
            Editable editable = getText();

            if (editable != null && editable.length() > 0) {
                int start = getSelectionStart();
                int end = getSelectionEnd();

                editable.insert(end, string);
                editable.insert(start, string);
            }
            invalidate();
        }
    };

    public ComposeEditText(Context context) {
        super(context);

        init(context);
    }

    public ComposeEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context);
    }

    public ComposeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        this.context = context;

        PreferenceService preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();

        this.setImeOptions(getImeOptions() | (EditorInfo.IME_ACTION_SEND & ~EditorInfo.IME_FLAG_NO_FULLSCREEN));
        this.setRawInputType(preferenceService.isEnterToSend() ?
            InputType.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT |
                (preferenceService.getEmojiStyle() == PreferenceService.EmojiStyle_ANDROID ? EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE : 0) :
            InputType.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);

        setFilters(appendMentionFilter(this.getFilters()));

        this.mentionTextWatcher = new MentionTextWatcher(this);
        new MarkupTextWatcher(context, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // do not add on lollipop or lower due to this bug: https://issuetracker.google.com/issues/36937508
            setCustomSelectionActionModeCallback(textSelectionCallback);
        }
    }

    /**
     * Add our MentionFilter as the first item to the array of existing InputFilters
     *
     * @param originalFilters
     * @return Array of filters
     */
    private InputFilter[] appendMentionFilter(@Nullable InputFilter[] originalFilters) {
        InputFilter[] result;

        if (originalFilters != null) {
            result = new InputFilter[originalFilters.length + 1];
            System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
        } else {
            result = new InputFilter[1];
        }
        result[0] = new MentionFilter(this.context);

        return result;
    }

    /**
     * Add mention at the current cursor position
     *
     * @param identity
     */
    public void addMention(String identity) {
        final int start = getSelectionStart();
        final int end = getSelectionEnd();

        // fix reverse selections
        getText().replace(Math.min(start, end), Math.max(start, end), "@[" + identity + "]");
    }

    /**
     * Enable the mention popup for this edit text.
     */
    public void enableMentionPopup(
        @NonNull Activity activity,
        @NonNull GroupService groupService,
        @NonNull ContactService contactService,
        @NonNull UserService userService,
        @NonNull PreferenceService preferenceService,
        @NonNull GroupModel groupModel,
        @Nullable TextInputLayout mentionPopupBoundary
    ) {
        mentionPopupData = new MentionPopupData(
            activity,
            groupService,
            contactService,
            userService,
            preferenceService,
            groupModel
        );

        this.mentionPopupBoundary = mentionPopupBoundary;
    }

    /**
     * Enable the mention popup for this edit text.
     */
    public void enableMentionPopup(@NonNull MentionPopupData data, @Nullable TextInputLayout boundary) {
        this.mentionPopupData = data;
        this.mentionPopupBoundary = boundary;
    }

    /**
     * Return true if the mention popup is currently showing
     */
    public boolean isMentionPopupShowing() {
        return mentionPopup != null && mentionPopup.isShowing();
    }

    /**
     * Dismiss the mention popup (if currently shown)
     */
    public void dismissMentionPopup() {
        if (mentionPopup != null) {
            try {
                mentionPopup.dismiss();
            } catch (IllegalArgumentException ignored) {
                // whatever
            } finally {
                mentionPopup = null;
            }
        }
    }

    public void setLocked(boolean isLocked) {
        this.isLocked = isLocked;
        setLongClickable(!isLocked);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            return !isLocked && super.onTouchEvent(event);
        } catch (IndexOutOfBoundsException e) {
            logger.error("Exception", e);
            return false;
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int maxLines = getResources().getInteger(R.integer.message_edittext_max_lines);

        if (this.mentionTextWatcher != null) {
            if (TestUtil.isBlankOrNull(getText())) {
                // workaround to keep hint ellipsized on the first line
                setMaxLines(1);
                setHint(this.hint);
            } else {
                setMaxLines(maxLines);
                this.mentionTextWatcher.setMaxLines(maxLines);
            }
        } else {
            setMaxLines(maxLines);
        }
    }

    @Override
    protected void onTextChanged(CharSequence s, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(s, start, lengthBefore, lengthAfter);

        if (mentionPopupData == null) {
            return;
        }

        if (lengthAfter == 1 && s.charAt(start) == '@' && start < s.length() // if current char is @ and only if adding a new char
            && (start == 0 || s.charAt(start - 1) == ' ' || s.charAt(start - 1) == '\n') // and only show if at start or if there is empty space before to not interrupt typing mail addresses
            && (s.length() <= start + 1 || s.charAt(start + 1) == ' ' || s.charAt(start + 1) == '\n') // and only show if @ is at the very end or also has empty space in the back.
        ) {
            mentionPopup = new MentionSelectorPopup(
                mentionPopupData.activity,
                this,
                mentionPopupData.groupService,
                mentionPopupData.contactService,
                mentionPopupData.userService,
                mentionPopupData.preferenceService,
                mentionPopupData.groupModel
            );
            mentionPopup.show(mentionPopupData.activity, this, mentionPopupBoundary);
        }
    }

    @Override
    public void onContactSelected(String identity, int length, int insertPosition) {
        Editable editable = getText();
        if (editable == null) {
            return;
        }

        if (insertPosition >= 0 && insertPosition <= editable.length()) {
            editable.delete(insertPosition, insertPosition + length);
            addMention(identity);
        }
    }

    public static class MentionPopupData {
        final @NonNull Activity activity;
        final @NonNull GroupService groupService;
        final @NonNull ContactService contactService;
        final @NonNull UserService userService;
        final @NonNull PreferenceService preferenceService;
        final @NonNull GroupModel groupModel;

        public MentionPopupData(
            @NonNull Activity activity,
            @NonNull GroupService groupService,
            @NonNull ContactService contactService,
            @NonNull UserService userService,
            @NonNull PreferenceService preferenceService,
            @NonNull GroupModel groupModel
        ) {
            this.activity = activity;
            this.groupService = groupService;
            this.contactService = contactService;
            this.userService = userService;
            this.preferenceService = preferenceService;
            this.groupModel = groupModel;
        }
    }
}

