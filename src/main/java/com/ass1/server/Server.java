package com.ass1.server;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;

public class Server implements ServerInterface {
    @Override
    public QueryResult getPopulationOfCountry(String countryName, int clientZone){
        return null;
    }
    @Override
    public QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone){
        return null;
    }
    @Override
    public QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone){
        return null;
    }
    @Override
    public QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone){
        return null;
    }
    @Override
    public int getCurrentWorkload(){
        return 0;
    }
}