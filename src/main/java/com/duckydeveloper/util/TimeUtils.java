package com.duckydeveloper.util;

public class TimeUtils {

    public static String formatCooldown(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        if (minutes == 0) {
            return String.format("%ds", remainingSeconds);
        }
        return String.format("%dm, %02ds", minutes, remainingSeconds);
    }

    public static String formatCooldown(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes == 0) {
            return String.format("%ds", remainingSeconds);
        }
        return String.format("%dm, %02ds", minutes, remainingSeconds);
    }

    public static String formatDuration(long totalSeconds) {
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600) % 24;
        long days = (totalSeconds / 86400) % 7;
        long weeks = (totalSeconds / 604800) % 4;
        long months = totalSeconds / 2419200;

        String[] names = {"month", "week", "day", "hour", "minute", "second"};
        long[] values = {months, weeks, days, hours, minutes, seconds};

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < values.length && count < 2; i++) {
            if (values[i] > 0) {
                sb.append(values[i]).append(" ").append(names[i]);
                if (values[i] > 1) sb.append("s");
                sb.append(" ");
                count++;
            }
        }
        return sb.toString().trim();
    }
}