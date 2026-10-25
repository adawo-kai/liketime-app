package com.liketime.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * CSV для Excel с русской локалью: разделитель «;», дробная часть через запятую, кодировка UTF-8 с BOM
 * (иначе Excel покажет кракозябры).
 */
public final class CsvFile {

    private static final String BOM = "﻿";

    private final String filename;
    private final StringBuilder content = new StringBuilder(BOM);

    public CsvFile(String filename) {
        this.filename = filename;
    }

    public CsvFile row(Object... cells) {
        List<String> out = new ArrayList<>();
        for (Object cell : cells) {
            out.add(format(cell));
        }
        content.append(String.join(";", out)).append("\r\n");
        return this;
    }

    public ResponseEntity<byte[]> toResponse() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String format(Object cell) {
        if (cell == null) {
            return "";
        }
        if (cell instanceof BigDecimal money) {
            return money.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        }
        if (cell instanceof Number number) {
            return number.toString();
        }
        return quote(neutralizeFormula(cell.toString()));
    }

    /** Текст, начинающийся с = + - @, Excel выполнил бы как формулу: добавляем апостроф */
    private static String neutralizeFormula(String text) {
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    private static String quote(String text) {
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
