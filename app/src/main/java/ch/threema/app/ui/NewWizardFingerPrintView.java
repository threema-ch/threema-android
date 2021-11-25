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

package ch.threema.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import ch.threema.app.R;

public class NewWizardFingerPrintView extends SquareImageView implements View.OnTouchListener {
	public interface OnSwipeResult {
		void newBytes(byte[] bytes, int step, int maxStep);
	}

	static int CHAR_LENGTH = 16;
	private final String backgroundChars = "0123456789ABCDEF";
	private int maximalSteps = 0;
	private int currentStep = 0;

	private final int backgroundCharsCount = this.backgroundChars.length();
	private float positionCorrection;
	final Paint backgroundCharPaint = new Paint();
	final Paint backgroundCharPaintFixed = new Paint();
	private int backgroundCharSpace;

	private OnSwipeResult swipeByteListener;
	private Integer pointLeakCount = 0;
	private Integer pointLeak = 5;
    private byte[] lastDigest;
	private LockableScrollView lockableScrollViewParent;
	Random randomGenerator = new Random();
	private int fixedCharCount;
	private int charsToFixPerStep;

	private class Char {
		public boolean isFixed = false;
		public char text;
		public int[] position = new int[2];
	}


	private final List<Char> currentChars = new ArrayList<>();

	public NewWizardFingerPrintView(Context context) {
		super(context);
		this.initView();
	}

	private void initView() {
		this.setFocusable(true);
		this.setFocusableInTouchMode(true);
		this.setOnTouchListener(this);
		this.backgroundCharPaint.setColor(Color.WHITE);
		this.backgroundCharPaint.setAntiAlias(true);
		this.backgroundCharPaint.setTextAlign(Paint.Align.CENTER);
		this.backgroundCharPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

		this.backgroundCharPaintFixed.setColor(this.getResources().getColor(R.color.wizard_color_accent));
		this.backgroundCharPaintFixed.setAntiAlias(true);
		this.backgroundCharPaintFixed.setTextAlign(Paint.Align.CENTER);
		this.backgroundCharPaintFixed.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
		this.reset();
	}

	public void reset() {
		this.currentStep = 0;
		this.fixedCharCount = 0;
		this.resetChars(true);
		this.invalidate();
	}

	@Override
	protected void onWindowVisibilityChanged(int visibility) {

		ViewParent p = this.getParent();

		while (p != null) {
			if (p instanceof LockableScrollView) {
				this.lockableScrollViewParent = (LockableScrollView) p;
				break;
			}

			p = p.getParent();
		}

		super.onWindowVisibilityChanged(visibility);
	}

	public NewWizardFingerPrintView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.initView();

	}

	public NewWizardFingerPrintView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.initView();
	}

	public void setOnSwipeByte(OnSwipeResult swipeByteListener, int maximalSteps) {
		this.swipeByteListener = swipeByteListener;
		this.maximalSteps = maximalSteps;
		this.charsToFixPerStep = Math.max((int)Math.ceil(this.currentChars.size() / this.maximalSteps), 1);
	}

	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {

		if (!this.isEnabled()) {
			return false;
		}

		if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
			if (this.lockableScrollViewParent != null) {
				this.lockableScrollViewParent.setScrollingEnabled(false);
			}
		} else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
			if (this.lockableScrollViewParent != null) {
				this.lockableScrollViewParent.setScrollingEnabled(true);
			}
		}

		if (this.pointLeakCount++ >= this.pointLeak) {
			PointF currentPoint = new PointF(
					motionEvent.getX(),
					motionEvent.getY()
			);

			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

				// add last digest
				if (lastDigest != null)
					messageDigest.update(lastDigest);

				// add position and timestamp of this touch
				String positionTimestamp = currentPoint.x + "-" + currentPoint.y + "-" + new Date().getTime();
				messageDigest.update(positionTimestamp.getBytes());

				lastDigest = messageDigest.digest();

				if(this.fixedCharCount < this.currentChars.size()) {
					//get next FIXED chars
					int fixCharCount = this.charsToFixPerStep;
					do {
						int index = randomGenerator.nextInt(this.currentChars.size());
						Char fChar = this.currentChars.get(index);
						if (!fChar.isFixed) {
							fChar.isFixed = true;
							this.fixedCharCount++;
							fixCharCount--;
						}

					} while (fixCharCount > 0 && this.fixedCharCount < this.currentChars.size());
				}


				if (this.swipeByteListener != null) {
					this.swipeByteListener.newBytes(lastDigest, this.currentStep, this.maximalSteps);
				}

				this.currentStep++;
			} catch (NoSuchAlgorithmException e) {
				return false;
			}

			this.resetChars(false);
			this.invalidate();
			this.pointLeakCount = 0;
		}

		this.pointLeakCount++;


		return true;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		for (Char chr : this.currentChars) {
			Paint paint = chr.isFixed ? this.backgroundCharPaintFixed : this.backgroundCharPaint;

			int x = (int)(positionCorrection + (chr.position[0] * this.backgroundCharSpace));
			int y = (int)(positionCorrection + (chr.position[1] * this.backgroundCharSpace));

			canvas.drawText(String.valueOf(chr.text),
					x + (this.backgroundCharSpace/2),
					(int) ((y + (this.backgroundCharSpace/2)) - ((paint.descent() + paint.ascent()) / 2)),
					paint);
		}
	}

	private void resetChars(boolean initState) {
		if(initState) {
			this.currentChars.clear();
		}

		//regenerate chars
		int listIndex = 0;
		for(int x = 0; x < CHAR_LENGTH; x++) {
			for(int y = 0; y < CHAR_LENGTH; y++) {
				final Char c;
				if(this.currentChars.size() < listIndex+1) {
					c = new Char();
					//set position!
					c.position[0] = x;
					c.position[1] = y;

					this.currentChars.add(listIndex, c);
				}
				else {
					c = this.currentChars.get(listIndex);
				}

				if(!c.isFixed) {
					if (!initState) {
						c.text = this.backgroundChars.charAt(randomGenerator.nextInt(this.backgroundCharsCount - 1));
					} else {
						c.text = this.backgroundChars.charAt(listIndex % CHAR_LENGTH);
					}
				}

				listIndex++;
			}
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		this.backgroundCharSpace = this.getWidth()/ CHAR_LENGTH;
		this.positionCorrection = ((this.getWidth()-(this.backgroundCharSpace*CHAR_LENGTH))/2);

		this.backgroundCharPaint.setTextSize(this.determineMaxTextSize("X", this.backgroundCharSpace) / 2);
		this.backgroundCharPaintFixed.setTextSize(this.backgroundCharPaint.getTextSize());

		super.onSizeChanged(w, h, oldw, oldh);
	}

	private int determineMaxTextSize(String str, float maxWidth) {
		int size = 0;
		Paint paint = new Paint();

		do {
			paint.setTextSize(++size);
		}
		while (paint.measureText(str) < maxWidth);

		return size;
	}
}
