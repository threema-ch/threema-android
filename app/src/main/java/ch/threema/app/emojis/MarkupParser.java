/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Stack;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class MarkupParser {
    private static final Logger logger = getThreemaLogger("MarkupParser");

    private static final String BOUNDARY_PATTERN = "[\\s.,!?¡¿‽⸮;:&(){}\\[\\]⟨⟩‹›«»'\"‘’“”*~\\-_…⋯᠁]";
    private static final String URL_BOUNDARY_PATTERN = "[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]";
    private static final String URL_START_PATTERN = "^[a-zA-Z]+://.*";

    public static final char MARKUP_CHAR_BOLD = '*';
    public static final char MARKUP_CHAR_ITALIC = '_';
    public static final char MARKUP_CHAR_STRIKETHROUGH = '~';
    public static final String MARKUP_CHAR_PATTERN = ".*[\\*_~].*";

    private final Pattern boundaryPattern, urlBoundaryPattern, urlStartPattern;

    // Singleton stuff
    private static MarkupParser sInstance = null;

    public static synchronized MarkupParser getInstance() {
        if (sInstance == null) {
            sInstance = new MarkupParser();
        }
        return sInstance;
    }

    private MarkupParser() {
        this.boundaryPattern = Pattern.compile(BOUNDARY_PATTERN);
        this.urlBoundaryPattern = Pattern.compile(URL_BOUNDARY_PATTERN);
        this.urlStartPattern = Pattern.compile(URL_START_PATTERN);
    }

    public enum TokenType {
        TEXT,
        NEWLINE,
        ASTERISK,
        UNDERSCORE,
        TILDE
    }

    public static class MarkupToken {
        TokenType kind;
        int start;
        int end;

        private MarkupToken(TokenType kind, int start, int end) {
            this.kind = kind;
            this.start = start;
            this.end = end;
        }

        @NonNull
        @Override
        public String toString() {
            return "MarkupToken(" + "kind = " + kind + ", start = " + start + ", end = " + end + ")";
        }
    }

    public static class SpanItem {
        public TokenType kind;
        public int textStart;
        public int textEnd;
        public int markerStart;
        public int markerEnd;

        SpanItem(TokenType kind, int textStart, int textEnd, int markerStart, int markerEnd) {
            this.kind = kind;
            this.textStart = textStart;
            this.textEnd = textEnd;
            this.markerStart = markerStart;
            this.markerEnd = markerEnd;
        }
    }

    // Booleans to avoid searching the stack.
    // This is used for optimization.
    public static class TokenPresenceMap extends HashMap<TokenType, Boolean> {
        TokenPresenceMap() {
            init();
        }

        public void init() {
            this.put(TokenType.ASTERISK, false);
            this.put(TokenType.UNDERSCORE, false);
            this.put(TokenType.TILDE, false);
        }
    }

    private HashMap<TokenType, Character> markupChars = new HashMap<>();

    {
        markupChars.put(TokenType.ASTERISK, MARKUP_CHAR_BOLD);
        markupChars.put(TokenType.UNDERSCORE, MARKUP_CHAR_ITALIC);
        markupChars.put(TokenType.TILDE, MARKUP_CHAR_STRIKETHROUGH);
    }

    /**
     * Return whether the specified token type is a markup token.
     */
    private boolean isMarkupToken(TokenType tokenType) {
        return markupChars.containsKey(tokenType);
    }

    /**
     * Return whether the character at the specified position in the string is a boundary character.
     * When `character` is out of range, the function will return true.
     */
    private boolean isBoundary(CharSequence text, int position) {
        if (position < 0 || position >= text.length()) {
            return true;
        }
        return boundaryPattern.matcher(TextUtils.substring(text, position, position + 1)).matches();
    }

    /**
     * Return whether the specified character is a URL boundary character.
     * When `character` is undefined, the function will return true.
     * <p>
     * Characters that may be in an URL according to RFC 3986:
     * ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%
     */
    private boolean isUrlBoundary(CharSequence text, int position) {
        if (position < 0 || position >= text.length()) {
            return true;
        }
        return !urlBoundaryPattern.matcher(TextUtils.substring(text, position, position + 1)).matches();
    }

    /**
     * Return whether the specified string starts an URL.
     */
    private boolean isUrlStart(CharSequence text, int position) {
        if (position < 0 || position >= text.length()) {
            return false;
        }
        return urlStartPattern.matcher(TextUtils.substring(text, position, text.length())).matches();
    }

    private int pushTextBufToken(int tokenLength, int i, ArrayList<MarkupToken> markupTokens) {
        if (tokenLength > 0) {
            markupTokens.add(new MarkupToken(TokenType.TEXT, i - tokenLength, i));
            tokenLength = 0;
        }
        return tokenLength;
    }

    /**
     * This function accepts a string and returns a list of tokens.
     */
    @NonNull
    public ArrayList<MarkupToken> tokenize(@NonNull CharSequence text) {
        int tokenLength = 0;
        boolean matchingUrl = false;
        ArrayList<MarkupToken> markupTokens = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);

            // Detect URLs
            if (!matchingUrl) {
                matchingUrl = isUrlStart(text, i);
            }

            // URLs have a limited set of boundary characters, therefore we need to
            // treat them separately.
            if (matchingUrl) {
                final boolean nextIsUrlBoundary = isUrlBoundary(text, i + 1);
                if (nextIsUrlBoundary) {
                    tokenLength = pushTextBufToken(tokenLength, i, markupTokens);
                    matchingUrl = false;
                }
                tokenLength++;
            } else {
                final boolean prevIsBoundary = isBoundary(text, i - 1);
                final boolean nextIsBoundary = isBoundary(text, i + 1);

                if (currentChar == MARKUP_CHAR_BOLD && (prevIsBoundary || nextIsBoundary)) {
                    tokenLength = pushTextBufToken(tokenLength, i, markupTokens);
                    markupTokens.add(new MarkupToken(TokenType.ASTERISK, i, i + 1));
                } else if (currentChar == MARKUP_CHAR_ITALIC && (prevIsBoundary || nextIsBoundary)) {
                    tokenLength = pushTextBufToken(tokenLength, i, markupTokens);
                    markupTokens.add(new MarkupToken(TokenType.UNDERSCORE, i, i + 1));
                } else if (currentChar == MARKUP_CHAR_STRIKETHROUGH && (prevIsBoundary || nextIsBoundary)) {
                    tokenLength = pushTextBufToken(tokenLength, i, markupTokens);
                    markupTokens.add(new MarkupToken(TokenType.TILDE, i, i + 1));
                } else if (currentChar == '\n') {
                    tokenLength = pushTextBufToken(tokenLength, i, markupTokens);
                    markupTokens.add(new MarkupToken(TokenType.NEWLINE, i, i + 1));
                } else {
                    tokenLength++;
                }
            }
        }

        pushTextBufToken(tokenLength - 1, text.length() - 1, markupTokens);

        return markupTokens;
    }

    private void applySpans(@NonNull SpannableStringBuilder stringBuilder, @NonNull Stack<SpanItem> spanStack) {
        ArrayList<Integer> deletables = new ArrayList<>();

        while (!spanStack.isEmpty()) {
            SpanItem span = spanStack.pop();
            if (span.textStart > span.textEnd) {
                logger.debug("range problem. ignore");
            } else {
                if (span.textStart > 0 && span.textEnd < stringBuilder.length()) {
                    if (span.textStart != span.textEnd) {
                        stringBuilder.setSpan(getCharacterStyle(span.kind), span.textStart, span.textEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        deletables.add(span.markerStart);
                        deletables.add(span.markerEnd);
                    }
                }
            }
        }

        if (!deletables.isEmpty()) {
            Collections.sort(deletables, Collections.reverseOrder());
            for (int deletable : deletables) {
                stringBuilder.delete(deletable, deletable + 1);
            }
        }
    }

    private void applySpans(Editable s, @ColorInt int markerColor, Stack<SpanItem> spanStack) {
        while (!spanStack.isEmpty()) {
            SpanItem span = spanStack.pop();
            if (span.textStart > span.textEnd) {
                logger.debug("range problem. ignore");
            } else {
                if (span.textStart > 0 && span.textEnd < s.length()) {
                    if (span.textStart != span.textEnd) {
                        s.setSpan(getCharacterStyle(span.kind), span.textStart, span.textEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                        s.setSpan(new ForegroundColorSpan(markerColor), span.markerStart, span.markerStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        s.setSpan(new ForegroundColorSpan(markerColor), span.markerEnd, span.markerEnd + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
    }

    // Helper: Pop the stack, throw an exception if it's empty
    private MarkupToken popStack(Stack<MarkupToken> stack) throws MarkupParserException {
        try {
            return stack.pop();
        } catch (EmptyStackException e) {
            throw new MarkupParserException("Stack is empty");
        }
    }

    private static CharacterStyle getCharacterStyle(TokenType tokenType) {
        switch (tokenType) {
            case UNDERSCORE:
                return new StyleSpan(Typeface.ITALIC);
            case ASTERISK:
                return new StyleSpan(Typeface.BOLD);
            case TILDE:
            default:
                return new StrikethroughSpan();
        }
    }

    private void parse(ArrayList<MarkupToken> markupTokens, SpannableStringBuilder builder, Editable editable, @ColorInt int markerColor) throws MarkupParserException {

        Stack<SpanItem> spanStack = buildSpanStack(markupTokens);

        // Concatenate processed tokens
        if (builder != null) {
            applySpans(builder, spanStack);
        } else {
            if (!spanStack.isEmpty()) {
                applySpans(editable, markerColor, spanStack);
            }
        }
    }

    @NonNull
    public Stack<SpanItem> buildSpanStack(@NonNull ArrayList<MarkupToken> markupTokens) throws MarkupParserException {

        // Process the tokens. Add them to a stack. When a token pair is complete
        // (e.g. the second asterisk is found), pop the stack until you find the
        // matching token and convert everything in between to formatted text.
        Stack<MarkupToken> stack = new Stack<>();
        Stack<SpanItem> spanStack = new Stack<>();
        TokenPresenceMap tokenPresenceMap = new TokenPresenceMap();

        for (MarkupToken markupToken : markupTokens) {
            switch (markupToken.kind) {
                // Keep text as-is
                case TEXT:
                    stack.push(markupToken);
                    break;

                // If a markup token is found, try to find a matching token.
                case ASTERISK:
                case UNDERSCORE:
                case TILDE:
                    // Optimization: Only search the stack if a token with this token type exists
                    if (tokenPresenceMap.get(markupToken.kind)) {
                        // Pop tokens from the stack. If a matching token was found, apply
                        // markup to the text parts in between those two tokens.
                        Stack<MarkupToken> textParts = new Stack<>();
                        while (true) {
                            MarkupToken stackTop = popStack(stack);
                            if (stackTop.kind == TokenType.TEXT) {
                                textParts.push(stackTop);
                            } else if (stackTop.kind == markupToken.kind) {
                                int start, end;
                                if (textParts.isEmpty()) {
                                    // no text in between two markups
                                    start = end = stackTop.end;
                                } else {
                                    start = textParts.get(textParts.size() - 1).start;
                                    end = textParts.get(0).end;
                                }
                                spanStack.push(new SpanItem(markupToken.kind, start, end, stackTop.start, markupToken.start));
                                stack.push(new MarkupToken(TokenType.TEXT, start, end));
                                tokenPresenceMap.put(markupToken.kind, false);
                                break;
                            } else if (isMarkupToken(stackTop.kind)) {
                                textParts.push(new MarkupToken(TokenType.TEXT, stackTop.start, stackTop.end));
                            } else {
                                throw new MarkupParserException("Unknown token on stack: " + markupToken.kind);
                            }
                            tokenPresenceMap.put(stackTop.kind, false);
                        }
                    } else {
                        stack.push(markupToken);
                        tokenPresenceMap.put(markupToken.kind, true);
                    }
                    break;

                // Don't apply formatting across newlines
                case NEWLINE:
                    tokenPresenceMap.init();
                    break;

                default:
                    throw new MarkupParserException("Invalid token kind: " + markupToken.kind);
            }
        }

        return spanStack;
    }

    /**
     * Add text markup to given SpannableStringBuilder
     *
     * @param builder
     */
    public void markify(SpannableStringBuilder builder) {
        try {
            parse(tokenize(builder), builder, null, 0);
        } catch (Exception e) {
            //
        }
    }

    /**
     * Add text markup to text in given editable.
     *
     * @param editable    Editable to be markified
     * @param markerColor Desired color of markup markers
     */
    public void markify(Editable editable, @ColorInt int markerColor) {
        try {
            parse(tokenize(editable), null, editable, markerColor);
        } catch (Exception e) {
            //
        }
    }

    public class MarkupParserException extends Exception {
        MarkupParserException(String e) {
            super(e);
        }
    }

}
