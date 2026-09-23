package com.ass1.server;

import com.ass1.server.common.Result;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerInterface extends Remote {
    Result getPopulationOfCountry(String countryName, int clientZone) throws RemoteException;
    Result getNumberOfCities(String countryName, int threshold, String comp, int clientZone) throws RemoteException;
    Result getNumberOfCountries(int cityCount, int threshold, String comp, int clientZone) throws RemoteException;
    Result getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone) throws RemoteException;
    int getCurrentWorkload() throws RemoteException;
}
