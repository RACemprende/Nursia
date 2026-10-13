package com.fir.simulacro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Locale;

public class ProgressRingView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int done = 0;
    private int total = 0;

    public ProgressRingView(Context context) {
        this(context, null);
    }

    public ProgressRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        int primary = ContextCompat.getColor(context, R.color.primary);
        int ink = ContextCompat.getColor(context, R.color.ink);
        int muted = ContextCompat.getColor(context, R.color.muted);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(14 * density);
        trackPaint.setColor(Color.argb(70, Color.red(primary), Color.green(primary), Color.blue(primary)));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(14 * density);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(primary);

        percentPaint.setColor(ink);
        percentPaint.setTextAlign(Paint.Align.CENTER);
        percentPaint.setFakeBoldText(true);
        percentPaint.setTextSize(30 * scaledDensity);

        detailPaint.setColor(muted);
        detailPaint.setTextAlign(Paint.Align.CENTER);
        detailPaint.setTextSize(13 * scaledDensity);
    }

    public void setProgress(int done, int total) {
        this.done = Math.max(0, done);
        this.total = Math.max(0, total);
        setContentDescription(String.format(Locale.getDefault(),
                "Progreso: %d de %d preguntas, %s", this.done, this.total, percentText()));
        invalidate();
    }

    private double fraction() {
        if (total <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) done / total);
    }

    private String percentText() {
        return String.format(Locale.getDefault(), "%.1f%%", fraction() * 100.0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = arcPaint.getStrokeWidth();
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f + stroke / 2f;
        float top = (getHeight() - size) / 2f + stroke / 2f;
        oval.set(left, top, left + size - stroke, top + size - stroke);

        canvas.drawArc(oval, 0f, 360f, false, trackPaint);
        float sweep = (float) (360.0 * fraction());
        if (sweep > 0f) {
            canvas.drawArc(oval, -90f, sweep, false, arcPaint);
        }

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText(percentText(), cx, cy + percentPaint.getTextSize() * 0.15f, percentPaint);
        canvas.drawText(done + " / " + total, cx, cy + percentPaint.getTextSize() * 0.15f + detailPaint.getTextSize() * 1.5f, detailPaint);
    }
}
