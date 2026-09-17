package com.ass1.server;
import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;

import java.rmi.*;

public interface ServerInterface extends Remote {

    QueryResult getPopulationOfCountry(String countryName, int clientZone);
    QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone);
    QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone);
    QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone);
    int getCurrentWorkload();
}
