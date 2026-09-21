package com.ass1.server;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import java.io.BufferedReader;
import java.io.FileReader;

import java.rmi.RemoteException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;

public class Server implements ServerInterface{

    //TODO: Assigned when the server registers with proxy


    //-------------
    // FIFO-queue
    //-------------
    // Using a LinkedBlockingQueue for FIFO queue
    // Makes the FIFO-queue that contains task-objects.
    BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();

    // Instructions for the worker thread.
    // Takes out the first task and continously the next one.
    // If there are no tasks it will wait untill a task becomes available.
    Runnable work = new Runnable(){ 
        @Override
        public void run(){
            while(true){ //loop for the thread. Takes tasks out of queue first in, first out and returns a QueryResult.
                try{
                    Task task = taskQueue.take();
                    switch(task.getMethod()){ // Select which task/query the worker should execute. 
                        case "getPopulationOfCountry": {
                            String countryName = (String) task.getArguments()[0];
                            int clientZone = (int) task.getArguments()[1];
                            
                            long start = System.currentTimeMillis(); //Calculating execution time based on before and after calculation.
                            int value = calculatePopulationofCountry(countryName);    
                            long executionTime =  System.currentTimeMillis() - start;
                            
                            QueryResult result = new QueryResult(value, executionTime, 0, serverZone);  
                            task.getFuture().complete(result); //returns a result when task is complete. Using getFuture to synchronize result with RMI-call.     
                            break;
                        }
                        case "getNumberOfCities": {
                            String countryName = (String) task.getArguments()[0];
                            int threshold = (int) task.getArguments()[1];
                            Comparison comp = (Comparison) task.getArguments()[2];
                            int clientZone = (int) task.getArguments()[3];

                            long start = System.currentTimeMillis();
                            int value = calculateNumberofCities(countryName, threshold, comp);
                            long executionTime = System.currentTimeMillis() - start;

                            QueryResult result = new QueryResult(value, executionTime, 0, serverZone);
                            task.getFuture().complete(result);
                            break;
                        }
                        case "getNumberOfCountries": {
                            int cityCount = (int) task.getArguments()[0];
                            int threshold = (int) task.getArguments()[1];
                            Comparison comp = (Comparison) task.getArguments()[2];
                            int clientZone = (int) task.getArguments()[3];
                            
                            long start = System.currentTimeMillis();
                            int value = calculateNumberofCountries(cityCount, threshold, comp);
                            long executionTime = System.currentTimeMillis() - start;

                            QueryResult result = new QueryResult(value, executionTime, 0, serverZone);
                            task.getFuture().complete(result);
                            break;
                        }
                        case "getNumberOfCountriesMM": {
                            int cityCount = (int) task.getArguments()[0];
                            int minPopulation = (int) task.getArguments()[1];
                            int maxPopulation = (int) task.getArguments()[2];
                            int clientZone = (int) task.getArguments()[3];

                            long start = System.currentTimeMillis();
                            int value = calculateNumberofCountriesMM(cityCount, minPopulation, maxPopulation);
                            long executionTime = System.currentTimeMillis() - start;

                            QueryResult result = new QueryResult(value, executionTime, 0, serverZone);
                            task.getFuture().complete(result);
                            break;
                        }
                    }
                    
                } catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    };

    //
    Thread worker = new Thread(work);

  public Server(){
        worker.start();
    }

    private int serverZone = -1;
    //--------------
    // Helper method
    //--------------

    public List<String[]> readCsv(){
        // Helper method for reading .csv file and turning it to an array.  
        // Storing .csv file content on a ArrayList.

        List<String[]> dataset = new ArrayList<>();
        try {
            // Temporary path for reading dataset.
            BufferedReader br = new BufferedReader(new FileReader("src/main/java/com/ass1/Resources/exercise_1_dataset.csv"));
            br.readLine(); //Skip header.
            String line;

            // Loop that copies each Row of the .csv file and saving each line on the ArrayList untill it reaches the end.
            while((line = br.readLine()) != null){
                String[] values = line.split(";");
                dataset.add(values);
            }
            br.close();
        } catch(Exception e){
            e.printStackTrace();
        }

        return dataset;
    }


    // ---------------
    // Remote methods
    // ---------------
    
    @Override
    public QueryResult getPopulationOfCountry(String countryName, int clientZone) throws RemoteException { 
        Task task = new Task("getPopulationOfCountry", new Object[]{countryName, clientZone}); //Creates a new task when called 

        try{ // Adds request to FIFO queue
            taskQueue.put(task); 
            return task.getFuture().get(); // Waits untill worker completes this task.
        } 
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RemoteException("Request was interrupted.", e);
        } 
        catch(ExecutionException a){
            throw new RemoteException("Task execution failed", a);
        }
    }

    @Override
    public QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone) throws RemoteException { 
        Task task = new Task("getNumberOfCities", new Object[]{countryName, threshold, comp, clientZone});

        try{
            taskQueue.put(task);
            return task.getFuture().get();
        } 
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RemoteException("Request was interrupted.", e);
        } 
        catch(ExecutionException a){
            throw new RemoteException("Task execution failed", a);
        }
    }

     @Override
    public QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone) throws RemoteException { 
        Task task = new Task("getNumberOfCountries", new Object[]{cityCount, threshold, comp, clientZone});

        try{
            taskQueue.put(task);
            return task.getFuture().get();
        } 
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RemoteException("Request was interrupted.", e);
        } 
        catch(ExecutionException a){
            throw new RemoteException("Task execution failed", a);
        }
    }

     @Override
    public QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone) throws RemoteException { 
        Task task = new Task("getNumberOfCountriesMM", new Object[]{cityCount, minPopulation, maxPopulation, clientZone});

        try{
            taskQueue.put(task);
            return task.getFuture().get();
        } 
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RemoteException("Request was interrupted.", e);
        } 
        catch(ExecutionException a){
            throw new RemoteException("Task execution failed", a);
        }
    }
    
    


    //----------------
    // Internal logic
    //----------------
    
     
    private int calculatePopulationofCountry(String countryName) {
        List<String[]> data = readCsv(); 
        int population = 0;

        //Loop through dataset and matching countryName with row. 
        //if it matches the population on index 4 is added to population total.
        for(String[] row : data){
            if(countryName.equals(row[3])){
                population += Integer.parseInt(row[4]);
            }
        }
        return population;
    }

    private int calculateNumberofCities(String countryName, int threshold, Comparison comp) {
        List<String[]> data = readCsv();
        int totalCities = 0;
        

        //Loop through data set and matching countryName with row.
        //If there's a match, we compare population to threshold based on the selected comp "min" or "max".
        //If it meets those requirements totalCities are incremented by 1.
        for(String[] row : data){
            int population = Integer.parseInt(row[4]);

            if(countryName.equals(row[3])){
                if(comp == Comparison.MIN){
                    if(population >= threshold){
                    totalCities += 1;
                    }

                } else if(comp == Comparison.MAX){
                    if(population <= threshold){
                        totalCities += 1;
                    }
                }
            }
        }

        return totalCities;
    }
    private int calculateNumberofCountries(int citycount, int threshold, Comparison comp) {
        
        //Method is suppoused to return the amount of countries, that has more or equal to CityCount. 
        //The threshold for how large each city is based on comp and threshold.
        //Example: Cities are, less or equal to 200,000.
       
        List<String[]> data = readCsv();
        int numberOfCountries = 0;

        //A country can have more cities that meet the requirements of a city.
        //We're using a hashmap to store: CountryName and CityCount that are qualified.
        HashMap<String, Integer> countryAndCitycount = new HashMap<>(); 

        //Loop through dataset
        for(String[] row : data){
            String countryName = row[3];
            int population = Integer.parseInt(row[4]);

            //Skip rows in the arrayList that doesn't have names/blank.
            if(countryName.isBlank()){
                continue;
            }

            //Comparing rows with comp and threshold. 
            //If the requirements are met, countryName and 1 is added into the HashMap.
            //If the countryName already exist in the HashMap, merge allows us to merge countryNames and add 1 so we avoid duplicates.
            if(comp == Comparison.MIN){
                if(population >= threshold){
                    countryAndCitycount.merge(countryName, 1, Integer::sum);
                }
            } else if(comp == Comparison.MAX){
                if(population <= threshold){
                    countryAndCitycount.merge(countryName, 1, Integer::sum);
                }
            }
        }

        //Loop through HashMap to find countries that have cities that meet the required citycount.
        for(int countries : countryAndCitycount.values()){
            if(countries >= citycount){
                numberOfCountries += 1;
            }
        }

        return numberOfCountries;
    }
    private int calculateNumberofCountriesMM(int citycount, int minPopulation, int maxPopulation) {
        //This method uses the same logic as before, except population is based on minimum- and maxiumum population.
        //Qualified cities are now between min and max.

        List<String[]> data = readCsv();
        int numberOfCountries = 0;
        HashMap<String, Integer> countryAndCitycount = new HashMap<>();

        for(String[] row : data){
            String countryName = row[3];
            int population = Integer.parseInt(row[4]);
            if(countryName.isBlank()){
                continue;
            }

            if(population >= minPopulation && population <= maxPopulation){
                countryAndCitycount.merge(countryName, 1, Integer::sum);
            } 
        }

        for(int countries : countryAndCitycount.values()){
            if(countries >= citycount){
                numberOfCountries += 1;
            }
        }

        return numberOfCountries;
    }

    @Override
    public int getCurrentWorkload() {
        return 0;
    }


    public static void main(String[] args) throws RemoteException, InterruptedException{
        // Simple local testing for methods. Based on examples on exercise_1 document.
        
        Server server = new Server();
        QueryResult r1 = server.getPopulationOfCountry("Norway", 0 );
        QueryResult r2 = server.getNumberOfCities("Norway", 100000, Comparison.MIN, 1);
        QueryResult r3 = server.getNumberOfCountries(2, 5000000, Comparison.MIN, 1);
        QueryResult r4 = server.getNumberOfCountriesMM(30, 100000, 800000, 1);

        System.out.println(r1);
        System.out.println(r2);
        System.out.println(r3);
        System.out.println(r4);
    }
}