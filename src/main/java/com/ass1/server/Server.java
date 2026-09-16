package com.ass1.server;

import com.ass1.common.Comparison;

public class Server implements ServerInterface {
    public QueryResult getPopulationOfCountry(String countryName, int clientZone);
    public QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone);
    public QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone);
    public QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone);
    public int getCurrentWorkload();
}