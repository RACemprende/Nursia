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
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

final class BadgeShareHelper {

    private BadgeShareHelper() {}

    static void share(Activity activity, AppDatabaseHelper.Badge badge) {
        try {
            Bitmap shareBitmap = renderBadgeShareBitmap(activity, badge);
            File shareDir = new File(activity.getCacheDir(), "shared");
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                Toast.makeText(activity, "No se pudo preparar el compartido", Toast.LENGTH_SHORT).show();
                return;
            }
            File shareFile = new File(shareDir, "badge_share.png");
            try (FileOutputStream out = new FileOutputStream(shareFile)) {
                shareBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri shareUri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".fileprovider", shareFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "¡He desbloqueado la insignia \"" + badge.nombre + "\" en Nursia! 🎉");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(shareIntent, "Compartir insignia"));
        } catch (Exception e) {
            Toast.makeText(activity, "No se pudo compartir la insignia", Toast.LENGTH_SHORT).show();
        }
    }

    private static Bitmap renderBadgeShareBitmap(Context context, AppDatabaseHelper.Badge badge) {
        int width = 1080;
        int height = 1920;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#FFF8E7"));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setColor(Color.parseColor("#B8860B"));
        paint.setTextSize(72);
        paint.setFakeBoldText(true);
        canvas.drawText("¡Nueva insignia!", width / 2f, 280, paint);

        int resId = 0;
        if (badge.imagenDrawable != null) {
            resId = context.getResources().getIdentifier(badge.imagenDrawable, "drawable", context.getPackageName());
        }
        if (resId == 0) {
            resId = R.drawable.badge_locked_default;
        }
        Bitmap badgeBmp = BitmapFactory.decodeResource(context.getResources(), resId);
        if (badgeBmp != null) {
            int badgeSize = 720;
            int badgeLeft = (width - badgeSize) / 2;
            int badgeTop = 380;
            Rect src = new Rect(0, 0, badgeBmp.getWidth(), badgeBmp.getHeight());
            Rect dst = new Rect(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize);
            canvas.drawBitmap(badgeBmp, src, dst, null);
        }

        paint.setColor(Color.parseColor("#333333"));
        paint.setTextSize(96);
        paint.setFakeBoldText(true);
        canvas.drawText(badge.nombre != null ? badge.nombre : "", width / 2f, 1290, paint);

        paint.setColor(Color.parseColor("#5A5A5A"));
        paint.setTextSize(52);
        paint.setFakeBoldText(false);
        drawWrappedText(canvas, badge.descripcion, width / 2f, 1400, width - 160, paint, 68);

        paint.setColor(Color.parseColor("#B8860B"));
        paint.setTextSize(64);
        paint.setFakeBoldText(true);
        canvas.drawText("Nursia", width / 2f, height - 140, paint);

        return bmp;
    }

    private static void drawWrappedText(Canvas canvas, String text, float centerX, float startY,
                                        int maxWidth, Paint paint, float lineHeight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        float y = startY;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), centerX, y, paint);
                y += lineHeight;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), centerX, y, paint);
        }
    }
}
