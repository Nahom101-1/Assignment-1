package com.ass1.server;
import java.rmi.*; 

interface ServerInterFace extends Remote {

    QueryResult getPopulationofCountry(String countryName); 
    QueryResult getNumberofCities(String countryName, int threshold, String comp); 
    QueryResult getNumberofCountries(String citycount, int threshold, String comp); 
    QueryResult getNumberofCountriesMM(int citycount, int minpopulation, int taxpopulation); 
    int getCurrentWorkload(); 
}
