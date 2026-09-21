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

                //Outer try/catch handles the worker thread being interrupted
                //Inner try/catch handles error while executing a task, so waiting RMI-call doesn't wait forever.

                try{
                    Task task = taskQueue.take();
                    logQueueSize();
                    long waitingTime = System.currentTimeMillis() - task.getEntryTime();

                    try {
                        switch(task.getMethod()){ // Select which task/query the worker should execute. 
                            case "getPopulationOfCountry": {
                                String countryName = (String) task.getArguments()[0];
                                int clientZone = (int) task.getArguments()[1];
                                
                                long start = System.currentTimeMillis(); //Calculating execution time based on before and after running calculation.
                                int value = calculatePopulationofCountry(countryName);    
                                long executionTime =  System.currentTimeMillis() - start;
                                
                                QueryResult result = new QueryResult(value, executionTime, waitingTime, serverZone);  
                                task.getFuture().complete(result); //Gives the task its result so that the RMI-call that is waiting on getFuture().get() can continue.     
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

                                QueryResult result = new QueryResult(value, executionTime, waitingTime, serverZone);
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

                                QueryResult result = new QueryResult(value, executionTime, waitingTime, serverZone);
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

                                QueryResult result = new QueryResult(value, executionTime, waitingTime, serverZone);
                                task.getFuture().complete(result);
                                break;
                            }
                        }
                        
                    } catch(RuntimeException e){
                        task.getFuture().completeExceptionally(e);
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


    private void logQueueSize(){
        long timestamp = System.currentTimeMillis();
        int queueSize = taskQueue.size();

        System.out.println(timestamp + "," + queueSize);
    }


    // ---------------
    // Remote methods
    // ---------------
    
    @Override
    public QueryResult getPopulationOfCountry(String countryName, int clientZone) throws RemoteException { 
        Task task = new Task("getPopulationOfCountry", new Object[]{countryName, clientZone}); //Creates a new task when called 

        try{ // Adds request to FIFO queue
            task.setEntryTime(System.currentTimeMillis()); // Saves the time when the task enters the queue, so waiting time can be calculated later
            taskQueue.put(task);
            logQueueSize(); 
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
            task.setEntryTime(System.currentTimeMillis());
            taskQueue.put(task);
            logQueueSize();
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
            task.setEntryTime(System.currentTimeMillis());
            taskQueue.put(task);
            logQueueSize();
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
            task.setEntryTime(System.currentTimeMillis());
            taskQueue.put(task);
            logQueueSize();
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
        return taskQueue.size();
    }


    public static void main(String[] args) throws RemoteException, InterruptedException{
        // Testing workload method and waitingTime.
        
        Server server = new Server();
        Thread t1 = new Thread(() -> {
            try {
                QueryResult r = server.getPopulationOfCountry("Norway", 0);
                System.out.println("T1: " + r);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                QueryResult r = server.getNumberOfCities(
                    "Norway", 100000, Comparison.MIN, 1
                );
                System.out.println("T2: " + r);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                QueryResult r = server.getNumberOfCountries(
                    2, 5000000, Comparison.MIN, 1
                );
                System.out.println("T3: " + r);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();
        t3.start();

        try {
            Thread.sleep(50);
            System.out.println("Current workload: " + server.getCurrentWorkload());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}