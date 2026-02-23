package ch.threema.app.utils;

import android.content.ContentResolver;
import android.net.Uri;

public class BitmapWorkerTaskParams {
    public Uri imageUri;
    public int width;
    public int height;
    public ContentResolver contentResolver;
    public int orientation;
    public int flip;
    public int exifOrientation;
    public int exifFlip;
    public boolean mutable;
}
