package com.proyredes.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Colores ANSI
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";

    @Override
    public String format(LogRecord record) {
        String timestamp = String.format("%s[%s]%s", GRAY, sdf.format(new Date(record.getMillis())), RESET);
        String levelColor;
        switch (record.getLevel().getName()) {
            case "SEVERE":
                levelColor = RED;
                break;
            case "WARNING":
                levelColor = YELLOW;
                break;
            case "INFO":
                levelColor = BLUE;
                break;
            default:
                levelColor = RESET;
        }
        String level = String.format("%s%s%s", levelColor, record.getLevel().getName(), RESET);

        return String.format("%s %s: %s%n", timestamp, level, formatMessage(record));
    }
}
