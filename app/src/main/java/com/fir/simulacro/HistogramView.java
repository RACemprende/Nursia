package com.fir.simulacro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Locale;

public class HistogramView extends View {
    private static final int BINS = 20;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint userTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private double[] scores = new double[0];
    private double userScore;

    public HistogramView(Context context) {
        super(context);
        init();
    }

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        barPaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.ink));
        linePaint.setStrokeWidth(2.5f * density);
        axisPaint.setColor(ContextCompat.getColor(getContext(), R.color.muted_soft));
        axisPaint.setStrokeWidth(1f * density);
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.muted));
        textPaint.setTextSize(11f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        userTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.ink));
        userTextPaint.setTextSize(12f * density);
        userTextPaint.setFakeBoldText(true);
    }

    public void setData(double[] scores, double userScore) {
        this.scores = scores == null ? new double[0] : scores;
        this.userScore = userScore;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (190 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (scores.length == 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;

        double min = Math.min(userScore, scores[0]);
        double max = Math.max(userScore, scores[0]);
        for (double s : scores) {
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        min = Math.floor(min);
        max = Math.ceil(max);
        if (max <= min) {
            max = min + 1;
        }

        int[] counts = new int[BINS];
        for (double s : scores) {
            int bin = (int) ((s - min) / (max - min) * BINS);
            counts[Math.max(0, Math.min(BINS - 1, bin))]++;
        }
        int peak = 1;
        for (int c : counts) {
            peak = Math.max(peak, c);
        }

        float left = 8 * density;
        float right = getWidth() - 8 * density;
        float top = 22 * density;
        float bottom = getHeight() - 22 * density;
        float binWidth = (right - left) / BINS;
        float gap = Math.min(2 * density, binWidth * 0.12f);

        for (int i = 0; i < BINS; i++) {
            float barHeight = (bottom - top) * counts[i] / peak;
            RectF bar = new RectF(left + i * binWidth + gap / 2, bottom - barHeight,
                    left + (i + 1) * binWidth - gap / 2, bottom);
            canvas.drawRoundRect(bar, 2 * density, 2 * density, barPaint);
        }
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        canvas.drawText(format(min), left + 12 * density, getHeight() - 5 * density, textPaint);
        canvas.drawText(format((min + max) / 2), (left + right) / 2, getHeight() - 5 * density, textPaint);
        canvas.drawText(format(max), right - 12 * density, getHeight() - 5 * density, textPaint);

        float x = left + (float) ((userScore - min) / (max - min)) * (right - left);
        canvas.drawLine(x, top - 6 * density, x, bottom, linePaint);
        String label = "Tú";
        float labelWidth = userTextPaint.measureText(label);
        float labelX = Math.max(left, Math.min(right - labelWidth, x - labelWidth / 2));
        canvas.drawText(label, labelX, top - 10 * density, userTextPaint);
    }

    private static String format(double value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }
}
