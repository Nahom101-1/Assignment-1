package com.ass1.server;
import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;


public interface ServerInterface extends Remote {
    int getPopulationofCountry(String countryName) throws RemoteException; 
    int getNumberofCities(String countryName, int threshold, String comp) throws RemoteException; 
    int getNumberofCountries(int citycount, int threshold, String comp) throws RemoteException; 
    int getNumberofCountriesMM(int citycount, int minpopulation, int maxpopulation) throws RemoteException; 
    int getCurrentWorkload() throws RemoteException; 


public interface ServerInterface extends Remote {

    QueryResult getPopulationOfCountry(String countryName, int clientZone);
    QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone);
    QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone);
    QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone);
    int getCurrentWorkload();
}
