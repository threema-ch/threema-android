package ch.threema.app.motionviews;

import android.graphics.Bitmap;
import android.media.FaceDetector;

public class FaceItem {
    private FaceDetector.Face face;
    private Bitmap bitmap;
    private float preScale;

    public FaceItem(FaceDetector.Face face, Bitmap bitmap, float preScale) {
        this.face = face;
        this.bitmap = bitmap;
        this.preScale = preScale;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public FaceDetector.Face getFace() {
        return face;
    }

    public float getPreScale() {
        return preScale;
    }
}
