package App.utils;

import App.models.Tasks;
import App.models.Users;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class databaseconn {
    private static final String URL = "jdbc:sqlite:ToDoList.db";
    public static Connection getConnection() throws SQLException, ClassNotFoundException { return DriverManager.getConnection(URL); }

    public static void createTable() {

        String createTasksTableSQL = "CREATE TABLE IF NOT EXISTS tasks ("
                + "id INTEGER, "
                + "user_id INTEGER, "
                + "title TEXT NOT NULL, "
                + "description TEXT, "
                + "due_date DATE, "
                + "type TEXT, "
                + "status TEXT DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'COMPLETED', 'OVERDUE')), "
                + "priority TEXT DEFAULT 'LOW' CHECK(priority IN ('LOW', 'MEDIUM', 'HIGH')), "
                + "dependency INTEGER DEFAULT 0, "
                + "is_recurring CHAR(1) DEFAULT 'N', "
                + "recurrence_type TEXT DEFAULT 'NEVER', "
                + "recurrence_end_date DATE, "
                + "embedding BLOB, "
                + "PRIMARY KEY (user_id, id), "
                + "FOREIGN KEY (user_id) REFERENCES users(id)"
                + ");";

        String createUsersTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "email TEXT NOT NULL UNIQUE, "
                + "password TEXT NOT NULL"
                + ");";


        try (Connection conn = getConnection(); PreparedStatement stmt1 = conn.prepareStatement(createUsersTableSQL); PreparedStatement stmt2 = conn.prepareStatement(createTasksTableSQL)) {
            stmt1.executeUpdate();
            stmt2.executeUpdate();
            System.out.println("Tables 'users' and 'tasks' created successfully.");
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error creating table: " + e.getMessage());
        }
    }

    public int getNextTaskIdForUser(int user_id) {
        String query = "SELECT MAX(id) AS max_id FROM tasks WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, user_id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int maxId = rs.getInt("max_id");
                    return maxId + 1;
                } else {
                    return 1;
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Error fetching next task ID: " + e.getMessage());
            return -1;
        }
    }

    public void insertUser(String email, String password) {
        String insertSQL = "INSERT INTO users (email, password) "
                + "VALUES (?, ?);";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
            stmt.setString(1, email);
            stmt.setString(2, password);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("User \"" + email + "\" inserted successfully!");
            } else {
                System.out.println("Task insertion failed!");
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error inserting task: " + e.getMessage());
        }
    }

    public Users fetchUserByEmail(String email) {
        String selectSQL = "SELECT * FROM users WHERE email = ?;";
        Users user = null;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int user_id = rs.getInt("id");
                    String password = rs.getString("password");

                    user = new Users(user_id, email, password);
                } else {
                    System.out.println("User with email " + email + " not found.");
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error fetching user: " + e.getMessage());
        }

        return user;
    }

    public String fetchEmailByID(int id) {
        String selectSQL = "SELECT email FROM users WHERE id = ?;";
        String email = null;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    email = rs.getString("email");

                } else {
                    System.out.println("User with ID " + id + " not found.");
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error fetching task: " + e.getMessage());
        }

        return email;
    }

    public static byte[] toByteArray(float[] floatArray) {
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float value : floatArray) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public void insertTask(int user_id, String title, String description, Date due_date, String type, String status, Tasks.Priority priority, int dependency, char is_recurring, Tasks.Recurrence_type recurrence_type, Date recurrence_end_date) {
        int nextTaskId = getNextTaskIdForUser(user_id);
        if (nextTaskId == -1) {
            System.out.println("Failed to add task: Could not generate task ID.");
        } else {
            float[] combinedEmbedding;
            try {
                combinedEmbedding = EmbeddingGenerator.getCombinedEmbedding(title, description);
                System.out.println("Original embedding length: " + combinedEmbedding.length);

                // Debug print first few values of the embedding
                System.out.println("First few embedding values: ");
                for (int i = 0; i < Math.min(5, combinedEmbedding.length); i++) {
                    System.out.println("Value " + i + ": " + combinedEmbedding[i]);
                }

                byte[] embeddingBytes = toByteArray(combinedEmbedding);
                System.out.println("Converted embedding bytes length: " + embeddingBytes.length);

                String insertSQL = "INSERT INTO tasks (id, user_id, title, description, due_date, type, status, priority, dependency, is_recurring, recurrence_type, recurrence_end_date, embedding) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(insertSQL)) {

                    stmt.setInt(1, nextTaskId);
                    stmt.setInt(2, user_id);
                    stmt.setString(3, title);
                    stmt.setString(4, description);
                    stmt.setDate(5, due_date);
                    stmt.setString(6, type);
                    stmt.setString(7, status);
                    stmt.setString(8, priority.name());
                    stmt.setInt(9, dependency);
                    stmt.setString(10, String.valueOf(is_recurring));
                    stmt.setString(11, recurrence_type.name());
                    stmt.setDate(12, recurrence_end_date);

                    // Use setBytes for BLOB data
                    stmt.setBytes(13, embeddingBytes);

                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected > 0) {
                        System.out.println("Task \"" + title + "\" inserted successfully!");

                        // Verify the stored embedding
                        verifyStoredEmbedding(conn, nextTaskId, user_id, embeddingBytes.length);
                    } else {
                        System.out.println("Task insertion failed!");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in task insertion: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void verifyStoredEmbedding(Connection conn, int taskId, int userId, int expectedLength) throws SQLException {
        String verifySQL = "SELECT embedding FROM tasks WHERE id = ? AND user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(verifySQL)) {
            stmt.setInt(1, taskId);
            stmt.setInt(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] storedEmbedding = rs.getBytes("embedding");
                    System.out.println("Verification - Stored embedding length: " +
                            (storedEmbedding != null ? storedEmbedding.length : "null"));

                    if (storedEmbedding == null || storedEmbedding.length != expectedLength) {
                        System.err.println("WARNING: Stored embedding length mismatch! Expected: " +
                                expectedLength + ", Actual: " +
                                (storedEmbedding != null ? storedEmbedding.length : "null"));
                    }
                }
            }
        }
    }

    public void updateTask(String title, String description, Date due_date, String type, String status, Tasks.Priority priority, int dependency, char is_recurring, Tasks.Recurrence_type recurrence_type, Date recurrence_end_date, int taskId, int user_id) {
        float[] combinedEmbedding;
        try {
            combinedEmbedding = EmbeddingGenerator.getCombinedEmbedding(title, description);
        } catch (Exception e) {
            System.err.println("Error generating embedding: " + e.getMessage());
            return; // Exit the method if embedding generation fails
        }

        byte[] embeddingBytes = toByteArray(combinedEmbedding);

        String updateSQL = "UPDATE tasks SET title = ?, description = ?, due_date = ?, type = ?, status = ?, priority = ?, dependency = ?, is_recurring = ?, recurrence_type = ?, recurrence_end_date = ?, embedding = ? WHERE id = ? AND user_id = ?;";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setDate(3, due_date);
            stmt.setString(4, type);
            stmt.setString(5, status);
            stmt.setString(6, priority.name());
            stmt.setInt(7, dependency);
            stmt.setString(8, String.valueOf(is_recurring));
            stmt.setString(9, recurrence_type.name());
            stmt.setDate(10, recurrence_end_date);
            stmt.setBytes(11, embeddingBytes);
            stmt.setInt(12, taskId);
            stmt.setInt(13, user_id);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                if (status.equalsIgnoreCase("PENDING")) {
                    System.out.println("Task \"" + title + "\"updated successfully!");
                } else {
                    System.out.println("Task \"" + title + "\" has been checked off successfully!");
                }
            } else {
                System.out.println("Task update failed! Task with ID " + taskId + " not found.");
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error updating task: " + e.getMessage());
        }
    }

    public Tasks fetchTaskById(int user_id, int taskId) {
        String selectSQL = "SELECT * FROM tasks WHERE user_id = ? AND id = ?;";
        Tasks task = null;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setInt(1, user_id);
            stmt.setInt(2, taskId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("title");
                    String description = rs.getString("description");
                    Date due_date = rs.getDate("due_date");
                    String type = rs.getString("type");
                    String status = rs.getString("status");
                    Tasks.Priority priority = Tasks.Priority.valueOf(rs.getString("priority")); // Convert string to enum
                    int dependency = rs.getInt("dependency");
                    char is_recurring = rs.getString("is_recurring").charAt(0);
                    Tasks.Recurrence_type recurrence_type = Tasks.Recurrence_type.valueOf(rs.getString("recurrence_type")); // Convert string to enum
                    Date recurrence_end_date = rs.getDate("recurrence_end_date");

                    task = new Tasks(taskId, title, description, due_date, type, status, priority, dependency, is_recurring, recurrence_type, recurrence_end_date);
                } else {
                    System.out.println("Task with ID " + taskId + " not found.");
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error fetching task: " + e.getMessage());
        }

        return task;
    }

    public boolean deleteTask(int task_id, String title) {
        String deleteSQL = "DELETE FROM tasks WHERE id = ?";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {
            stmt.setInt(1, task_id);
            int i= stmt.executeUpdate();
            if (i> 0){
                System.out.println("Task \"" + title + "\" deleted successfully!");
                return true;
            }
            else{
                System.out.println("No task found with the specified Task Index.");
                return false;
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Error deleting task: " + e.getMessage());
            return false;
        }
    }

    public ArrayList<Tasks> fetchTasksFromDatabase(int user_id) {
        ArrayList<Tasks> taskList = new ArrayList<>();
        String fetchSQL = "SELECT * FROM tasks WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(fetchSQL)) {

            stmt.setInt(1, user_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Extract data from the ResultSet
                    int taskID = rs.getInt("id");
                    String title = rs.getString("title");
                    String description = rs.getString("description");
                    Date due_date = rs.getDate("due_date");
                    String type = rs.getString("type");
                    String status = rs.getString("status");
                    Tasks.Priority priority = Tasks.Priority.valueOf(rs.getString("priority"));
                    int dependency = rs.getInt("dependency");
                    char is_recurring = rs.getString("is_recurring").charAt(0);
                    Tasks.Recurrence_type recurrence_type = Tasks.Recurrence_type.valueOf(rs.getString("recurrence_type")); // Convert string to enum
                    Date recurrence_end_date = rs.getDate("recurrence_end_date");

                    // Create a Task object and add it to the ArrayList
                    Tasks task = new Tasks(taskID, title, description, due_date, type, status, priority, dependency, is_recurring, recurrence_type, recurrence_end_date);
                    taskList.add(task);
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Error fetching tasks from the database: " + e.getMessage());
        }

        return taskList;
    }

    public void loadTasksFromDatabase(HashMap<Integer, Tasks> taskMap, int user_id) {
        String loadSQL = "SELECT * FROM tasks WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(loadSQL)) {

            stmt.setInt(1, user_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Extract data from the ResultSet
                    int taskID = rs.getInt("id");
                    String title = rs.getString("title");
                    String description = rs.getString("description");
                    Date due_date = rs.getDate("due_date");
                    String type = rs.getString("type");
                    String status = rs.getString("status");
                    Tasks.Priority priority = Tasks.Priority.valueOf(rs.getString("priority"));
                    int dependency = rs.getInt("dependency");
                    char is_recurring = rs.getString("is_recurring").charAt(0);
                    Tasks.Recurrence_type recurrence_type = Tasks.Recurrence_type.valueOf(rs.getString("recurrence_type"));
                    Date recurrence_end_date = rs.getDate("recurrence_end_date");

                    // Create a Task object and add it to the HashMap
                    Tasks task = new Tasks(taskID, title, description, due_date, type, status, priority, dependency, is_recurring, recurrence_type, recurrence_end_date);
                    taskMap.put(taskID, task); // Add the task to the HashMap
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Error fetching tasks from the database: " + e.getMessage());
        }
    }

    public ArrayList<Tasks> searchTasksByEmbedding(float[] queryEmbedding, int user_id) {
        ArrayList<Tasks> taskList = new ArrayList<>();
        String fetchSQL = "SELECT * FROM tasks WHERE user_id = ?";

        // Similarity threshold (adjust as needed)
        double SIMILARITY_THRESHOLD = 0.5;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(fetchSQL)) {

            stmt.setInt(1, user_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Extract data from the ResultSet
                    int taskID = rs.getInt("id");
                    String title = rs.getString("title");
                    String description = rs.getString("description");
                    Date due_date = rs.getDate("due_date");
                    String type = rs.getString("type");
                    String status = rs.getString("status");
                    Tasks.Priority priority = Tasks.Priority.valueOf(rs.getString("priority"));
                    int dependency = rs.getInt("dependency");
                    char is_recurring = rs.getString("is_recurring").charAt(0);
                    Tasks.Recurrence_type recurrence_type = Tasks.Recurrence_type.valueOf(rs.getString("recurrence_type"));
                    Date recurrence_end_date = rs.getDate("recurrence_end_date");

                    // Fetch the task's embedding as a byte array
                    byte[] embeddingBytes = rs.getBytes("embedding");

                    System.out.println("Retrieved embedding bytes length: " + embeddingBytes.length);

                    // Convert the byte array to a float array
                    float[] taskEmbedding = toFloatArray(embeddingBytes);

                    // Calculate cosine similarity between the query embedding and the task's embedding
                    double similarity = cosineSimilarity(queryEmbedding, taskEmbedding);

                    // Add the task to the list if similarity exceeds the threshold
                    if (similarity > SIMILARITY_THRESHOLD) {
                        Tasks task = new Tasks(taskID, title, description, due_date, type, status, priority, dependency, is_recurring, recurrence_type, recurrence_end_date);
                        taskList.add(task);
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Error searching tasks by embedding: " + e.getMessage());
        }

        return taskList;
    }

    // Helper method to convert byte[] to float[]
    public static float[] toFloatArray(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) {
            throw new IllegalArgumentException("Byte array is null or empty");
        }
        if (byteArray.length % Float.BYTES != 0) {
            throw new IllegalArgumentException(
                    "Byte array length (" + byteArray.length +
                            ") is not a multiple of Float.BYTES (" + Float.BYTES + ")"
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        // Use same byte ordering as in toByteArray
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] floatArray = new float[byteArray.length / Float.BYTES];

        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = buffer.getFloat();
        }
        return floatArray;
    }

    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        // Validate input vectors
        if (vectorA == null || vectorB == null) {
            throw new IllegalArgumentException("Vectors cannot be null.");
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have the same length.");
        }

        // Calculate dot product
        double dotProduct = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
        }

        // Calculate magnitudes (norms) of the vectors
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        // Avoid division by zero
        if (normA == 0 || normB == 0) {
            throw new IllegalArgumentException("One of the vectors has zero magnitude.");
        }

        // Calculate and return cosine similarity
        return dotProduct / (normA * normB);
    }

}

