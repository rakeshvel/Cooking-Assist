import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Backend {
    // JDBC URL, username, and password of MySQL server
    private static final String URL = "jdbc:mysql://localhost:3306/recipe_data";
    private static final String USER = "root";
    private static final String PASS = "52097173";

    public static void main(String[] args) {
        Connection conn = null;

        try {
            // Register MySQL JDBC driver (optional for JDBC 4.0+)
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish a conn
            try {
                conn = DriverManager.getConnection(URL, USER, PASS);
                System.out.println("Connected to the database!");

                setupDatabase(conn);
                // Perform database operations here
                //TODO:

            } catch (SQLException e) {
                System.err.println("Connection failed!");
                e.printStackTrace();
            }
        } catch (ClassNotFoundException error) {
            System.err.println("MySQL JDBC driver not found!");
            error.printStackTrace();
        }
    }

    public static void setupDatabase(Connection conn) {
        Statement statement = null;

        try {
            statement = conn.createStatement();

            // Create table recipes if not exists
            String newRecipeTableSQL = "CREATE TABLE IF NOT EXISTS recipes (" +
                    "recipe_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "recipe_name VARCHAR(255) NOT NULL," +
                    "instructions TEXT," +
                    "prepTime DOUBLE NOT NULL" +
                    "cookTime DOUBLE NOT NULL" +
                    "totalTime DOUBLE AS (prepTime + cookTime) STORED" +
                    ")";
            statement.executeUpdate(newRecipeTableSQL);

            String newIngredientTableSQL = "CREATE TABLE IF NOT EXISTS ingredients (" +
                    "ingredient_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "ingredient_name VARCHAR(255) NOT NULL," +
                    "quantity INT" +
                    "cost DOUBLE," +
                    ")";
            statement.executeUpdate(newIngredientTableSQL);
            
            String linkRecipeIngredientSQL = "CREATE TABLE IF NOT EXISTS recipe_ingredients (" +
                    "recipe_id INT," +
                    "ingredient_id INT," +
                    "quantity INT," +
                    "PRIMARY KEY (recipe_id, ingredient_id)," +
                    "FOREIGN KEY (recipe_id) REFERENCES recipes(recipe_id) ON DELETE CASCADE," +
                    "FOREIGN KEY (ingredient_id) REFERENCES ingredients(ingredient_id) ON DELETE CASCADE" +
                    ")";
            statement.executeUpdate(linkRecipeIngredientSQL);
            
            String newToolTableSQL = "CREATE TABLE IF NOT EXISTS tools (" +
                    "tool_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "tool_name VARCHAR(255) NOT NULL," +
                    "is_clean BOOLEAN DEFAULT TRUE," +
                    ")";
            statement.executeUpdate(newToolTableSQL);

            String linkRecipeToolSQL = "CREATE TABLE IF NOT EXISTS recipe_tools (" +
                    "recipe_id INT," +
                    "tool_id INT," +
                    "PRIMARY KEY (recipe_id, tool_id)," +
                    "FOREIGN KEY (recipe_id) REFERENCES recipes(recipe_id) ON DELETE CASCADE," +
                    "FOREIGN KEY (tool_id) REFERENCES tools(tool_id) ON DELETE CASCADE" +
                    ")";
            statement.executeUpdate(linkRecipeToolSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // Closing resources
            try {
                if (statement != null) statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void selectData(Connection conn) {
        
    }

    public static int insertRecipe(Connection conn, String recipeName, String instructions, double prepTime, double cookTime) throws SQLException {
        String checkRecipeSQL = "SELECT recipe_id FROM recipes WHERE recipe_name = ?";
        try (PreparedStatement checkStmnt = conn.prepareStatement(checkRecipeSQL)) {
            checkStmnt.setString(1, recipeName);
            try (ResultSet res = checkStmnt.executeQuery()) {
                if (res.next()) {
                    throw new SQLException("Recipe already exists: " + recipeName);
                }
            }
        }

        String insertRecipeSQL = "INSERT INTO recipes (recipe_name, instructions, prepTime, cookTime) VALUES (?, ?, ?, ?)";
        try (PreparedStatement prepStmnt = conn.prepareStatement(insertRecipeSQL, Statement.RETURN_GENERATED_KEYS)) {
            prepStmnt.setString(1, recipeName);
            prepStmnt.setString(2, instructions);
            prepStmnt.setDouble(3, prepTime);
            prepStmnt.setDouble(4, cookTime);
            prepStmnt.executeUpdate();

            try (ResultSet generatedKeys = prepStmnt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating recipe failed, no ID obtained.");
                }
            }
        }
    }

    // Method to insert an ingredient without returning the ID
    public static void insertIngredient(Connection conn, String ingredientName, int quantity, double cost) throws SQLException {
        String checkIngredientSQL = "SELECT ingredient_id FROM ingredients WHERE ingredient_name = ?";
        try (PreparedStatement checkStmnt = conn.prepareStatement(checkIngredientSQL)) {
            checkStmnt.setString(1, ingredientName);
            try (ResultSet res = checkStmnt.executeQuery()) {
                if (res.next()) {
                    throw new SQLException("Ingredient already exists: " + ingredientName);
                }
            }
        }
        
        String insertIngredientSQL = "INSERT INTO ingredients (ingredient_name, quantity, cost) VALUES (?, ?, ?)";
        try (PreparedStatement prepStmnt = conn.prepareStatement(insertIngredientSQL)) {
            prepStmnt.setString(1, ingredientName);
            prepStmnt.setInt(2, quantity);
            prepStmnt.setDouble(3, cost);
            prepStmnt.executeUpdate();
        }
    }

    // Method to insert a tool without returning the ID
    public static void insertTool(Connection conn, String toolName, boolean isClean) throws SQLException {
        String checkToolSQL = "SELECT tool_id FROM tools WHERE tool_name = ?";
        try (PreparedStatement checkStmnt = conn.prepareStatement(checkToolSQL)) {
            checkStmnt.setString(1, toolName);
            try (ResultSet res = checkStmnt.executeQuery()) {
                if (res.next()) {
                    throw new SQLException("Tool already exists: " + toolName);
                }
            }
        }
        
        String insertToolSQL = "INSERT INTO tools (tool_name, is_clean) VALUES (?, ?)";
        try (PreparedStatement prepStmnt = conn.prepareStatement(insertToolSQL)) {
            prepStmnt.setString(1, toolName);
            prepStmnt.setBoolean(2, isClean);
            prepStmnt.executeUpdate();
        }
    }

    public static void insertRecipeIngredient(Connection conn, String recipeName, String ingredientName, int quantity) throws SQLException {
        int recipeId = getRecipeId(conn, recipeName);
        int ingredientId = getIngredientId(conn, ingredientName);

        String checkRecipeIngredientSQL = "SELECT * FROM recipe_ingredients WHERE recipe_id = ? AND ingredient_id = ?";
        try (PreparedStatement checkStmnt = conn.prepareStatement(checkRecipeIngredientSQL)) {
            checkStmnt.setInt(1, recipeId);
            checkStmnt.setInt(2, ingredientId);
            try (ResultSet checkRes = checkStmnt.executeQuery()) {
                if (checkRes.next()) {
                    throw new SQLException("Ingredient already exists for this recipe: " + ingredientName);
                }
            }
        }

        String insertRecipeIngredientSQL = "INSERT INTO recipe_ingredients (recipe_id, ingredient_id, quantity) VALUES (?, ?, ?)";
        try (PreparedStatement insertStatement = conn.prepareStatement(insertRecipeIngredientSQL)) {
            insertStatement.setInt(1, recipeId);
            insertStatement.setInt(2, ingredientId);
            insertStatement.setInt(3, quantity);
            insertStatement.executeUpdate();
        }
    }

    // Method to insert a recipe-tool relationship by recipe name
    public static void insertRecipeToolByName(Connection conn, String recipeName, String toolName) throws SQLException {
        int recipeId = getRecipeId(conn, recipeName);
        int toolId = getToolId(conn, toolName);

        String checkRecipeToolSQL = "SELECT * FROM recipe_tools WHERE recipe_id = ? AND tool_id = ?";
        try (PreparedStatement checkStmnt = conn.prepareStatement(checkRecipeToolSQL)) {
            checkStmnt.setInt(1, recipeId);
            checkStmnt.setInt(2, toolId);
            try (ResultSet checkRes = checkStmnt.executeQuery()) {
                if (checkRes.next()) {
                    throw new SQLException("Tool already exists for this recipe: " + toolName);
                }
            }
        }

        String insertRecipeToolSQL = "INSERT INTO recipe_tools (recipe_id, tool_id) VALUES (?, ?)";
        try (PreparedStatement insertStatement = conn.prepareStatement(insertRecipeToolSQL)) {
            insertStatement.setInt(1, recipeId);
            insertStatement.setInt(2, toolId);
            insertStatement.executeUpdate();
        }
    }

    // Helper method to get recipe ID by name
    public static int getRecipeId(Connection conn, String recipeName) throws SQLException {
        String selectRecipeSQL = "SELECT recipe_id FROM recipes WHERE recipe_name = ?";
        try (PreparedStatement selectStatement = conn.prepareStatement(selectRecipeSQL)) {
            selectStatement.setString(1, recipeName);
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("recipe_id");
                } else {
                    throw new SQLException("Recipe not found: " + recipeName);
                }
            }
        }
    }

    // Helper method to get ingredient ID by name
    public static int getIngredientId(Connection conn, String ingredientName) throws SQLException {
        String selectIngredientSQL = "SELECT ingredient_id FROM ingredients WHERE ingredient_name = ?";
        try (PreparedStatement selectStatement = conn.prepareStatement(selectIngredientSQL)) {
            selectStatement.setString(1, ingredientName);
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("ingredient_id");
                } else {
                    throw new SQLException("Ingredient not found: " + ingredientName);
                }
            }
        }
    }

    // Helper method to get tool ID by name
    public static int getToolId(Connection conn, String toolName) throws SQLException {
        String selectToolSQL = "SELECT tool_id FROM tools WHERE tool_name = ?";
        try (PreparedStatement selectStatement = conn.prepareStatement(selectToolSQL)) {
            selectStatement.setString(1, toolName);
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("tool_id");
                } else {
                    throw new SQLException("Tool not found: " + toolName);
                }
            }
        }
    }
    
    public static void deleteData(Connection conn) {
        
    }

    public static void updateData(Connection conn) {
        
    }
}
