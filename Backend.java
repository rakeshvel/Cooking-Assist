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

            // Create table recipes if it doesn't exist already
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

    public static boolean availableRecipe(Connection conn, String recipeName) throws SQLException{
        int recipeId = getRecipeId(conn, recipeName);
        boolean check = true;

        String getIngredientSQL= "SELECT i.ingredient_id, i.ingredient_name, i.quantity, ri.quantity, AS used FROM ingredients i " +
        "JOIN recipe_ingredients ri ON i.ingredient_id = ri.ingredient_id WHERE ri.recipe_id = ?";
        try(PreparedStatement getStmnt = conn.prepareStatement(getIngredientSQL)){
            getStmnt.setInt(1, recipeId);
            try(ResultSet res = getStmnt.executeQuery()){
                while(res.next()){
                    String name = res.getString("ingredient_name");
                    double current = res.getDouble("quantity");
                    double used = res.getDouble("used");

                    if(current<used){
                        System.out.println("Only " +current+ " of '" + name + "' available when " +used+" is required.");
                        check = false;
                    }
                }
            }
        }

        String checkToolSQL = "SELECT t.tool_name, t.is_clean FROM tools t " +
        "JOIN recipe_tools rt ON t.tool_id = rt.tool_id WHERE recipe_id = ?";
        try(PreparedStatement chckStmnt = conn.prepareStatement(checkToolSQL)){
            chckStmnt.setInt(1, recipeId);
            try(ResultSet res = chckStmnt.executeQuery()){
                while(res.next()){
                    String name = res.getString("tool_name");
                    boolean clean = res.getBoolean("is_clean");
                    if(!clean){
                        System.out.println(name + " is not clean.");
                        check = false;
                    }
                }
            }
        }
        return check;
    }

    public static void useRecipe(Connection conn, String recipeName) throws SQLException {
        if(!availableRecipe(conn, recipeName))
            throw new SQLException("Recipe missing ingredients or clean tools");
        int recipeId = getRecipeId(conn, recipeName);

        String getIngredientSQL= "SELECT i.ingredient_id, i.ingredient_name, i.quantity, ri.quantity, AS used FROM ingredients i " +
        "JOIN recipe_ingredients ri ON i.ingredient_id = ri.ingredient_id WHERE ri.recipe_id = ?";
        try(PreparedStatement getStmnt = conn.prepareStatement(getIngredientSQL)){
            getStmnt.setInt(1, recipeId);
            try(ResultSet res = getStmnt.executeQuery()){
                while(res.next()){
                    String name = res.getString("ingredient_name");
                    int id = res.getInt("ingredient_id");
                    double current = res.getDouble("quantity");
                    double used = res.getDouble("used");

                    double update = current-used;

                    String updateIngredientSQL = "UPDATE ingredients SET quantity = ? where ingredient_id = ?";
                    try(PreparedStatement prepStmnt = conn.prepareStatement(updateIngredientSQL)){
                        prepStmnt.setDouble(1, update);
                        prepStmnt.setInt(2, id);
                        prepStmnt.executeUpdate();
                    }

                    System.out.println("Ingredient '" + name + "' has new quantity of " + update);
                }
            }
        }

        String getToolSQL = "UPDATE tools t JOIN recipe_tools rt ON t.tool_id = rt.tool_id " +
        "SET t.is_clean = false WHERE rt.recipe_id = ?";
        try(PreparedStatement toolStmnt = conn.prepareStatement(getToolSQL)){
            toolStmnt.setInt(1, recipeId);
        }
    }

    public static void insertRecipe(Connection conn, String recipeName, String instructions, double prepTime, double cookTime) throws SQLException {
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
        try (PreparedStatement prepStmnt = conn.prepareStatement(insertRecipeSQL)) {
            prepStmnt.setString(1, recipeName);
            prepStmnt.setString(2, instructions);
            prepStmnt.setDouble(3, prepTime);
            prepStmnt.setDouble(4, cookTime);
            prepStmnt.executeUpdate();
        }
    }

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
        try (PreparedStatement insStmnt = conn.prepareStatement(insertRecipeIngredientSQL)) {
            insStmnt.setInt(1, recipeId);
            insStmnt.setInt(2, ingredientId);
            insStmnt.setInt(3, quantity);
            insStmnt.executeUpdate();
            System.out.println("Inserted ingredient '" + ingredientName + "' into recipe '" + recipeName + "'");
        }
    }

    public static void insertRecipeTool(Connection conn, String recipeName, String toolName) throws SQLException {
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
        try (PreparedStatement insStmnt = conn.prepareStatement(insertRecipeToolSQL)) {
            insStmnt.setInt(1, recipeId);
            insStmnt.setInt(2, toolId);
            insStmnt.executeUpdate();
            System.out.println("Inserted tool '" + toolName + "' into recipe '" + recipeName + "'");
        }
    }

    // Helper method to get recipe ID by name
    public static int getRecipeId(Connection conn, String recipeName) throws SQLException {
        String selectRecipeSQL = "SELECT recipe_id FROM recipes WHERE recipe_name = ?";
        try (PreparedStatement slctStmnt = conn.prepareStatement(selectRecipeSQL)) {
            slctStmnt.setString(1, recipeName);
            try (ResultSet res = slctStmnt.executeQuery()) {
                if (res.next()) {
                    return res.getInt("recipe_id");
                } else {
                    throw new SQLException("Recipe not found: " + recipeName);
                }
            }
        }
    }

    // Helper method to get ingredient ID by name
    public static int getIngredientId(Connection conn, String ingredientName) throws SQLException {
        String selectIngredientSQL = "SELECT ingredient_id FROM ingredients WHERE ingredient_name = ?";
        try (PreparedStatement slctStmnt = conn.prepareStatement(selectIngredientSQL)) {
            slctStmnt.setString(1, ingredientName);
            try (ResultSet res = slctStmnt.executeQuery()) {
                if (res.next()) {
                    return res.getInt("ingredient_id");
                } else {
                    throw new SQLException("Ingredient not found: " + ingredientName);
                }
            }
        }
    }

    // Helper method to get tool ID by name
    public static int getToolId(Connection conn, String toolName) throws SQLException {
        String selectToolSQL = "SELECT tool_id FROM tools WHERE tool_name = ?";
        try (PreparedStatement slctStmnt = conn.prepareStatement(selectToolSQL)) {
            slctStmnt.setString(1, toolName);
            try (ResultSet res = slctStmnt.executeQuery()) {
                if (res.next()) {
                    return res.getInt("tool_id");
                } else {
                    throw new SQLException("Tool not found: " + toolName);
                }
            }
        }
    }
    
    public static void deleteRecipe(Connection conn, String recipeName) throws SQLException{
        int recipeID = getRecipeId(conn, recipeName);

        String deleteRecipeIngredientSQL = "DELETE FROM recipe_ingredients WHERE recipe_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteRecipeIngredientSQL)){
            prepStmnt.setInt(1, recipeID);
            prepStmnt.executeUpdate();
        }

        String deleteRecipeToolSQL = "DELETE FROM recipe_tools WHERE recipe_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteRecipeToolSQL)){
            prepStmnt.setInt(1, recipeID);
            prepStmnt.executeUpdate();
        }

        String deleteRecipeSQL = "DELETE FROM recipes WHERE recipe_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteRecipeSQL)){
            prepStmnt.setInt(1, recipeID);
            prepStmnt.executeUpdate();
            System.out.println("Deleted recipe: " + recipeName);
        }
    }

    public static void deleteIngredient(Connection conn, String ingredientName) throws SQLException{
        int ingredientId = getIngredientId(conn, ingredientName);

        String deleteRecipeIngredientSQL = "DELETE FROM recipe_ingredients WHERE ingredient_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteRecipeIngredientSQL)){
            prepStmnt.setInt(1, ingredientId);
            prepStmnt.executeUpdate();
        }

        String deleteIngredientSQL = "DELETE FROM ingredients WHERE ingredient_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteIngredientSQL)){
            prepStmnt.setInt(1, ingredientId);
            prepStmnt.executeUpdate();
            System.out.println("Deleted ingredient: " + ingredientName);
        }
    }

    public static void deleteTool(Connection conn, String toolName) throws SQLException{
        int toolId = getToolId(conn, toolName);

        String deleteRecipeToolSQL = "DELETE FROM recipe_tools WHERE tool_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteRecipeToolSQL)){
            prepStmnt.setInt(1, toolId);
            prepStmnt.executeUpdate();
        }

        String deleteToolSQL = "DELETE FROM tools WHERE tool_id = ?";
        try(PreparedStatement prepStmnt  = conn.prepareStatement(deleteToolSQL)){
            prepStmnt.setInt(1, toolId);
            prepStmnt.executeUpdate();
            System.out.println("Deleted tool: " + toolName);
        }
    }

    public static void deleteIngredientFromRecipe(Connection conn, String recipeName, String ingredientName) throws SQLException{
        int recipeId = getRecipeId(conn, recipeName);
        int ingredientId = getIngredientId(conn, ingredientName);

        String deleteRecipeIngredientSQL = "DELETE FROM recipe_ingredients WHERE recipe_id = ? AND ingredient_id = ?";
        try(PreparedStatement prepStmnt = conn.prepareStatement(deleteRecipeIngredientSQL)){
            prepStmnt.setInt(1, recipeId);
            prepStmnt.setInt(2, ingredientId);
            int rows = prepStmnt.executeUpdate();

            if(rows>0){
                System.out.println("Deleted ingredient '" + ingredientName + "'' from recipe '" + recipeName + "'");
            }else{
                System.out.println("No ingredient with name '" + ingredientName + "'' found in recipe '" + recipeName + "'");
            }
        }
    }

    public static void deleteToolFromRecipe(Connection conn, String recipeName, String toolName) throws SQLException{
        int recipeId = getRecipeId(conn, recipeName);
        int toolId = getIngredientId(conn, toolName);

        String deleteRecipeToolSQL = "DELETE FROM recipe_tools WHERE recipe_id = ? AND tool_id = ?";
        try(PreparedStatement prepStmnt = conn.prepareStatement(deleteRecipeToolSQL)){
            prepStmnt.setInt(1, recipeId);
            prepStmnt.setInt(2, toolId);
            int rows = prepStmnt.executeUpdate();

            if(rows>0){
                System.out.println("Deleted tool '" + toolName + "'' from recipe '" + recipeName + "'");
            }else{
                System.out.println("No tool with name '" + toolId + "'' found in recipe '" + recipeName + "'");
            }
        }
    }

    public static void updateRecipe(Connection conn, String name, String instructions, double prepTime, double cookTime) throws SQLException{
        int recipeId = getRecipeId(conn, name);

        String updateRecipeSQL = "UPDATE recipes SET recipe_name = ?, instructions = ?, prep_time = ?, cook_time = ?, total_time = ? WHERE recipe_id = ?";
        try(PreparedStatement prepStmnt = conn.prepareStatement(updateRecipeSQL)){
            prepStmnt.setString(1, name);
            prepStmnt.setString(2, instructions);
            prepStmnt.setDouble(3, prepTime);
            prepStmnt.setDouble(4, cookTime);
            prepStmnt.setInt(5, recipeId);
        }
    }

    public static void updateIngredient(Connection conn, String name, double quantity, double cost) throws SQLException{
        int ingredientId = getIngredientId(conn, name);

        String updateIngredientSQL = "UPDATE ingredients SET ingredient_name = ?, quantity = ?, cost = ? WHERE ingredient_id = ?";
        try(PreparedStatement prepStmnt = conn.prepareStatement(updateIngredientSQL)){
            prepStmnt.setString(1, name);
            prepStmnt.setDouble(2, quantity);
            prepStmnt.setDouble(3, cost);
            prepStmnt.setInt(4, ingredientId);
        }
    }

    public static void updateTool(Connection conn, String name, boolean isClean) throws SQLException{
        int toolId = getToolId(conn, name);

        String updateTool = "UPDATE tools SET tool_name = ?, is_clean = ? WHERE tool_id = ?";
        try(PreparedStatement prepStmnt = conn.prepareStatement(updateTool)){
            prepStmnt.setString(1, name);
            prepStmnt.setBoolean(2, isClean);
            prepStmnt.setInt(3, toolId);
        }
    }

    public static void selectRecipesByName(Connection conn) throws SQLException{
        String recipesSQL = "SELECT recipe_name, instructions, prep_time, cook_time, total_time FROM recipes ORDER BY recipe_name ASC";
        
        try(PreparedStatement prepStmnt = conn.prepareStatement(recipesSQL); ResultSet res = prepStmnt.executeQuery()){
            System.out.println("Recipes sorted by name:");
            
            while (res.next()) {
                String name = res.getString("recipe_name");
                String instructions = res.getString("instructions");
                double prepTime = res.getDouble("prep_time");
                double cookTime = res.getDouble("cook_time");
                double totalTime = res.getDouble("total_time");
    
                System.out.println("Name: " + name);
                System.out.println("Instructions: " + instructions);
                System.out.println("Prep Time: " + prepTime + " min");
                System.out.println("Cook Time: " + cookTime + " min");
                System.out.println("Total Time: " + totalTime + " min");
            }
        }
    }

    public static void selectRecipesByPrice(Connection conn) throws SQLException{
        String recipesSQL = "SELECT r.recipe_name, r.instructions, r.prep_time, r.cook_time, r.total_time, " +
                            "SUM(i.cost * ri.quantity) AS total_cost FROM recipes r " +
                            "JOIN recipe_ingredients ri ON r.recipe_id = ri.recipe_id " +
                            "JOIN ingredients i ON ri.ingredient_id = i.ingredient_id " +
                            "GROUP BY r.recipe_name, r.instructions, r.prep_time, r.cook_time, r.total_time ORDER BY total_cost ASC";
        
        try(PreparedStatement prepStmnt = conn.prepareStatement(recipesSQL); ResultSet res = prepStmnt.executeQuery()){
            System.out.println("Recipes sorted by price:");
            
            while (res.next()) {
                String name = res.getString("recipe_name");
                String instructions = res.getString("instructions");
                double prepTime = res.getDouble("prep_time");
                double cookTime = res.getDouble("cook_time");
                double totalTime = res.getDouble("total_time");
                double totalCost = res.getDouble("total_cost");
    
                System.out.println("Name: " + name);
                System.out.println("Instructions: " + instructions);
                System.out.println("Prep Time: " + prepTime + " min");
                System.out.println("Cook Time: " + cookTime + " min");
                System.out.println("Total Time: " + totalTime + " min");
                System.out.println("Total Cost: $" + totalCost);
            }
        }
    }

    public static void selectRecipesMakeableByName(Connection conn) throws SQLException {
        String recipesSQL = "SELECT r.recipe_name, r.instructions, r.prep_time, r.cook_time, r.total_time FROM recipes r " +
                            "WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients ri JOIN ingredients i " +
                            "ON ri.ingredient_id = i.ingredient_id WHERE ri.recipe_id = r.recipe_id AND i.quantity < ri.quantity) " +
                            "AND NOT EXISTS (SELECT 1 FROM recipe_tools rt JOIN tools t ON rt.tool_id = t.tool_id " +
                            "WHERE rt.recipe_id = r.recipe_id AND t.is_clean = false) ORDER BY r.recipe_name ASC";
        
        try(PreparedStatement prepStmnt = conn.prepareStatement(recipesSQL); ResultSet res = prepStmnt.executeQuery()){
            System.out.println("Recipes that can currently be made sorted by name:");
            
            while (res.next()) {
                String name = res.getString("recipe_name");
                String instructions = res.getString("instructions");
                double prepTime = res.getDouble("prep_time");
                double cookTime = res.getDouble("cook_time");
                double totalTime = res.getDouble("total_time");
    
                System.out.println("Name: " + name);
                System.out.println("Instructions: " + instructions);
                System.out.println("Prep Time: " + prepTime + " min");
                System.out.println("Cook Time: " + cookTime + " min");
                System.out.println("Total Time: " + totalTime + " min");
            }
        }
    }

    //TODO
    public static void selectRecipesMakeableByPrice(Connection conn) throws SQLException{

    }

    public static void selectIngredients(Connection conn) throws SQLException{
        String ingredientsSQL = "SELECT ingredient_name, quantity, cost FROM ingredients ORDER BY ingredient_name ASC";

        try(PreparedStatement prepStmnt = conn.prepareStatement(ingredientsSQL); ResultSet res = prepStmnt.executeQuery()){
            System.out.println("Ingredients sorted by name:");
            
            while (res.next()) {
                String name = res.getString("ingredient_name");
                String quantity = res.getString("quantity");
                String cost = res.getString("cost");
    
                System.out.println("Name: " + name);
                System.out.println("Quantity: " + quantity);
                System.out.println("Cost: $" + cost);
            }
        }
    }

    public static void selectIngredientsMin(Connection conn, double minQ) throws SQLException{
        String ingredientsSQL = "SELECT ingredient_name, quantity, cost FROM ingredients WHERE quantity >= ? ORDER BY quantity DESC";

        try(PreparedStatement prepStmnt = conn.prepareStatement(ingredientsSQL)){
            prepStmnt.setDouble(1, minQ);

            try(ResultSet res = prepStmnt.executeQuery()){
                System.out.println("Ingredients with quantity greater than or equal to " + minQ + ":");
                
                while (res.next()) {
                    String name = res.getString("ingredient_name");
                    String quantity = res.getString("quantity");
                    String cost = res.getString("cost");
        
                    System.out.println("Name: " + name);
                    System.out.println("Quantity: " + quantity);
                    System.out.println("Cost: $" + cost);
                }
            }
        }
    }

    public static void selectIngredientsCheap(Connection conn, double maxP) throws SQLException{
        String ingredientsSQL = "SELECT ingredient_name, quantity, cost FROM ingredients WHERE cost <= ? ORDER BY cost ASC";

        try(PreparedStatement prepStmnt = conn.prepareStatement(ingredientsSQL)){
            prepStmnt.setDouble(1, maxP);

            try(ResultSet res = prepStmnt.executeQuery()){
                System.out.println("Ingredients with cost below $" + maxP + ":");
                
                while (res.next()) {
                    String name = res.getString("ingredient_name");
                    String quantity = res.getString("quantity");
                    String cost = res.getString("cost");
        
                    System.out.println("Name: " + name);
                    System.out.println("Quantity: " + quantity);
                    System.out.println("Cost: $" + cost);
                }
            }
        }
    }

    public static void selectTools(Connection conn) throws SQLException{
        String toolsSQL = "SELECT tool_name, is_clean FROM tools ORDER BY tool_name ASC";

        try(PreparedStatement prepStmnt = conn.prepareStatement(toolsSQL); ResultSet res = prepStmnt.executeQuery()){
            System.out.println("Tools sorted by name:");
            
            while (res.next()) {
                String name = res.getString("tool_name");
                boolean clean = res.getBoolean("is_clean");
    
                System.out.println("Name: " + name);
                System.out.println("Clean: " + clean);
            }
        }
    }

    public static void selectToolsClean(Connection conn) throws SQLException {
        String toolsSQL = "SELECT tool_name, is_clean FROM tools WHERE is_clean = true ORDER BY tool_name ASC";

        try(PreparedStatement prepStmnt = conn.prepareStatement(toolsSQL); ResultSet res = prepStmnt.executeQuery()){
            System.out.println("Clean tools sorted by name:");

            while (res.next()) {
                String name = res.getString("tool_name");
                System.out.println("Name: " + name);
            }
        }
    }
}