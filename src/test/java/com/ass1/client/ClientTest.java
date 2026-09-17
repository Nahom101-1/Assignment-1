package com.ass1.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest{
    @Test
    void readsQueriesFromFile() throws IOException {

        // Arrange
        Client client = new Client();

        // Act
        client.readQueries("src/test/resources/queries-test.txt");

        // Assert
        assertEquals(4, client.getQueries().size());
    }

    @Test
    void parsesPopulationOfCountryCorrectly() throws IOException {
        // Arrange
        Client client = new Client();

        // Act
        client.readQueries("src/test/resources/queries-test.txt");

        // Assert
        Query firstQuery = client.getQueries().get(0);

        // your assertions here
    }
}