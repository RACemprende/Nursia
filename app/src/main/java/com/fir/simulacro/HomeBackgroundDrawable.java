package com.fir.simulacro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

final class HomeBackgroundDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap icon;
    private final Rect src = new Rect();
    private final Rect dst = new Rect();

    HomeBackgroundDrawable(Context context) {
        fill.setColor(ContextCompat.getColor(context, R.color.primary));
        circle.setColor(android.graphics.Color.argb(40, 255, 255, 255));
        iconPaint.setAlpha(120);
        icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.app_icon);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        canvas.drawRect(b, fill);

        float unit = b.width() / 1080f;
        canvas.drawCircle(b.right - 80 * unit, b.top + 160 * unit, 320 * unit, circle);
        canvas.drawCircle(b.left + 60 * unit, b.bottom - 120 * unit, 360 * unit, circle);

        if (icon != null) {
            int size = Math.round(Math.max(620 * unit, 0));
            int offset = Math.round(120 * unit);
            src.set(0, 0, icon.getWidth(), icon.getHeight());
            dst.set(b.left - offset, b.bottom - size + offset, b.left + size - offset, b.bottom + offset);
            canvas.drawBitmap(icon, src, dst, iconPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
