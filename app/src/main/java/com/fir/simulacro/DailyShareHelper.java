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

final class DailyShareHelper {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;

    private DailyShareHelper() {}

    /** selected[i] = opcion marcada (-1 en blanco); correct[i] = opcion correcta; sources[i] = "Comunidad Año". */
    static void share(Activity activity, String dateLabel, int[] selected, int[] correct, String[] sources) {
        try {
            Bitmap bitmap = render(activity, dateLabel, selected, correct, sources);
            File dir = new File(activity.getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(activity, "No se pudo preparar el compartido", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(dir, "daily_share.png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TEXT, "Mi RETO DIARIO del " + dateLabel + " en Nursia");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Compartir resultado"));
        } catch (Exception e) {
            Toast.makeText(activity, "No se pudo compartir el resultado", Toast.LENGTH_SHORT).show();
        }
    }

    private static Bitmap render(Context context, String dateLabel, int[] selected, int[] correct, String[] sources) {
        int n = selected.length;
        int hits = 0;
        int wrong = 0;
        for (int i = 0; i < n; i++) {
            if (selected[i] < 0) {
                continue;
            }
            if (selected[i] == correct[i]) {
                hits++;
            } else {
                wrong++;
            }
        }
        double netas = n == 0 ? 0 : (hits - wrong / 3.0) * 10.0 / n;

        Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#8CD2C0"));

        final int ink = Color.parseColor("#26251e");
        final int green = Color.parseColor("#2E9E4F");
        final int red = Color.parseColor("#E04848");

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);

        // Franja decorativa clara arriba y circulos suaves de fondo
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

        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        paint.setColor(Color.WHITE);
        paint.setTextSize(112);
        paint.setShadowLayer(1, 6, 8, Color.argb(90, 38, 37, 30));
        canvas.drawText("RETO", WIDTH / 2f, 220, paint);
        canvas.drawText("DIARIO", WIDTH / 2f, 340, paint);
        paint.clearShadowLayer();

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setColor(ink);
        paint.setTextSize(52);
        canvas.drawText(dateLabel, WIDTH / 2f, 425, paint);

        // Resultado
        RectF scoreCard = new RectF(120, 480, WIDTH - 120, 700);
        paint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawRoundRect(scoreCard, 40, 40, paint);

        paint.setColor(ink);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        paint.setTextSize(88);
        canvas.drawText(hits + "/" + n, WIDTH * 0.32f, 625, paint);
        canvas.drawText(String.format(Locale.US, "%.2f", netas), WIDTH * 0.70f, 625, paint);

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(36);
        paint.setColor(Color.parseColor("#5a5852"));
        canvas.drawText("ACERTADAS", WIDTH * 0.32f, 550, paint);
        canvas.drawText("NOTA NETA", WIDTH * 0.70f, 550, paint);

        // Lista de preguntas
        float rowHeight = Math.min(110f, 1040f / Math.max(1, n));
        float square = rowHeight * 0.72f;
        float top = 770;
        float squareLeft = WIDTH / 2f - square / 2f;

        Paint squarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5);
        stroke.setColor(ink);

        for (int i = 0; i < n; i++) {
            float cy = top + rowHeight * i + rowHeight / 2f;
            RectF box = new RectF(squareLeft, cy - square / 2f, squareLeft + square, cy + square / 2f);
            if (selected[i] < 0) {
                squarePaint.setColor(Color.WHITE);
            } else if (selected[i] == correct[i]) {
                squarePaint.setColor(green);
            } else {
                squarePaint.setColor(red);
            }
            canvas.drawRoundRect(box, 16, 16, squarePaint);
            canvas.drawRoundRect(box, 16, 16, stroke);

            paint.setColor(ink);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            paint.setTextSize(Math.min(58f, rowHeight * 0.52f));
            paint.setTextAlign(Paint.Align.RIGHT);
            float baseline = cy + paint.getTextSize() * 0.35f;
            canvas.drawText(String.valueOf(i + 1), squareLeft - 40, baseline, paint);

            if (sources != null && i < sources.length && sources[i] != null) {
                float labelRight = squareLeft - 125;
                float maxWidth = labelRight - 40;
                paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                paint.setColor(Color.parseColor("#3d3c34"));
                float textSize = Math.min(32f, rowHeight * 0.34f);
                paint.setTextSize(textSize);
                while (paint.measureText(sources[i]) > maxWidth && textSize > 18f) {
                    textSize -= 1f;
                    paint.setTextSize(textSize);
                }
                canvas.drawText(sources[i], labelRight, cy + textSize * 0.35f, paint);
                paint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                paint.setColor(ink);
                paint.setTextSize(Math.min(58f, rowHeight * 0.52f));
            }

            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.valueOf((char) ('A' + correct[i])), squareLeft + square + 40, baseline, paint);
        }

        // Leyenda
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(32);
        paint.setColor(Color.parseColor("#3d3c34"));
        canvas.drawText("Nº de pregunta  ·  tu resultado  ·  opción correcta", WIDTH / 2f, top + rowHeight * n + 60, paint);

        return bmp;
    }
}
