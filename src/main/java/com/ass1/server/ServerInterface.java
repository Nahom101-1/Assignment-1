package com.ass1.server;

import com.ass1.common.QueryResult;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The remote interface of a zone server – this is the contract the client sees.
 *
 * <p>In Java RMI every remote interface must extend {@link Remote} and every
 * method must declare {@code throws RemoteException} (the network can always
 * fail). The {@code clientZone} parameter is needed to simulate the network
 * delay: 80 ms inside the same zone, 80 + 30 ms for every zone of distance.</p>
 */
public interface ServerInterface extends Remote {

    /** Total population of a country. */
    QueryResult getPopulationofCountry(String country, int clientZone) throws RemoteException;

    /** Cities of a country with at least ("min") / at most ("max") {@code threshold} people. */
    QueryResult getNumberofCities(String country, long threshold, String comparison, int clientZone)
            throws RemoteException;

    /** Countries with at least {@code cityCount} such cities. */
    QueryResult getNumberofCountries(int cityCount, long threshold, String comparison, int clientZone)
            throws RemoteException;

    /** Countries with at least {@code cityCount} cities between two population limits. */
    QueryResult getNumberofCountriesMM(int cityCount, long minPopulation, long maxPopulation, int clientZone)
            throws RemoteException;

    /** Number of requests currently waiting in this server's waiting list. */
    int getQueueLength() throws RemoteException;

    /** Zone this server belongs to (assigned by the proxy at registration time). */
    int getZone() throws RemoteException;
}

