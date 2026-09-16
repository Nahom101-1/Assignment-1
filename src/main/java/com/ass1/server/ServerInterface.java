package com.ass1.server;
import java.rmi.*; 

interface ServerInterFace extends Remote {

    int getPopulationofCountry(String countryName); 
    int getNumberofCities(String countryName, int threshold, String comp); 
    int getNumberofCountries(String citycount, int threshold, String comp); 
    int getNumberofCountriesMM(int citycount, int minpopulation, int taxpopulation); 
    int getCurrentWorkload(); 

}
