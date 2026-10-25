package com.fir.simulacro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

final class ExamShareHelper {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int BINS = 20;

    private ExamShareHelper() {}

    static void share(Activity activity, String comunidad, String year, String dateLabel,
                      int hits, int total, double netas, String timeLabel, ExamScoreStats stats) {
        try {
            Bitmap bitmap = render(activity, comunidad, year, dateLabel, hits, total, netas, timeLabel, stats);
            File dir = new File(activity.getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(activity, "No se pudo preparar el compartido", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(dir, "exam_share.png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TEXT, "Mi EXAMEN de " + comunidad + " " + year + " en Nursia");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Compartir resultado"));
        } catch (Exception e) {
            Toast.makeText(activity, "No se pudo compartir el resultado", Toast.LENGTH_SHORT).show();
        }
    }

    private static void drawFitted(Canvas canvas, Paint paint, String text, float x, float y, float maxWidth, float minSize) {
        float size = paint.getTextSize();
        while (paint.measureText(text) > maxWidth && size > minSize) {
            size -= 1f;
            paint.setTextSize(size);
        }
        canvas.drawText(text, x, y, paint);
    }

    private static Bitmap render(Context context, String comunidad, String year, String dateLabel,
                                 int hits, int total, double netas, String timeLabel, ExamScoreStats stats) {
        Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#8CD2C0"));

        final int ink = Color.parseColor("#26251e");
        final int body = Color.parseColor("#5a5852");

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setColor(Color.argb(40, 255, 255, 255));
        canvas.drawCircle(80, 120, 300, paint);
        canvas.drawCircle(WIDTH - 60, HEIGHT - 120, 360, paint);

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.app_icon);
        if (icon != null) {
            int size = 620;
            Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            iconPaint.setAlpha(120);
            canvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                    new Rect(WIDTH - size + 120, HEIGHT - size + 120, WIDTH + 120, HEIGHT + 120), iconPaint);
        }

        // Titulo
        String upperCom = comunidad == null ? "" : comunidad.toUpperCase(new Locale("es", "ES"));
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(1, 6, 8, Color.argb(90, 38, 37, 30));
        paint.setTextSize(112);
        canvas.drawText("EXAMEN", WIDTH / 2f, 220, paint);
        paint.setTextSize(112);
        drawFitted(canvas, paint, ("DE " + upperCom + " " + year).trim(), WIDTH / 2f, 340, WIDTH - 140, 40);
        paint.clearShadowLayer();

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setColor(ink);
        paint.setTextSize(52);
        canvas.drawText(dateLabel, WIDTH / 2f, 425, paint);

        // Resultado: acertadas, nota neta, tiempo
        RectF scoreCard = new RectF(80, 480, WIDTH - 80, 700);
        paint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawRoundRect(scoreCard, 40, 40, paint);

        float[] xs = {WIDTH * 0.20f, WIDTH * 0.50f, WIDTH * 0.80f};
        String[] values = {
                hits + "/" + total,
                String.format(Locale.US, "%.2f", netas),
                timeLabel
        };
        String[] labels = {"ACERTADAS", "NOTA NETA", "TIEMPO"};
        for (int i = 0; i < 3; i++) {
            paint.setColor(ink);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            paint.setTextSize(80);
            drawFitted(canvas, paint, values[i], xs[i], 625, 270, 36);

            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(34);
            paint.setColor(body);
            canvas.drawText(labels[i], xs[i], 550, paint);
        }

        // Histograma
        double[] scores = stats.scores;
        double min = netas;
        double max = netas;
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

        RectF card = new RectF(80, 770, WIDTH - 80, 1440);
        paint.setColor(Color.argb(170, 255, 255, 255));
        canvas.drawRoundRect(card, 40, 40, paint);

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setColor(body);
        paint.setTextSize(34);
        canvas.drawText("Así quedaron los " + scores.length + " presentados", WIDTH / 2f, 825, paint);

        float left = 130;
        float right = WIDTH - 130;
        float top = 920;
        float bottom = 1340;
        float binWidth = (right - left) / BINS;
        float gap = Math.min(4f, binWidth * 0.12f);

        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#4FA48D"));
        for (int i = 0; i < BINS; i++) {
            float barHeight = (bottom - top) * counts[i] / peak;
            RectF bar = new RectF(left + i * binWidth + gap / 2, bottom - barHeight,
                    left + (i + 1) * binWidth - gap / 2, bottom);
            canvas.drawRoundRect(bar, 5, 5, barPaint);
        }

        Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
        axis.setColor(Color.parseColor("#a09c92"));
        axis.setStrokeWidth(3);
        canvas.drawLine(left, bottom, right, bottom, axis);

        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(Color.parseColor("#807d72"));
        paint.setTextSize(32);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f", min), left + 24, bottom + 50, paint);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f", (min + max) / 2), (left + right) / 2, bottom + 50, paint);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f", max), right - 24, bottom + 50, paint);

        float x = left + (float) ((netas - min) / (max - min)) * (right - left);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(ink);
        line.setStrokeWidth(8);
        canvas.drawLine(x, top - 30, x, bottom, line);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        paint.setColor(ink);
        paint.setTextSize(42);
        paint.setTextAlign(Paint.Align.LEFT);
        String label = "Tú";
        float labelWidth = paint.measureText(label);
        float labelX = Math.max(left, Math.min(right - labelWidth, x - labelWidth / 2));
        canvas.drawText(label, labelX, top - 42, paint);
        paint.setTextAlign(Paint.Align.CENTER);

        // Posicion
        int rank = stats.rankOf(netas);
        int topPercent = stats.topPercent(netas);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        paint.setColor(ink);
        paint.setTextSize(64);
        drawFitted(canvas, paint,
                String.format(Locale.getDefault(), "Habrías quedado el %d de %d", rank, scores.length),
                WIDTH / 2f, 1540, WIDTH - 140, 36);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setColor(Color.parseColor("#3d3c34"));
        paint.setTextSize(50);
        canvas.drawText(String.format(Locale.getDefault(), "Estarías en el Top %d%%", topPercent), WIDTH / 2f, 1620, paint);

        return bmp;
    }
}
