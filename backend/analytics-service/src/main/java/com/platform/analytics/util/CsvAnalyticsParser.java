package com.platform.analytics.util;

import com.platform.analytics.exception.BadRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses a user-uploaded CSV of daily metrics into validated rows, ready to
 * upsert into {@link com.platform.analytics.entity.Analytics}. Fixed
 * schema, one row per day: date,followers,views,likes,comments,shares
 * (header names case-insensitive, column order doesn't matter). Every row
 * is validated before any row is returned, so a malformed file fails
 * entirely rather than partially importing.
 */
public final class CsvAnalyticsParser {

    private CsvAnalyticsParser() {
    }

    /** Generous enough for years of daily data; guards against pathological uploads. */
    private static final int MAX_ROWS = 5000;

    private static final Set<String> REQUIRED_HEADERS =
            Set.of("date", "followers", "views", "likes", "comments", "shares");

    public record Row(LocalDate date, long followers, long views, long likes, long comments, long shares) {
    }

    public static List<Row> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is empty");
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            // Maps each required lowercase header to the actual column name
            // as it appears in the file, since commons-csv's CSVRecord.get(name)
            // lookup is exact-case.
            Map<String, String> headerLookup = new HashMap<>();
            for (String actual : parser.getHeaderNames()) {
                headerLookup.put(actual.trim().toLowerCase(Locale.ROOT), actual);
            }
            if (!headerLookup.keySet().containsAll(REQUIRED_HEADERS)) {
                throw new BadRequestException(
                        "CSV must have these columns (any order): " + String.join(", ", REQUIRED_HEADERS));
            }

            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new BadRequestException("CSV exceeds the maximum of " + MAX_ROWS + " rows");
                }
                rows.add(parseRow(record, headerLookup));
            }

            if (rows.isEmpty()) {
                throw new BadRequestException("CSV has no data rows");
            }
            return rows;
        } catch (IOException e) {
            throw new BadRequestException("Could not read CSV file: " + e.getMessage());
        }
    }

    private static Row parseRow(CSVRecord record, Map<String, String> headerLookup) {
        long line = record.getRecordNumber() + 1; // +1 to account for the header row itself
        String rawDate = record.get(headerLookup.get("date"));
        try {
            LocalDate date = LocalDate.parse(rawDate.trim());
            long followers = parseNonNegativeLong(record.get(headerLookup.get("followers")), "followers", line);
            long views = parseNonNegativeLong(record.get(headerLookup.get("views")), "views", line);
            long likes = parseNonNegativeLong(record.get(headerLookup.get("likes")), "likes", line);
            long comments = parseNonNegativeLong(record.get(headerLookup.get("comments")), "comments", line);
            long shares = parseNonNegativeLong(record.get(headerLookup.get("shares")), "shares", line);
            return new Row(date, followers, views, likes, comments, shares);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Row " + line + ": invalid date (expected YYYY-MM-DD): " + rawDate);
        }
    }

    private static long parseNonNegativeLong(String raw, String field, long line) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return 0;
        }
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Row " + line + ": " + field + " must be a whole number, got \"" + value + "\"");
        }
        if (parsed < 0) {
            throw new BadRequestException("Row " + line + ": " + field + " cannot be negative");
        }
        return parsed;
    }
}
