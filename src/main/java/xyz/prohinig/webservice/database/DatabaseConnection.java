package xyz.prohinig.webservice.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    private static final String DATABASE_URL = "jdbc:postgresql://localhost/christianprohinig";

    private static final String USER_PROPERTY_KEY = "christianprohinig";

    private static final String USER = "christianprohinig";

    public Connection getConnection() {
        Properties properties = new Properties();
        properties.setProperty(USER_PROPERTY_KEY, USER);

        try {
            return DriverManager.getConnection(DATABASE_URL, properties);
        } catch (SQLException exception) {
            return null;
        }
    }
}
