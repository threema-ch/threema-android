package ch.threema.app.motionviews.widget;

import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.motionviews.FaceItem;
import ch.threema.app.motionviews.viewmodel.Layer;

public class FaceEmojiEntity extends FaceEntity {

    public FaceEmojiEntity(@NonNull Layer layer,
                           @NonNull FaceItem faceItem,
                           @IntRange(from = 1) int originalImageWidth,
                           @IntRange(from = 1) int originalImageHeight,
                           @IntRange(from = 1) int canvasWidth,
                           @IntRange(from = 1) int canvasHeight) {
        super(layer, faceItem, originalImageWidth, originalImageHeight, canvasWidth, canvasHeight);
    }

    @Override
    public void drawContent(@NonNull Canvas canvas, @Nullable Paint drawingPaint) {
        canvas.drawBitmap(bitmap, matrix, drawingPaint);
    }
}
