package com.ass1.client;

import com.ass1.common.Comparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Client}: file reading and per-line query parsing.
 */
class ClientTest {

    @TempDir
    Path tmp;

    /**
     * Writes the given lines to a temp file, reads them through a fresh
     * {@link Client} and returns the parsed queries.
     */
    private List<Query> parseLines(String... lines) throws IOException {
        Path file = Files.createTempFile(tmp, "queries", ".txt");
        Files.write(file, List.of(lines));

        Client client = new Client();
        client.readQueries(file.toString());
        return client.getQueries();
    }

    /** Parses a single line and returns the resulting query. */
    private Query parseLine(String line) throws IOException {
        return parseLines(line).get(0);
    }

    // ---------------------------------------------------------------- reading

    @Test
    void readsQueriesFromFile() throws IOException {
        Client client = new Client();

        client.readQueries("src/test/resources/queries-test.txt");

        assertEquals(4, client.getQueries().size());
    }

    @Test
    void readQueriesAppendsToExistingQueries() throws IOException {
        Client client = new Client();

        client.readQueries("src/test/resources/queries-test.txt");
        client.readQueries("src/test/resources/queries-test.txt");

        assertEquals(8, client.getQueries().size(), "readQueries accumulates across calls");
    }

    @Test
    void preservesFileOrder() throws IOException {
        List<Query> queries = parseLines(
                "getPopulationofCountry Norway Zone:1",
                "getNumberofCities Norway 100 min Zone:2",
                "getNumberofCountries 3 100 max Zone:3",
                "getNumberofCountriesMM 4 100 200 Zone:4");

        assertInstanceOf(PopulationOfCountry.class, queries.get(0));
        assertInstanceOf(NumberOfCities.class, queries.get(1));
        assertInstanceOf(NumberOfCountries.class, queries.get(2));
        assertInstanceOf(NumberOfCountriesMM.class, queries.get(3));
    }

    @Test
    void handlesWindowsLineEndings() throws IOException {
        Path file = tmp.resolve("crlf.txt");
        Files.writeString(file,
                "getPopulationofCountry Norway Zone:1\r\n"
                        + "getNumberofCities Norway 100 min Zone:2\r\n");

        Client client = new Client();
        client.readQueries(file.toString());

        assertEquals(2, client.getQueries().size());
        assertEquals(1, client.getQueries().get(0).zone);
        assertEquals(2, client.getQueries().get(1).zone);
    }

    @Test
    void missingFileThrowsIoException() {
        Client client = new Client();

        assertThrows(IOException.class, () -> client.readQueries("does/not/exist.txt"));
    }

    // -------------------------------------------- getPopulationofCountry

    @Test
    void parsesPopulationOfCountryCorrectly() throws IOException {
        Query query = parseLine("getPopulationofCountry United States Zone:1");

        PopulationOfCountry population = assertInstanceOf(PopulationOfCountry.class, query);
        assertEquals("United States", population.countryName);
        assertEquals(1, population.zone);
    }

    @Test
    void parsesSingleWordCountryName() throws IOException {
        PopulationOfCountry population =
                assertInstanceOf(PopulationOfCountry.class,
                        parseLine("getPopulationofCountry Seychelles Zone:4"));

        assertEquals("Seychelles", population.countryName);
        assertEquals(4, population.zone);
    }

    @Test
    void parsesThreeWordCountryName() throws IOException {
        PopulationOfCountry population =
                assertInstanceOf(PopulationOfCountry.class,
                        parseLine("getPopulationofCountry Bosnia and Herzegovina Zone:2"));

        assertEquals("Bosnia and Herzegovina", population.countryName);
    }

    // ------------------------------------------------- getNumberofCities

    @Test
    void parsesNumberOfCitiesCorrectly() throws IOException {
        NumberOfCities cities =
                assertInstanceOf(NumberOfCities.class,
                        parseLine("getNumberofCities Norway 568422 min Zone:4"));

        assertEquals("Norway", cities.countryName);
        assertEquals(568422, cities.threshold);
        assertEquals(Comparison.MIN, cities.comp);
        assertEquals(4, cities.zone);
    }

    @Test
    void parsesNumberOfCitiesWithMultiWordCountry() throws IOException {
        NumberOfCities cities =
                assertInstanceOf(NumberOfCities.class,
                        parseLine("getNumberofCities Equatorial Guinea 59582 min Zone:5"));

        assertEquals("Equatorial Guinea", cities.countryName);
        assertEquals(59582, cities.threshold);
        assertEquals(Comparison.MIN, cities.comp);
        assertEquals(5, cities.zone);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({"min, MIN", "max, MAX", "MIN, MIN", "Max, MAX"})
    void comparisonIsCaseInsensitive(String literal, Comparison expected) throws IOException {
        NumberOfCities cities =
                assertInstanceOf(NumberOfCities.class,
                        parseLine("getNumberofCities Honduras 40894 " + literal + " Zone:5"));

        assertEquals(expected, cities.comp);
    }

    @Test
    void unknownComparisonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> parseLine("getNumberofCities Honduras 40894 avg Zone:5"));
    }

    // ---------------------------------------------- getNumberofCountries

    @Test
    void parsesNumberOfCountriesCorrectly() throws IOException {
        NumberOfCountries countries =
                assertInstanceOf(NumberOfCountries.class,
                        parseLine("getNumberofCountries 3 1616894 min Zone:5"));

        assertEquals(3, countries.cityCount);
        assertEquals(1616894, countries.threshold);
        assertEquals(Comparison.MIN, countries.comp);
        assertEquals(5, countries.zone);
    }

    @Test
    void parsesNumberOfCountriesWithMaxComparison() throws IOException {
        NumberOfCountries countries =
                assertInstanceOf(NumberOfCountries.class,
                        parseLine("getNumberofCountries 9 100973 max Zone:3"));

        assertEquals(9, countries.cityCount);
        assertEquals(100973, countries.threshold);
        assertEquals(Comparison.MAX, countries.comp);
        assertEquals(3, countries.zone);
    }

    // -------------------------------------------- getNumberofCountriesMM

    @Test
    void parsesNumberOfCountriesMmCorrectly() throws IOException {
        NumberOfCountriesMM countries =
                assertInstanceOf(NumberOfCountriesMM.class,
                        parseLine("getNumberofCountriesMM 6 1677496 4406235 Zone:4"));

        assertEquals(6, countries.cityCount);
        assertEquals(1677496, countries.minPopulation);
        assertEquals(4406235, countries.maxPopulation);
        assertEquals(4, countries.zone);
    }

    @Test
    void numberOfCountriesMmIsNotConfusedWithNumberOfCountries() throws IOException {
        Query query = parseLine("getNumberofCountriesMM 2 71922 124524 Zone:2");

        assertInstanceOf(NumberOfCountriesMM.class, query);
        assertFalse(query instanceof NumberOfCountries,
                "the MM variant must not be parsed as the min/max-comparison variant");
    }

    // ------------------------------------------------------------ failures

    @Test
    void unknownMethodThrows() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parseLine("getSomethingElse Norway Zone:1"));

        assertTrue(error.getMessage().contains("getSomethingElse"),
                "error message should name the unsupported method");
    }

    @Test
    void nonNumericZoneThrows() {
        assertThrows(NumberFormatException.class,
                () -> parseLine("getPopulationofCountry Norway Zone:x"));
    }

    @Test
    void nonNumericThresholdThrows() {
        assertThrows(NumberFormatException.class,
                () -> parseLine("getNumberofCities Norway many min Zone:1"));
    }

    // --------------------------------------------------------- originalQuery

    @Test
    void everyQueryTypeKeepsItsOriginalLine() throws IOException {
        String population = "getPopulationofCountry Norway Zone:1";
        String cities     = "getNumberofCities Norway 100000 min Zone:2";
        String countries  = "getNumberofCountries 3 1616894 min Zone:3";
        String countriesMm = "getNumberofCountriesMM 6 1677496 4406235 Zone:4";

        List<Query> parsed = parseLines(population, cities, countries, countriesMm);

        assertEquals(population,  parsed.get(0).originalQuery);
        assertEquals(cities,      parsed.get(1).originalQuery);
        assertEquals(countries,   parsed.get(2).originalQuery);
        assertEquals(countriesMm, parsed.get(3).originalQuery);
    }

    @Test
    void originalQueryIsNeverNullOrBlank() throws IOException {
        for (Query query : parseLines(
                "getPopulationofCountry United States Zone:1",
                "getNumberofCities Norway 100000 max Zone:5")) {

            assertNotNull(query.originalQuery);
            assertFalse(query.originalQuery.isBlank());
        }
    }

    @Test
    void paddedLineStillParsesAndKeepsWhatWasRead() throws IOException {
        Query query = parseLine("   getPopulationofCountry Norway Zone:1   ");

        PopulationOfCountry population = assertInstanceOf(PopulationOfCountry.class, query);
        assertEquals("Norway", population.countryName);
        assertEquals(1, population.zone);
        assertTrue(population.originalQuery.contains("getPopulationofCountry Norway Zone:1"));
    }

    @Test
    void originalQueryMatchesTheRealInputFile() throws IOException {
        Client client = new Client();
        client.readQueries("src/test/resources/queries-test.txt");

        List<String> lines = Files.readAllLines(Path.of("src/test/resources/queries-test.txt"));
        List<Query> parsed = client.getQueries();

        for (int i = 0; i < parsed.size(); i++) {
            assertEquals(lines.get(i), parsed.get(i).originalQuery,
                    "line " + (i + 1) + " should round-trip unchanged");
        }
    }
}
