package com.fir.simulacro;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExamScoreStats {
    public final double[] scores;

    private ExamScoreStats(double[] scores) {
        this.scores = scores;
    }

    public static String assetNameFor(String comunidad, String year) {
        String slug = Normalizer.normalize(comunidad, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "notas/notas_" + slug + "_" + year.trim() + ".csv";
    }

    public static boolean isAvailable(Context context, String comunidad, String year) {
        try (java.io.InputStream in = context.getAssets().open(assetNameFor(comunidad, year))) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Notas reales de ese examen, o null si no hay CSV (o no tiene notas validas). */
    public static ExamScoreStats load(Context context, String comunidad, String year) {
        List<Double> values = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(assetNameFor(comunidad, year)), StandardCharsets.UTF_8))) {
            String line;
            char delimiter = ',';
            int column = 0;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    line = line.replace("﻿", "");
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (first) {
                    if (line.indexOf(';') >= 0) {
                        delimiter = ';';
                    } else if (line.indexOf(',') < 0 || line.trim().matches("\"?-?\\d+,\\d+\"?")) {
                        delimiter = '\n'; // una sola columna: la coma es decimal
                    } else {
                        delimiter = ',';
                    }
                }
                List<String> cols = delimiter == '\n'
                        ? java.util.Collections.singletonList(line.replace("\"", ""))
                        : splitLine(line, delimiter);
                if (first) {
                    first = false;
                    boolean header = parseNumber(cols.get(0)) == null;
                    if (header) {
                        for (int i = 0; i < cols.size(); i++) {
                            if ("nota".equalsIgnoreCase(cols.get(i).trim())) {
                                column = i;
                                break;
                            }
                        }
                        continue;
                    }
                }
                if (column >= cols.size()) {
                    continue;
                }
                Double value = parseNumber(cols.get(column));
                if (value != null) {
                    values.add(value);
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (values.size() < 2) {
            return null;
        }
        double[] scores = new double[values.size()];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = values.get(i);
        }
        return new ExamScoreStats(scores);
    }

    /** Puesto del usuario (1 = mejor nota) entre los que se presentaron. */
    public int rankOf(double userScore) {
        int better = 0;
        for (double s : scores) {
            if (s > userScore) {
                better++;
            }
        }
        return better + 1;
    }

    /** "Top X%": porcentaje de presentados que habrian quedado por delante o igual que tu puesto. */
    public int topPercent(double userScore) {
        int percent = (int) Math.ceil(rankOf(userScore) * 100.0 / scores.length);
        return Math.max(1, Math.min(100, percent));
    }

    private static List<String> splitLine(String line, char delimiter) {
        if (delimiter == ',') {
            return CsvUtils.parseCsvLine(line);
        }
        List<String> parts = new ArrayList<>();
        for (String p : line.split(";", -1)) {
            parts.add(p.replace("\"", ""));
        }
        return parts;
    }

    private static Double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }
}
