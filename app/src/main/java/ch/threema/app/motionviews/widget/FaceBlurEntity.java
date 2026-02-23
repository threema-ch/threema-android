package ch.threema.app.motionviews.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.motionviews.FaceItem;
import ch.threema.app.motionviews.viewmodel.Layer;

public class FaceBlurEntity extends FaceEntity {

    public FaceBlurEntity(@NonNull Layer layer,
                          @NonNull FaceItem faceItem,
                          @IntRange(from = 1) int originalImageWidth,
                          @IntRange(from = 1) int originalImageHeight,
                          @IntRange(from = 1) int canvasWidth,
                          @IntRange(from = 1) int canvasHeight) {
        super(layer, faceItem, originalImageWidth, originalImageHeight, canvasWidth, canvasHeight);
    }

    @Override
    public void drawContent(@NonNull Canvas canvas, @Nullable Paint drawingPaint) {
        RenderScript rs = RenderScript.create(ThreemaApplication.getAppContext());
        Allocation input = Allocation.createFromBitmap(rs, faceItem.getBitmap());
        Allocation output = Allocation.createTyped(rs, input.getType());
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        blurScript.setRadius(25f);
        blurScript.setInput(input);
        blurScript.forEach(output);

        Paint paint = new Paint();
        paint.setDither(true);
        paint.setAntiAlias(true);

        Bitmap blurred = Bitmap.createBitmap(faceItem.getBitmap().getWidth(), faceItem.getBitmap().getHeight(), faceItem.getBitmap().getConfig());
        output.copyTo(blurred);

        Matrix newMatrix = new Matrix(matrix);
        newMatrix.preScale(faceItem.getPreScale(), faceItem.getPreScale());

        canvas.drawBitmap(blurred, newMatrix, paint);

        blurScript.destroy();
        input.destroy();
        output.destroy();
        rs.destroy();
        blurred.recycle();
    }

    @Override
    public int getWidth() {
        return Math.round(faceItem.getBitmap().getWidth() * faceItem.getPreScale());
    }

    @Override
    public int getHeight() {
        return Math.round(faceItem.getBitmap().getHeight() * faceItem.getPreScale());
    }
}
