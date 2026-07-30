package com.platform.analytics.util;

import com.platform.analytics.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvAnalyticsParserTest {

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "data.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parse_validCsv_returnsRowsInOrder() {
        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,1000,5000,200,30,10
                2026-07-02,1050,5200,210,32,11
                """;

        List<CsvAnalyticsParser.Row> rows = CsvAnalyticsParser.parse(csvFile(csv));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(new CsvAnalyticsParser.Row(LocalDate.of(2026, 7, 1), 1000, 5000, 200, 30, 10));
        assertThat(rows.get(1).date()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void parse_headersCaseInsensitiveAndAnyOrder_stillParses() {
        String csv = """
                Shares,DATE,Comments,Followers,Likes,Views
                5,2026-07-01,3,1000,20,500
                """;

        List<CsvAnalyticsParser.Row> rows = CsvAnalyticsParser.parse(csvFile(csv));

        assertThat(rows).hasSize(1);
        CsvAnalyticsParser.Row row = rows.get(0);
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(row.followers()).isEqualTo(1000);
        assertThat(row.views()).isEqualTo(500);
        assertThat(row.likes()).isEqualTo(20);
        assertThat(row.comments()).isEqualTo(3);
        assertThat(row.shares()).isEqualTo(5);
    }

    @Test
    void parse_blankNumericCell_defaultsToZero() {
        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,1000,,,,
                """;

        List<CsvAnalyticsParser.Row> rows = CsvAnalyticsParser.parse(csvFile(csv));

        CsvAnalyticsParser.Row row = rows.get(0);
        assertThat(row.followers()).isEqualTo(1000);
        assertThat(row.views()).isZero();
        assertThat(row.likes()).isZero();
    }

    @Test
    void parse_missingRequiredHeader_throwsBadRequest() {
        String csv = """
                date,followers,views,likes,comments
                2026-07-01,1000,500,20,3
                """;

        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("shares");
    }

    @Test
    void parse_invalidDate_throwsBadRequestWithRowNumber() {
        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,1000,500,20,3,1
                not-a-date,1000,500,20,3,1
                """;

        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Row 3")
                .hasMessageContaining("date");
    }

    @Test
    void parse_negativeNumber_throwsBadRequest() {
        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,-5,500,20,3,1
                """;

        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("followers")
                .hasMessageContaining("negative");
    }

    @Test
    void parse_nonNumericValue_throwsBadRequest() {
        String csv = """
                date,followers,views,likes,comments,shares
                2026-07-01,abc,500,20,3,1
                """;

        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void parse_emptyFile_throwsBadRequest() {
        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile("")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void parse_headersOnlyNoDataRows_throwsBadRequest() {
        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile("date,followers,views,likes,comments,shares\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no data rows");
    }

    @Test
    void parse_tooManyRows_throwsBadRequest() {
        String header = "date,followers,views,likes,comments,shares\n";
        String body = Stream.iterate(LocalDate.of(2020, 1, 1), d -> d.plusDays(1))
                .limit(5001)
                .map(d -> d + ",100,100,10,1,1")
                .collect(Collectors.joining("\n"));

        assertThatThrownBy(() -> CsvAnalyticsParser.parse(csvFile(header + body)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
    }
}
