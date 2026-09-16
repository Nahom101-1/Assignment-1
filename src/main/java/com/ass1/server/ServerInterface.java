package com.ass1.server;
import java.rmi.*; 

interface ServerInterFace extends Remote {

    QueryResult getPopulationOfCountry(String countryName, int clientZone); 
    QueryResult getNumberOfCities(String countryName, int threshold, String comp, int clientZone); 
    QueryResult getNumberOfCountries(int cityCount, int threshold, String comp, int clientZone); 
    QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone); 
    int getCurrentWorkload(); 
}
