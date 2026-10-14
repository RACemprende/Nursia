package com.fir.simulacro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.Calendar;

public final class DailySimulacro {
    public static final int QUESTIONS_PER_DAY = 10;

    private static final String TIME_ZONE = "Europe/Madrid";

    private DailySimulacro() {
    }

    public static long currentDayIndex() {
        Calendar madrid = Calendar.getInstance(TimeZone.getTimeZone(TIME_ZONE));
        return dayIndexOf(madrid.get(Calendar.YEAR), madrid.get(Calendar.MONTH), madrid.get(Calendar.DAY_OF_MONTH));
    }

    public static String currentDayLabel() {
        Calendar madrid = Calendar.getInstance(TimeZone.getTimeZone(TIME_ZONE));
        return String.format(Locale.US, "%02d/%02d/%04d",
                madrid.get(Calendar.DAY_OF_MONTH), madrid.get(Calendar.MONTH) + 1, madrid.get(Calendar.YEAR));
    }

    private static long dayIndexOf(int year, int month, int day) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month, day);
        long days = utc.getTimeInMillis() / 86_400_000L;
        utc.clear();
        utc.set(2026, Calendar.JANUARY, 1);
        return days - utc.getTimeInMillis() / 86_400_000L;
    }

    /**
     * Indices (sobre la lista ordenada de la base) de las preguntas del dia.
     * Cada ciclo recorre la base completa en un orden barajado con semilla = numero de ciclo,
     * asi que todo el mundo obtiene lo mismo y no se repite nada hasta agotar la base.
     */
    public static List<Integer> pickIndices(int totalQuestions, long dayIndex) {
        List<Integer> picked = new ArrayList<>();
        if (totalQuestions <= 0) return picked;
        int wanted = Math.min(QUESTIONS_PER_DAY, totalQuestions);

        long position = Math.max(0, dayIndex) * QUESTIONS_PER_DAY;
        long loadedCycle = -1;
        int[] permutation = null;
        Set<Integer> seen = new HashSet<>();
        while (picked.size() < wanted) {
            long cycle = position / totalQuestions;
            if (cycle != loadedCycle) {
                permutation = shuffledIndices(totalQuestions, cycle);
                loadedCycle = cycle;
            }
            int index = permutation[(int) (position % totalQuestions)];
            if (seen.add(index)) picked.add(index);
            position++;
        }
        return picked;
    }

    private static int[] shuffledIndices(int size, long seed) {
        int[] array = new int[size];
        for (int i = 0; i < size; i++) array[i] = i;
        int state = (int) (seed * 0x9E3779B9L + 0x6D2B79F5L);
        for (int i = size - 1; i > 0; i--) {
            state += 0x6D2B79F5;
            int z = state;
            z = (z ^ (z >>> 15)) * (z | 1);
            z ^= z + (z ^ (z >>> 7)) * (z | 61);
            z ^= z >>> 14;
            long unsigned = z & 0xFFFFFFFFL;
            int j = (int) (unsigned % (i + 1));
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
        return array;
    }
}
