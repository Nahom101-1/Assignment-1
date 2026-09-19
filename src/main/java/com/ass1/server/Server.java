package com.ass1.server;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import java.io.BufferedReader;
import java.io.FileReader;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;

public class Server implements ServerInterface{
    
  
    public List<String[]> readCsv(){
        // Helper method for reading .csv file and turning it to an array.  
        // Storing .csv file content on a ArrayList.

        List<String[]> dataset = new ArrayList<>();
        try {
            // Temporary path for reading dataset.
            BufferedReader br = new BufferedReader(new FileReader("src/main/java/com/ass1/Resources/exercise_1_dataset.csv"));
            br.readLine(); //Skip header.
            String line;

            // Loop that copies each Row of the .csv file and saving each line on the ArrayList untill it reaches the end.
            while((line = br.readLine()) != null){
                String[] values = line.split(";");
                dataset.add(values);
            }
            br.close();
        } catch(Exception e){
            e.printStackTrace();
        }

        return dataset;
    }
    
    public int getPopulationofCountry(String countryName) {
        List<String[]> data = readCsv(); 
        int population = 0;

        //Loop through dataset and matching countryName with row. 
        //if it matches the population on index 4 is added to population total.
        for(String[] row : data){
            if(countryName.equals(row[3])){
                population += Integer.parseInt(row[4]);
            }
        }
        return population;
    }

    public int getNumberofCities(String countryName, int threshold, String comp) {
        List<String[]> data = readCsv();
        int totalCities = 0;
        

        //Loop through data set and matching countryName with row.
        //If there's a match, we compare population to threshold based on the selected comp "min" or "max".
        //If it meets those requirements totalCities are incremented by 1.
        for(String[] row : data){
            int population = Integer.parseInt(row[4]);

            if(countryName.equals(row[3])){
                if(comp.equalsIgnoreCase("min")){
                    if(population >= threshold){
                    totalCities += 1;
                    }

                } else if(comp.equalsIgnoreCase("max")){
                    if(population < threshold){
                        totalCities += 1;
                    }
                }
            }
        }

        return totalCities;
    }
    public int getNumberofCountries(int citycount, int threshold, String comp) {
        
        //Method is suppoused to return the amount of countries, that has more or equal to CityCount. 
        //The threshold for how large each city is based on comp and threshold.
        //Example: Cities are, less or equal to 200,000.
       
        List<String[]> data = readCsv();
        int numberOfCountries = 0;

        //A country can have more cities that meet the requirements of a city.
        //We're using a hashmap to store: CountryName and CityCount that are qualified.
        HashMap<String, Integer> countryAndCitycount = new HashMap<>(); 

        //Loop through dataset
        for(String[] row : data){
            String countryName = row[3];
            int population = Integer.parseInt(row[4]);

            //Skip rows in the arrayList that doesn't have names/blank.
            if(countryName.isBlank()){
                continue;
            }

            //Comparing rows with comp and threshold. 
            //If the requirements are met, countryName and 1 is added into the HashMap.
            //If the countryName already exist in the HashMap, merge allows us to merge countryNames and add 1 so we avoid duplicates.
            if(comp.equalsIgnoreCase("min")){
                if(population >= threshold){
                    countryAndCitycount.merge(countryName, 1, Integer::sum);
                }
            } else if(comp.equalsIgnoreCase("max")){
                if(population <= threshold){
                    countryAndCitycount.merge(countryName, 1, Integer::sum);
                }
            }
        }

        //Loop through HashMap to find countries that have cities that meet the required citycount.
        for(int countries : countryAndCitycount.values()){
            if(countries >= citycount){
                numberOfCountries += 1;
            }
        }

        return numberOfCountries;
    }
    public int getNumberofCountriesMM(int citycount, int minpopulation, int maxpopulation) {
        //This method uses the same logic as before, except population is based on minimum- and maxiumum population.
        //Qualified cities are now between min and max.

        List<String[]> data = readCsv();
        int numberOfCountries = 0;
        HashMap<String, Integer> countryAndCitycount = new HashMap<>();

        for(String[] row : data){
            String countryName = row[3];
            int population = Integer.parseInt(row[4]);
            if(countryName.isBlank()){
                continue;
            }

            if(population >= minpopulation && population <= maxpopulation){
                countryAndCitycount.merge(countryName, 1, Integer::sum);
            } 
        }

        for(int countries : countryAndCitycount.values()){
            if(countries >= citycount){
                numberOfCountries += 1;
            }
        }

        return numberOfCountries;
    }

    public int getCurrentWorkload() {
        return 0;
    }


    public static void main(String[] args){
        // Simple local testing for methods. Based on examples on exercise_1 document.
        Server server = new Server();
        //int test1 = server.getPopulationofCountry("Norway");
        //int test2 = server.getPopulationofCountry("Sweden");
        //int test3 = server.getNumberofCities("Norway", 100000, "min");
        //int test4 = server.getNumberofCountries(2, 5000000, "min");
        int test5 = server.getNumberofCountriesMM(30, 100000, 800000);

        //System.out.println("getPopulationofCountry test");
        //System.out.println("Expected response:\n Norway: 3162856 \n Sweden: 9362428"); 
        //System.out.println("\n Actual response: \n Norway: " + test1 + "\n Sweden: " + test2);
        
        //System.out.println("getNumberofCities test.");
        //System.out.println("Expected response: 4");
        //System.out.println("Actual response: " + test3);

        //System.out.println("getNumberofCountries test");
        //System.out.println("Expected response: 7");
        //System.out.println("Actual response:" + test4);
        
        System.out.println("getNumberofCountriesMM test");
        System.out.println("Expected response: 30");
        System.out.println("Actual response: " + test5);
    }
}