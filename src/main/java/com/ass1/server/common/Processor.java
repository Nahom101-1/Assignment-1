package com.ass1.server.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.ass1.common.Comparison;
/**
 * Answers the four dataset queries.
 *
 * Instead of scanning the CSV file on every call, the file is parsed exactly once
 * and turned into an index:
 *
 *   country -> { sorted array of city populations, total population }
 *
 * Because the populations are sorted, "how many cities are >= / <= X" becomes a
 * binary search instead of a linear scan, and the total population is already
 * computed. The index is immutable after construction, so it is safe to share
 * between the zone servers running in the same JVM.
 */
public class Processor {

    private static final int COUNTRY_COLUMN = 3;
    private static final int POPULATION_COLUMN = 4;

    /*
    * Stores the path to the dataset CSV file.
    * The path is provided when Processor is created.
    */
    private final Path dataset;

    /*
    * Check that the dataset file actually exists and can be read.
    * If not, stop immediately with a clear error message.
    */
    public Processor(Path dataset){
        if(!Files.isReadable(dataset)){
            throw new IllegalArgumentException("Dataset file not found: " +dataset.toAbsolutePath());
        }

        /*
        * Saves an absolute, normalized version of the path. 
        * Example: data/exercise_1_dataset.csv becomes C:\Users\...\exercise_1_dataset.csv
        */
        this.dataset = dataset.toAbsolutePath().normalize();
    }

    public long getPopulationOfCountry(String countryName){
        long totalPopulation = 0; 

        try (BufferedReader reader = Files.newBufferedReader(dataset)){
            reader.readLine(); /* skip header */

            String line;

            while((line = reader.readLine()) != null){
                String[] columns =line.split(";", -1);

                if(columns.length <= POPULATION_COLUMN){
                    continue;
                }

                String country = columns[COUNTRY_COLUMN].trim();
                String populationText = columns[POPULATION_COLUMN].trim();

                if (country.equalsIgnoreCase(countryName) && !populationText.isEmpty()){
                    totalPopulation += Long.parseLong(populationText);
                }
            }
        } catch (IOException e){
            throw new RuntimeException("Could not read dataset", e);
        }

        return totalPopulation;
    }
    
    public int getNumberOfCities(String countryName, int threshold, Comparison comp){
        int totalCities = 0;
        try(BufferedReader reader = Files.newBufferedReader(dataset)){

            reader.readLine();
            String line;
            while((line = reader.readLine()) != null){
                String[] columns = line.split(";", -1);

                if(columns.length <= POPULATION_COLUMN){
                    continue;
                }

                String country = columns[COUNTRY_COLUMN].trim();
                String populationText = columns[POPULATION_COLUMN].trim();

                if(!country.equalsIgnoreCase(countryName) || populationText.isEmpty()){
                    continue;
                }
                
                int population = Integer.parseInt(populationText);
                if(comp == Comparison.MIN && population >= threshold){
                    totalCities++;
                }

                if(comp == Comparison.MAX && population <= threshold){
                    totalCities++;
                }
            }
                

        } catch(IOException e){
            throw new RuntimeException("Could not read dataset", e);
        }

        return totalCities;
        
    }

    public int getNumberOfCountries(int cityCount, int threshold, Comparison comp){
        Map<String, Integer> countryCounts = new HashMap<>();

        try(BufferedReader reader = Files.newBufferedReader(dataset)){
            reader.readLine();
            String line;

            while((line = reader.readLine()) != null){
                String[] columns = line.split(";", -1);

                if(columns.length <= POPULATION_COLUMN){
                    continue;
                }

                String country = columns[COUNTRY_COLUMN].trim();
                String populationText = columns[POPULATION_COLUMN].trim();
                
                if(country.isEmpty() || populationText.isEmpty()){
                    continue;
                }

                int population = Integer.parseInt(populationText);

                boolean matches = (comp == Comparison.MIN && population >= threshold) || (comp == Comparison.MAX && population <= threshold);

                if(matches){
                    countryCounts.merge(country, 1, Integer::sum);
                }
            }

        } catch(IOException e){
            throw new RuntimeException("Could not read dataset", e);
        }

        int numberOfCountries = 0;

        for(int count : countryCounts.values()) {
            if(count >= cityCount){
                numberOfCountries++;
            }
        }

        return numberOfCountries;
    }

    public int getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation){
        Map<String, Integer> countryCounts = new HashMap<>();

        try(BufferedReader reader = Files.newBufferedReader(dataset)){
            
            reader.readLine();
            String line;
            
            while((line = reader.readLine()) != null){

                String[] columns = line.split(";", -1);

                if(columns.length <= POPULATION_COLUMN){
                    continue;
                }

                String country = columns[COUNTRY_COLUMN].trim();
                String populationText = columns[POPULATION_COLUMN].trim();

                if(country.isEmpty() || populationText.isEmpty()){
                    continue;
                }

                int population = Integer.parseInt(populationText);

                if(population >= minPopulation && population <= maxPopulation){
                    countryCounts.merge(country, 1, Integer::sum);
                }
            }

        } catch(IOException e){ 
            throw new RuntimeException("Could not read dataset", e);
        }

        int numberOfCountries = 0;
        for(int count : countryCounts.values()){
            if(count >= cityCount){
                numberOfCountries++;
            }
        }

        return numberOfCountries;
    }
    
}
