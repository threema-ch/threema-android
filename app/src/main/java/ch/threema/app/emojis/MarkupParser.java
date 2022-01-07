/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Stack;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;

public class MarkupParser {
	private static final Logger logger = LoggerFactory.getLogger(MarkupParser.class);

	private static final String BOUNDARY_PATTERN = "[\\s.,!?¡¿‽⸮;:&(){}\\[\\]⟨⟩‹›«»'\"‘’“”*~\\-_…⋯᠁]";
	private static final String URL_BOUNDARY_PATTERN = "[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]";
	private static final String URL_START_PATTERN = "^[a-zA-Z]+://.*";

	private static final char MARKUP_CHAR_BOLD = '*';
	private static final char MARKUP_CHAR_ITALIC = '_';
	private static final char MARKUP_CHAR_STRIKETHRU = '~';
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

	private enum TokenType {
		TEXT,
		NEWLINE,
		ASTERISK,
		UNDERSCORE,
		TILDE
	}

	private class Token {
		TokenType kind;
		int start;
		int end;

		private Token(TokenType kind, int start, int end) {
			this.kind = kind;
			this.start = start;
			this.end = end;
		}
	}

	private static class SpanItem {
		TokenType kind;
		int textStart;
		int textEnd;
		int markerStart;
		int markerEnd;

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
		markupChars.put(TokenType.TILDE, MARKUP_CHAR_STRIKETHRU);
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
	 *
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

	private int pushTextBufToken(int tokenLength, int i, ArrayList<Token> tokens) {
		if (tokenLength > 0) {
			tokens.add(new Token(TokenType.TEXT, i - tokenLength, i));
			tokenLength = 0;
		}
		return tokenLength;
	}

	/**
	 * This function accepts a string and returns a list of tokens.
	 */
	private ArrayList<Token> tokenize(CharSequence text) {
		int tokenLength = 0;
		boolean matchingUrl = false;
		ArrayList<Token> tokens = new ArrayList<>();

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
					tokenLength = pushTextBufToken(tokenLength, i, tokens);
					matchingUrl = false;
				}
				tokenLength++;
			} else {
				final boolean prevIsBoundary = isBoundary(text, i - 1);
				final boolean nextIsBoundary = isBoundary(text, i + 1);

				if (currentChar == MARKUP_CHAR_BOLD && (prevIsBoundary || nextIsBoundary)) {
					tokenLength = pushTextBufToken(tokenLength, i, tokens);
					tokens.add(new Token(TokenType.ASTERISK, i, i + 1));
				} else if (currentChar == MARKUP_CHAR_ITALIC && (prevIsBoundary || nextIsBoundary)) {
					tokenLength = pushTextBufToken(tokenLength, i, tokens);
					tokens.add(new Token(TokenType.UNDERSCORE, i, i + 1));
				} else if (currentChar == MARKUP_CHAR_STRIKETHRU && (prevIsBoundary || nextIsBoundary)) {
					tokenLength = pushTextBufToken(tokenLength, i, tokens);
					tokens.add(new Token(TokenType.TILDE, i, i + 1));
				} else if (currentChar == '\n') {
					tokenLength = pushTextBufToken(tokenLength, i, tokens);
					tokens.add(new Token(TokenType.NEWLINE, i, i + 1));
				} else {
					tokenLength++;
				}
			}
		}

		pushTextBufToken(tokenLength - 1, text.length() - 1, tokens);

		return tokens;
	}

	private void applySpans(SpannableStringBuilder s, Stack<SpanItem> spanStack) {
		ArrayList<Integer> deletables = new ArrayList<>();

		while(!spanStack.isEmpty()) {
			SpanItem span = spanStack.pop();
			if (span.textStart > span.textEnd) {
				logger.debug("range problem. ignore");
			} else {
				if (span.textStart > 0 && span.textEnd < s.length()) {
					if (span.textStart != span.textEnd) {
						s.setSpan(getCharacterStyle(span.kind), span.textStart, span.textEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						deletables.add(span.markerStart);
						deletables.add(span.markerEnd);
					}
				}
			}
		}

		if (deletables.size() > 0) {
			Collections.sort(deletables, Collections.reverseOrder());
			for (int deletable : deletables) {
				s.delete(deletable, deletable + 1);
			}
		}
	}

	private void applySpans(Editable s, @ColorInt int markerColor, Stack<SpanItem> spanStack) {
		while(!spanStack.isEmpty()) {
			SpanItem span = spanStack.pop();
			if (span.textStart > span.textEnd) {
				logger.debug("range problem. ignore");
			} else {
				if (span.textStart > 0 && span.textEnd < s.length()) {
					if (span.textStart != span.textEnd) {
						s.setSpan(getCharacterStyle(span.kind), span.textStart, span.textEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						s.setSpan(new ForegroundColorSpan(markerColor), span.markerStart, span.markerStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						s.setSpan(new ForegroundColorSpan(markerColor), span.markerEnd, span.markerEnd + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					}
				}
			}
		}
	}

	// Helper: Pop the stack, throw an exception if it's empty
	private Token popStack(Stack<Token> stack) throws MarkupParserException {
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

	private void parse(ArrayList<Token> tokens, SpannableStringBuilder builder, Editable editable, @ColorInt int markerColor) throws MarkupParserException {
		// Process the tokens. Add them to a stack. When a token pair is complete
		// (e.g. the second asterisk is found), pop the stack until you find the
		// matching token and convert everything in between to formatted text.
		Stack<Token> stack = new Stack<>();
		Stack<SpanItem> spanStack = new Stack<>();
		TokenPresenceMap tokenPresenceMap = new TokenPresenceMap();

		for (Token token : tokens) {
			switch (token.kind) {
				// Keep text as-is
				case TEXT:
					stack.push(token);
					break;

				// If a markup token is found, try to find a matching token.
				case ASTERISK:
				case UNDERSCORE:
				case TILDE:
					// Optimization: Only search the stack if a token with this token type exists
					if (tokenPresenceMap.get(token.kind)) {
						// Pop tokens from the stack. If a matching token was found, apply
						// markup to the text parts in between those two tokens.
						Stack<Token> textParts = new Stack<>();
						while (true) {
							Token stackTop = popStack(stack);
							if (stackTop.kind == TokenType.TEXT) {
								textParts.push(stackTop);
							} else if (stackTop.kind == token.kind) {
								int start, end;
								if (textParts.size() == 0) {
									// no text in between two markups
									start = end = stackTop.end;
								} else {
									start = textParts.get(textParts.size() - 1).start;
									end = textParts.get(0).end;
								}
								spanStack.push(new SpanItem(token.kind, start, end, stackTop.start, token.start));
								stack.push(new Token(TokenType.TEXT, start, end));
								tokenPresenceMap.put(token.kind, false);
								break;
							} else if (isMarkupToken(stackTop.kind)) {
								textParts.push(new Token(TokenType.TEXT, stackTop.start, stackTop.end));
							} else {
								throw new MarkupParserException("Unknown token on stack: " + token.kind);
							}
							tokenPresenceMap.put(stackTop.kind, false);
						}
					} else {
						stack.push(token);
						tokenPresenceMap.put(token.kind, true);
					}
					break;

				// Don't apply formatting across newlines
				case NEWLINE:
					tokenPresenceMap.init();
					break;

				default:
					throw new MarkupParserException("Invalid token kind: " + token.kind);
			}
		}

		// Concatenate processed tokens
		if (builder != null) {
			applySpans(builder, spanStack);
		} else {
			if (spanStack.size() > 0) {
				applySpans(editable, markerColor, spanStack);
			}
		}
	}

	/**
	 * Add text markup to given SpannableStringBuilder
	 * @param builder
	 */
	public void markify(SpannableStringBuilder builder) {
		try {
			parse(tokenize(builder), builder, null, 0);
		} catch (MarkupParserException e) {
			//
		}
	}

	/**
	 * Add text markup to text in given editable.
	 * @param editable Editable to be markified
	 * @param markerColor Desired color of markup markers
	 */
	public void markify(Editable editable, @ColorInt int markerColor) {
		try {
			parse(tokenize(editable), null, editable, markerColor);
		} catch (MarkupParserException e) {
			//
		}
	}

	public class MarkupParserException extends Exception {
		MarkupParserException(String e) {
			super(e);
		}
	}

}
