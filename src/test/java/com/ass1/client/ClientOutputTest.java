package com.ass1.client;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientOutputTest {

    @TempDir
    Path tmp;

    private static ClientResult population(String country, int zone, long value,
                                           long turnaround, long execution,
                                           long waiting, int serverZone) {
        String line = "getPopulationofCountry " + country + " Zone:" + zone;
        return new ClientResult(
                new PopulationOfCountry(country, zone, line),
                new QueryResult(value, execution, waiting, serverZone),
                turnaround);
    }

    private static ClientResult cities(String country, int zone, long value, long turnaround) {
        String line = "getNumberofCities " + country + " 100000 min Zone:" + zone;
        return new ClientResult(
                new NumberOfCities(country, 100000, Comparison.MIN, zone, line),
                new QueryResult(value, 5L, 15L, 2),
                turnaround);
    }

    private List<String> write(Client client) throws IOException {
        Path file = tmp.resolve("out.txt");
        client.writeResultsToFile(file.toString());
        return Files.readAllLines(file);
    }

    @Test
    void resultLineMatchesTheSpecFormat() throws IOException {
        Client client = new Client();
        client.results.add(population("Sweden", 1, 9362428L, 120, 10, 100, 1));

        List<String> lines = write(client);

        assertEquals(
                "9362428 getPopulationofCountry Sweden Zone:1 "
                        + "(turnaround time: 120 ms, execution time: 10 ms, "
                        + "waiting time: 100 ms, processed by Server 1)",
                lines.get(0));
    }

    @Test
    void writesOneLinePerResultInOrder() throws IOException {
        Client client = new Client();
        client.results.add(population("Norway", 1, 1L, 100, 10, 90, 1));
        client.results.add(population("Sweden", 2, 2L, 110, 10, 100, 1));
        client.results.add(population("Denmark", 3, 3L, 120, 10, 110, 1));

        List<String> lines = write(client);

        assertTrue(lines.get(0).contains("Norway"));
        assertTrue(lines.get(1).contains("Sweden"));
        assertTrue(lines.get(2).contains("Denmark"));
    }

    @Test
    void echoesTheOriginalQueryVerbatim() throws IOException {
        Client client = new Client();
        client.results.add(population("United States", 4, 99L, 100, 10, 90, 2));

        assertTrue(write(client).get(0)
                .contains("getPopulationofCountry United States Zone:4"));
    }

    @Test
    void blankLineSeparatesResultsFromStatistics() throws IOException {
        Client client = new Client();
        client.results.add(population("Norway", 1, 1L, 100, 10, 90, 1));

        List<String> lines = write(client);

        assertEquals("", lines.get(1));
        assertTrue(lines.get(2).startsWith("getPopulationofCountry avg"));
    }

    @Test
    void onStatisticsEntryPerQueryType() throws IOException {
        Client client = new Client();
        client.results.add(population("Norway", 1, 1L, 100, 10, 90, 1));
        client.results.add(population("Sweden", 1, 2L, 200, 20, 180, 1));
        client.results.add(cities("Norway", 2, 4L, 300));

        List<String> statistics = write(client).stream()
                .filter(line -> line.contains("avg turn-around time"))
                .toList();

        assertEquals(2, statistics.size());
        assertTrue(statistics.get(0).startsWith("getPopulationofCountry "));
        assertTrue(statistics.get(1).startsWith("getNumberofCities "));
    }

    @Test
    void statisticsReportAveragesMinAndMax() throws IOException {
        Client client = new Client();
        client.results.add(population("Norway", 1, 1L, 100, 10, 90, 1));
        client.results.add(population("Sweden", 1, 2L, 200, 30, 170, 1));

        String line = write(client).stream()
                .filter(l -> l.startsWith("getPopulationofCountry avg"))
                .findFirst()
                .orElseThrow();

        assertTrue(line.contains("avg turn-around time: 150.0 ms"), line);
        assertTrue(line.contains("avg execution time: 20.0 ms"), line);
        assertTrue(line.contains("avg waiting time: 130.0 ms"), line);
        assertTrue(line.contains("min turn-around time: 100 ms"), line);
        assertTrue(line.contains("max turn-around time: 200 ms"), line);
    }

    @Test
    void decimalsUseADotRegardlessOfLocale() throws IOException {
        Client client = new Client();
        client.results.add(population("Norway", 1, 1L, 10, 1, 1, 1));
        client.results.add(population("Sweden", 1, 2L, 11, 1, 1, 1));

        String line = write(client).stream()
                .filter(l -> l.startsWith("getPopulationofCountry avg"))
                .findFirst()
                .orElseThrow();

        assertTrue(line.contains("10.5"), "a comma separator would break parsing: " + line);
    }

    @Test
    void failedQueriesStillGetALine() throws IOException {
        Client client = new Client();
        client.readQueries("src/test/resources/queries-test.txt");

        client.results.add(population("Norway", 1, 1L, 100, 10, 90, 1));
        client.results.add(null);
        client.results.add(population("Denmark", 3, 3L, 120, 10, 110, 1));

        List<String> lines = write(client);

        assertEquals(3, lines.indexOf(""), "three result lines before the blank separator");
        assertTrue(lines.get(1).startsWith("FAILED "), lines.get(1));
        assertTrue(lines.get(1).contains(client.getQueries().get(1).originalQuery));
    }

    @Test
    void failedQueriesAreExcludedFromStatistics() throws IOException {
        Client client = new Client();
        client.readQueries("src/test/resources/queries-test.txt");

        client.results.add(population("Norway", 1, 100L, 100, 10, 90, 1));
        client.results.add(null);

        String line = write(client).stream()
                .filter(l -> l.startsWith("getPopulationofCountry"))
                .filter(l -> l.contains("avg"))
                .findFirst()
                .orElseThrow();

        assertTrue(line.contains("avg turn-around time: 100.0 ms"), line);
    }

    @Test
    void emptyResultsProduceAnAlmostEmptyFile() throws IOException {
        Client client = new Client();

        List<String> lines = write(client);

        assertTrue(lines.isEmpty() || lines.stream().allMatch(String::isBlank));
    }
}
