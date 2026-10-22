package org.ssd.project;

import java.sql.*;

public class SqliteDemo {
    public static void testSqLite() {
        String url = "jdbc:sqlite:data/test.db";

        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected. SQLite version: "
                    + conn.getMetaData().getDatabaseProductVersion());

            createTable(conn);
            insertUser(conn, "Alice", 30);
            insertUser(conn, "Bob", 25);
            insertUser(conn, "Carla", 41);

            System.out.println("\nAll users:");
            printAllUsers(conn);

            System.out.println("\nUsers older than 28:");
            printUsersOlderThan(conn, 28);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void createTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id   INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                age  INTEGER
            )
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute("DELETE FROM users"); // start clean on each run
        }
    }

    private static void insertUser(Connection conn, String name, int age) throws SQLException {
        String sql = "INSERT INTO users(name, age) VALUES(?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, age);
            ps.executeUpdate();
        }
    }

    private static void printAllUsers(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, age FROM users")) {
            while (rs.next()) {
                System.out.printf("%d | %s | %d%n",
                        rs.getInt("id"), rs.getString("name"), rs.getInt("age"));
            }
        }
    }

    private static void printUsersOlderThan(Connection conn, int minAge) throws SQLException {
        String sql = "SELECT id, name, age FROM users WHERE age > ? ORDER BY age";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, minAge);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("%d | %s | %d%n",
                            rs.getInt("id"), rs.getString("name"), rs.getInt("age"));
                }
            }
        }
    }
}
