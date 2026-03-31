package ch.threema.app.adapters.decorators;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiConversationTextView;
import ch.threema.app.messagedetails.MessageDetailsActivity;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupMessageModel;

public class TextChatAdapterDecorator extends ChatAdapterDecorator {

    private final int quoteType;

    public TextChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
        this.quoteType = QuoteUtil.getQuoteType(messageModel);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void configureChatMessage(@NonNull final ComposeMessageHolder holder, @NonNull final Context context, final int position) {
        if (holder.bodyTextView != null) {
            holder.bodyTextView.setMovementMethod(LinkMovementMethod.getInstance());
            String messageText = this.getMessageModel().getBody();

            this.setOnClickListener(view -> {
                // no action on onClick
            }, holder.messageBlockView);

            if (quoteType != QuoteUtil.QUOTE_TYPE_NONE) {
                QuoteUtil.QuoteContent quoteContent = configureQuote(holder, context, this.getMessageModel());
                if (quoteContent != null) {
                    messageText = quoteContent.bodyText;
                }
            } else {
                holder.bodyTextView.setText(formatTextString(context, messageText, this.filterString, helper.getMaxBubbleTextLength() + 8));
            }

            boolean isTruncated = false;

            if (holder.readOnContainer != null) {
                if (messageText != null && messageText.length() > helper.getMaxBubbleTextLength()) {
                    holder.readOnContainer.setVisibility(View.VISIBLE);
                    if (quoteType != QuoteUtil.QUOTE_TYPE_NONE) {
                        holder.readOnContainer.setBackgroundResource(
                            this.getMessageModel().isOutbox() ?
                                R.drawable.bubble_fade_send_selector :
                                R.drawable.bubble_fade_recv_selector);
                    }
                    holder.readOnButton.setOnClickListener(view ->
                        view.getContext().startActivity(MessageDetailsActivity.createIntent(view.getContext(), getMessageModel()))
                    );
                    isTruncated = true;
                } else {
                    holder.readOnContainer.setVisibility(View.GONE);
                    holder.readOnButton.setOnClickListener(null);
                }
            }
            // remove movement method. Otherwise clicks on the text are not handled correctly
            holder.bodyTextView.setMovementMethod(null);
            LinkifyUtil.getInstance().linkify(
                holder.bodyTextView,
                getMessageModel(),
                /* includePhoneNumbers = */ true,
                /* unhandledClickHandler = */ this,
                /* linkifyListener = */ linkifyListener
            );

            // remove any clickable link span at the end of truncated text as the link may not be complete
            if (isTruncated && holder.bodyTextView.getText() instanceof SpannableString) {
                SpannableString buffer = (SpannableString) holder.bodyTextView.getText();
                if (buffer != null) {
                    int lastCharOffset = buffer.length() - 1;
                    ClickableSpan[] link = buffer.getSpans(lastCharOffset, lastCharOffset, ClickableSpan.class);
                    if (link.length > 0) {
                        // we found a clickable span at the end of the truncated text, remove it
                        buffer.removeSpan(link[link.length - 1]);
                    }
                }
            }
        }

        RuntimeUtil.runOnUiThread(() -> setupResendStatus(holder));
    }

    @Nullable
    private QuoteUtil.QuoteContent configureQuote(final ComposeMessageHolder holder, Context context, final AbstractMessageModel messageModel) {
        final @Nullable QuoteUtil.QuoteContent content = QuoteUtil.getQuoteContent(
            messageModel,
            this.helper.getMessageReceiver(),
            false,
            this.helper.getThumbnailCache(),
            context,
            this.helper.getMessageService(),
            this.helper.getUserService(),
            this.helper.getFileService(),
            this.helper.getPreferenceService().getContactNameFormat()
        );

        if (content == null) {
            return null;
        }

        if (holder.secondaryTextView instanceof EmojiConversationTextView) {
            ((EmojiConversationTextView) holder.secondaryTextView).setFade(
                TestUtil.isEmptyOrNull(filterString) && content.quotedText.length() > helper.getMaxQuoteTextLength()
            );
            holder.secondaryTextView.setText(
                formatTextString(context, content.quotedText, this.filterString, helper.getMaxQuoteTextLength() + 8),
                TextView.BufferType.SPANNABLE
            );
        }

        final @Nullable ContactModel contactModel = this.helper.getContactService().getByIdentity(content.identity);
        if (contactModel != null) {
            if (holder.tertiaryTextView != null) {
                holder.tertiaryTextView.setText(
                    NameUtil.getQuoteName(
                        contactModel,
                        this.getUserService(),
                        helper.getPreferenceService().getContactNameFormat()
                    )
                );
                holder.tertiaryTextView.setVisibility(View.VISIBLE);
            }

            if (holder.quoteBar != null) {
                // Use the default quote bar color-state-list and maybe replace it with an identity specific static color
                @NonNull ColorStateList barColor = context.getColorStateList(R.color.bubble_quote_bar_default_colorstatelist);
                if (!helper.getMyIdentity().equals(content.identity)) {
                    if (getMessageModel() instanceof GroupMessageModel) {
                        if (this.identityColors != null && this.identityColors.containsKey(content.identity)) {
                            final @Nullable @ColorInt Integer identityColor = this.identityColors.get(content.identity);
                            if (identityColor != null) {
                                barColor = ColorStateList.valueOf(identityColor);
                            }
                        }
                    } else {
                        barColor = ColorStateList.valueOf(contactModel.getIdColor().getThemedColor(context));
                    }
                }
                holder.quoteBar.setBackgroundTintList(barColor);
                holder.quoteBar.setVisibility(View.VISIBLE);
            }
        } else {
            if (holder.tertiaryTextView != null) {
                holder.tertiaryTextView.setVisibility(View.GONE);
            }
            holder.quoteBar.setVisibility(View.GONE);
        }

        holder.bodyTextView.setText(formatTextString(context, content.bodyText, this.filterString, helper.getMaxBubbleTextLength() + 8));
        holder.bodyTextView.setVisibility(View.VISIBLE);

        if (holder.quoteThumbnail != null) {
            if (content.thumbnail != null) {
                holder.quoteThumbnail.setImageBitmap(content.thumbnail);
                holder.quoteThumbnail.setVisibility(View.VISIBLE);
            } else {
                holder.quoteThumbnail.setVisibility(View.GONE);
            }
        }

        if (holder.quoteTypeImage != null) {
            if (content.icon != null) {
                holder.quoteTypeImage.setImageResource(content.icon);
                holder.quoteTypeImage.setVisibility(View.VISIBLE);
            } else {
                holder.quoteTypeImage.setVisibility(View.GONE);
            }
        }
        return content;
    }
}
