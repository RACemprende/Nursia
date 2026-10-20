package com.fir.simulacro;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExamReserves {
    private static final String ASSET = "examenes_reservas.csv";
    private static Map<String, Integer> cache;

    private ExamReserves() {
    }

    /** Numero de preguntas de reserva (las ultimas del examen); 0 si no consta. */
    public static synchronized int get(Context context, String comunidad, String year) {
        if (cache == null) {
            cache = load(context);
        }
        Integer value = cache.get(key(comunidad, year));
        return value == null ? 0 : value;
    }

    private static Map<String, Integer> load(Context context) {
        Map<String, Integer> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replace("﻿", "");
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> cols = CsvUtils.parseCsvLine(line);
                if (cols.size() < 3) {
                    continue;
                }
                try {
                    int reserves = Integer.parseInt(cols.get(2).trim());
                    Integer.parseInt(cols.get(1).trim());
                    map.put(key(cols.get(0), cols.get(1)), Math.max(0, reserves));
                } catch (NumberFormatException ignored) {
                    // cabecera o fila invalida
                }
            }
        } catch (Exception ignored) {
            // sin archivo: ninguna reserva
        }
        return map;
    }

    private static String key(String comunidad, String year) {
        String slug = Normalizer.normalize(comunidad.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return slug + "|" + year.trim();
    }
}
