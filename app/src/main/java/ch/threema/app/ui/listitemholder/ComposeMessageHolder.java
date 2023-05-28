/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

package ch.threema.app.ui.listitemholder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.AudioProgressBarView;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.TranscoderView;

public class ComposeMessageHolder extends AvatarListItemHolder {
	public TextView bodyTextView;
	public TextView secondaryTextView;
	public TextView tertiaryTextView;
	public TextView size;
	public TextView senderName;
	public TextView dateView;
	public View senderView;
	public ImageView deliveredIndicator;
	public ImageView attachmentImage;
	public ViewGroup messageBlockView;
	public ViewGroup contentView;
	public AudioProgressBarView seekBar;
	public View quoteBar;
	public ImageView quoteThumbnail, quoteTypeImage;
	public TranscoderView transcoderView;
	public FrameLayout readOnContainer;
	public Chip readOnButton;
	public MaterialButton messageTypeButton;
	public View groupAckContainer;
	public ImageView groupAckThumbsUpImage, groupAckThumbsDownImage;
	public TextView groupAckThumbsUpCount, groupAckThumbsDownCount;
	public TextView tapToResend;
	public View footerView;

	public ControllerView controller;

	// associated messageplayer
	public MessagePlayer messagePlayer;
}
