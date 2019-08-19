package com.jackharrhy.storefront;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Storage {
    private Logger logger;
    private Connection conn;
    private String url;

    public static String serializeLocation(Location location) {
        return "" + location.getWorld().getName()
                + ":" + location.getX()
                + ":" + location.getY()
                + ":" + location.getZ();
    }

    public Storage(Logger logger, String fileName) {
        this.logger = logger;

        url = "jdbc:sqlite:" + fileName;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                initialize(getConnection());
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(url);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

    private void initialize(Connection conn) {
        String sql = "CREATE TABLE IF NOT EXISTS chest ("
                + "	id INTEGER PRIMARY KEY,"
                + " owner TEXT NOT NULL, "
                + " location TEXT NOT NULL, "
                + "	contents TEXT NOT NULL, "
                + "	modified INTEGER NOT NULL"
                + ");";

        try {
            Statement stmt = conn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
        }
    }

    public Boolean updateStorefront(Player owner, Location location, String contents) {
        String locationSerialized = serializeLocation(location);

        Connection conn = getConnection();
        String sql = "INSERT OR REPLACE INTO chest (id, owner, location, contents) VALUES (" +
                "(SELECT id FROM chest WHERE location = ?), " +
                "?, ?, ?)";

        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, locationSerialized);
            pstmt.setString(2, owner.getUniqueId().toString());
            pstmt.setString(3, locationSerialized);
            pstmt.setString(4, contents);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Boolean removeStorefront(Player owner, Location location) {
        String locationSerialized = serializeLocation(location);

        Connection conn = getConnection();
        String sql = "DELETE FROM chest WHERE location = ? AND owner = ?";

        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, locationSerialized);
            pstmt.setString(2, owner.getUniqueId().toString());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public String owner(Location location) {
        Connection conn = getConnection();
        String sql = "SELECT owner FROM chest WHERE location = ?";

        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, serializeLocation(location));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
