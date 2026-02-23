package ch.threema.app.ui.listitemholder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import ch.threema.app.emojireactions.EmojiReactionGroup;
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
    public ImageView deliveredIndicator, datePrefixIcon, starredIcon;
    public ImageView attachmentImage;
    public MaterialCardView messageBlockView;
    public ViewGroup contentView;
    public AudioProgressBarView seekBar;
    public View quoteBar;
    public ImageView quoteThumbnail, quoteTypeImage;
    public TranscoderView transcoderView;
    public FrameLayout readOnContainer;
    public Chip readOnButton;
    public ImageView audioMessageIcon;
    public TextView tapToResend;
    public View footerView;
    public EmojiReactionGroup emojiReactionGroup;

    public ControllerView controller;

    // associated messageplayer
    public MessagePlayer messagePlayer;

    public TextView editedText;
    // content type of item represented by this holder (layout, decorator etc.)
    public int itemType;
}
