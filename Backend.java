import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Backend {
    public static void main(String[] args) {
        // JDBC URL, username, and password of MySQL server
        String jdbcUrl = "jdbc:mysql://localhost:3306/recipe_data";
        String jdbcUser = "root";
        String jdbcPassword = "52097173";

        try {
            // Register MySQL JDBC driver (optional for JDBC 4.0+)
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish a connection
            try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
                System.out.println("Connected to the database!");

                // Perform database operations here

            } catch (SQLException e) {
                System.err.println("Connection failed!");
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found!");
            e.printStackTrace();
        }
    }
}
