package com.ass1.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;


public interface ServerInterface extends Remote {

    QueryResult getPopulationOfCountry(String countryName, int clientZone) throws RemoteException;
    QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone) throws RemoteException;
    QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone) throws RemoteException;
    QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone) throws RemoteException;
    int getCurrentWorkload() ;
}
