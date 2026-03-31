package ch.threema.app.emojis;

import android.util.SparseArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public class EmojiGroup {
    private final @Nullable String assetPathPrefix;
    private final @Nullable String assetPathSuffix;
    private final @DrawableRes int groupIcon;
    private final @StringRes int groupName;
    private final @NonNull SparseArray<EmojiSpritemapBitmap> spritemapBitmaps = new SparseArray<>();

    EmojiGroup(@Nullable String assetPathPrefix, @Nullable String assetPathSuffix,
               @DrawableRes int groupIcon, @StringRes int groupName) {
        this.assetPathPrefix = assetPathPrefix;
        this.assetPathSuffix = assetPathSuffix;
        this.groupIcon = groupIcon;
        this.groupName = groupName;
    }

    public @DrawableRes int getGroupIcon() {
        return groupIcon;
    }

    public @StringRes int getGroupName() {
        return groupName;
    }

    @Nullable
    public String getAssetPath(int spritemapId) {
        if (this.assetPathPrefix == null || this.assetPathSuffix == null) {
            return null;
        }
        return this.assetPathPrefix + spritemapId + this.assetPathSuffix;
    }

    @Nullable
    public EmojiSpritemapBitmap getSpritemapBitmap(int spritemapId) {
        return this.spritemapBitmaps.get(spritemapId);
    }

    public void setSpritemapBitmap(int spritemapId, @NonNull EmojiSpritemapBitmap spritemapBitmap) {
        this.spritemapBitmaps.put(spritemapId, spritemapBitmap);
    }

}
