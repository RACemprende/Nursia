package com.fir.simulacro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Locale;

public class ExamPaceChartView extends View {
    private static final int ACCURACY_COLOR = Color.parseColor("#2E9E4F");
    private static final int TIME_COLOR = Color.parseColor("#E0873A");
    private static final int GRID_LINES = 4;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private double[] xMinutes = new double[0];
    private double[] accuracyPct = new double[0];
    private double[] avgSeconds = new double[0];

    public ExamPaceChartView(Context context) {
        super(context);
        init();
    }

    public ExamPaceChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.muted_soft));
        gridPaint.setStrokeWidth(1f * density);
        textPaint.setTextSize(11f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(double[] xMinutes, double[] accuracyPct, double[] avgSeconds) {
        this.xMinutes = xMinutes == null ? new double[0] : xMinutes;
        this.accuracyPct = accuracyPct == null ? new double[0] : accuracyPct;
        this.avgSeconds = avgSeconds == null ? new double[0] : avgSeconds;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (260 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = xMinutes.length;
        if (n == 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;

        float left = 46 * density;
        float right = getWidth() - 46 * density;
        float top = 36 * density;
        float bottom = getHeight() - 40 * density;

        double xMax = Math.max(xMinutes[n - 1], 0.5);
        double maxAvg = 0;
        for (double v : avgSeconds) {
            maxAvg = Math.max(maxAvg, v);
        }
        double timeMax = niceCeil(maxAvg);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= GRID_LINES; i++) {
            float frac = (float) i / GRID_LINES;
            float y = bottom - (bottom - top) * frac;
            canvas.drawLine(left, y, right, y, gridPaint);
            textPaint.setColor(ACCURACY_COLOR);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.getDefault(), "%d%%", Math.round(100 * frac)),
                    left - 6 * density, y + 4 * density, textPaint);
            textPaint.setColor(TIME_COLOR);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.format(Locale.getDefault(), "%.0f s", timeMax * frac),
                    right + 6 * density, y + 4 * density, textPaint);
        }

        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.muted));
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i <= GRID_LINES; i++) {
            double minutes = xMax * i / GRID_LINES;
            float x = left + (right - left) * i / GRID_LINES;
            String label = xMax >= 10
                    ? String.format(Locale.getDefault(), "%.0f", minutes)
                    : String.format(Locale.getDefault(), "%.1f", minutes);
            canvas.drawText(label, x, bottom + 16 * density, textPaint);
        }
        canvas.drawText("Tiempo (min)", (left + right) / 2, bottom + 33 * density, textPaint);

        drawSeries(canvas, accuracyPct, 100.0, ACCURACY_COLOR, xMax, left, right, top, bottom, density);
        drawSeries(canvas, avgSeconds, timeMax, TIME_COLOR, xMax, left, right, top, bottom, density);

        dotPaint.setColor(ACCURACY_COLOR);
        canvas.drawCircle(left, 12 * density, 5 * density, dotPaint);
        textPaint.setColor(ACCURACY_COLOR);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setFakeBoldText(true);
        canvas.drawText("% aciertos", left + 10 * density, 16 * density, textPaint);
        dotPaint.setColor(TIME_COLOR);
        canvas.drawCircle(right - 112 * density, 12 * density, 5 * density, dotPaint);
        textPaint.setColor(TIME_COLOR);
        canvas.drawText("Tiempo medio", right - 102 * density, 16 * density, textPaint);
        textPaint.setFakeBoldText(false);
    }

    private void drawSeries(Canvas canvas, double[] values, double yMax, int color, double xMax,
                            float left, float right, float top, float bottom, float density) {
        int n = Math.min(values.length, xMinutes.length);
        linePaint.setColor(color);
        dotPaint.setColor(color);
        Path path = new Path();
        for (int i = 0; i < n; i++) {
            float x = left + (float) (xMinutes[i] / xMax) * (right - left);
            float y = bottom - (float) (Math.min(values[i], yMax) / yMax) * (bottom - top);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, linePaint);
        for (int i = 0; i < n; i++) {
            float x = left + (float) (xMinutes[i] / xMax) * (right - left);
            float y = bottom - (float) (Math.min(values[i], yMax) / yMax) * (bottom - top);
            canvas.drawCircle(x, y, 4 * density, dotPaint);
        }
    }

    private static double niceCeil(double value) {
        double[] steps = {5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300};
        double target = value * 1.1;
        for (double s : steps) {
            if (s >= target) {
                return s;
            }
        }
        return Math.ceil(target / 60.0) * 60.0;
    }
}
