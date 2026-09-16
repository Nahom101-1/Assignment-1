package com.ass1.server;
import java.rmi.*; 

interface ServerInterFace extends Remote {

    QueryResult getPopulationofCountry(String countryName, int clientZone); 
    QueryResult getNumberofCities(String countryName, int threshold, String comp, int clientZone); 
    QueryResult getNumberofCountries(String citycount, int threshold, String comp, int clientZone); 
    QueryResult getNumberofCountriesMM(int citycount, int minpopulation, int taxpopulation, int clientZone); 
    int getCurrentWorkload(); 
}
