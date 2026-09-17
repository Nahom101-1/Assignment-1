package com.ass1.client;

import com.ass1.common.Comparison;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end parsing of the assignment input file, exercise_1_input.txt.
 *
 * <p>The file uses CRLF line endings, has no trailing newline and mixes all
 * four query types, so it doubles as a regression test for the whole
 * {@code readQueries} path.
 */
class ExerciseInputParsingTest {

    private static final Path INPUT = Path.of("src/test/resources/exercise_1_input.txt");

    private static List<String> lines;
    private static List<Query> queries;

    @BeforeAll
    static void parseInputFile() throws IOException {
        lines = Files.readAllLines(INPUT, StandardCharsets.UTF_8);

        Client client = new Client();
        client.readQueries(INPUT.toString());
        queries = client.getQueries();
    }

    @Test
    void everyLineProducesExactlyOneQuery() {
        assertEquals(lines.size(), queries.size());
        assertEquals(3166, queries.size(), "exercise_1_input.txt holds 3166 queries");
    }

    @Test
    void noQueryIsNull() {
        assertTrue(queries.stream().noneMatch(query -> query == null));
    }

    @Test
    void queryTypeCountsMatchTheInputFile() {
        Map<String, Long> expected = lines.stream()
                .collect(Collectors.groupingBy(
                        line -> line.trim().split("\\s+")[0], Collectors.counting()));

        Map<String, Long> actual = queries.stream()
                .collect(Collectors.groupingBy(
                        query -> switch (query) {
                            case PopulationOfCountry ignored -> "getPopulationofCountry";
                            case NumberOfCities ignored -> "getNumberofCities";
                            case NumberOfCountriesMM ignored -> "getNumberofCountriesMM";
                            case NumberOfCountries ignored -> "getNumberofCountries";
                            default -> "unknown";
                        },
                        Collectors.counting()));

        assertEquals(expected, actual);
        assertEquals(791L, actual.get("getPopulationofCountry"));
        assertEquals(791L, actual.get("getNumberofCities"));
        assertEquals(793L, actual.get("getNumberofCountries"));
        assertEquals(791L, actual.get("getNumberofCountriesMM"));
    }

    @Test
    void everyZoneIsBetweenOneAndFive() {
        for (int i = 0; i < queries.size(); i++) {
            int zone = queries.get(i).zone;
            assertTrue(zone >= 1 && zone <= 5,
                    "zone out of range on line " + (i + 1) + ": " + lines.get(i));
        }
    }

    @Test
    void zoneDistributionMatchesTheInputFile() {
        Map<Integer, Long> expected = lines.stream()
                .collect(Collectors.groupingBy(
                        line -> Integer.parseInt(
                                line.trim().substring(line.trim().lastIndexOf("Zone:") + 5)),
                        Collectors.counting()));

        Map<Integer, Long> actual = queries.stream()
                .collect(Collectors.groupingBy(query -> query.zone, Collectors.counting()));

        assertEquals(expected, actual);
    }

    @Test
    void countryNamesAreCleanAndNeverCarryZoneOrNumbers() {
        for (Query query : queries) {
            String country = countryNameOf(query);
            if (country == null || country.isEmpty()) {
                continue; // covered by linesMissingACountryNameParseToAnEmptyName
            }

            assertEquals(country.trim(), country, "country name must not be padded");
            assertFalse(country.contains("Zone:"), "country name leaked the zone: " + country);
            assertFalse(country.matches(".*\\d.*"), "country name leaked a number: " + country);
        }
    }

    /**
     * The supplied input file contains nine malformed lines where the country
     * name is simply missing (e.g. {@code "getPopulationofCountry Zone:3"}).
     * The parser does not reject them - it yields an empty country name - so
     * this test pins that behaviour and shows exactly which lines are affected.
     */
    @Test
    void linesMissingACountryNameParseToAnEmptyName() {
        List<String> malformed = lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("getPopulationofCountry")
                        ? line.split("\\s+").length < 3
                        : line.startsWith("getNumberofCities") && line.split("\\s+").length < 5)
                .toList();

        long emptyNames = queries.stream()
                .map(ExerciseInputParsingTest::countryNameOf)
                .filter(name -> name != null && name.isEmpty())
                .count();

        assertEquals(9, malformed.size(), "input file lines with no country name: " + malformed);
        assertEquals(malformed.size(), emptyNames,
                "every malformed line should surface as an empty country name");
    }

    private static String countryNameOf(Query query) {
        return switch (query) {
            case PopulationOfCountry population -> population.countryName;
            case NumberOfCities cities -> cities.countryName;
            default -> null;
        };
    }

    @Test
    void thresholdsAndCityCountsArePositive() {
        for (Query query : queries) {
            switch (query) {
                case NumberOfCities cities -> {
                    assertTrue(cities.threshold > 0);
                    assertNotNull(cities.comp);
                }
                case NumberOfCountries countries -> {
                    assertTrue(countries.cityCount > 0);
                    assertTrue(countries.threshold > 0);
                    assertNotNull(countries.comp);
                }
                case NumberOfCountriesMM countries -> {
                    assertTrue(countries.cityCount > 0);
                    assertTrue(countries.minPopulation > 0);
                    assertTrue(countries.maxPopulation > 0);
                }
                default -> { }
            }
        }
    }

    @Test
    void minPopulationNeverExceedsMaxPopulation() {
        queries.stream()
                .filter(NumberOfCountriesMM.class::isInstance)
                .map(NumberOfCountriesMM.class::cast)
                .forEach(query -> assertTrue(
                        query.minPopulation <= query.maxPopulation,
                        "min " + query.minPopulation + " > max " + query.maxPopulation));
    }

    @Test
    void bothComparisonKindsOccur() {
        List<Comparison> comparisons = queries.stream()
                .map(query -> switch (query) {
                    case NumberOfCities cities -> cities.comp;
                    case NumberOfCountries countries -> countries.comp;
                    default -> null;
                })
                .filter(comparison -> comparison != null)
                .toList();

        assertTrue(comparisons.contains(Comparison.MIN));
        assertTrue(comparisons.contains(Comparison.MAX));
        assertEquals(791 + 793, comparisons.size());
    }

    @Test
    void firstAndLastLinesParseToTheExpectedValues() {
        PopulationOfCountry first =
                assertInstanceOf(PopulationOfCountry.class, queries.get(0));
        assertEquals("French Guiana", first.countryName);
        assertEquals(4, first.zone);

        NumberOfCountriesMM last = assertInstanceOf(
                NumberOfCountriesMM.class, queries.get(queries.size() - 1));
        assertEquals(11, last.cityCount);
        assertEquals(40468, last.minPopulation);
        assertEquals(87521, last.maxPopulation);
        assertEquals(3, last.zone);
    }
}
