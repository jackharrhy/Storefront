package com.jackharrhy.storefront;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Storage {
    private Logger logger;
    private Jdbi jdbi;

    private static String serializeLocation(Location location) {
        return "" + location.getWorld().getName()
                + ":" + location.getX()
                + ":" + location.getY()
                + ":" + location.getZ();
    }

    private Location deserializeLocation(String serializedLocation) {
        String[] splitLocation = serializedLocation.split(":");
        return new Location(
                Bukkit.getWorld(splitLocation[0]),
                Double.parseDouble(splitLocation[1]),
                Double.parseDouble(splitLocation[2]),
                Double.parseDouble(splitLocation[3])
        );
    }

    public Storage(Logger logger, String fileName) {
        this.logger = logger;

        File dbFile = new File(fileName);

        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage());
                e.printStackTrace();
            }
        }

        String connectionString = "jdbc:sqlite:" + fileName;
        this.jdbi = Jdbi.create(connectionString);
        this.initialize();
    }

    private void initialize() {
        String createChestTableSql = "CREATE TABLE IF NOT EXISTS chest ("
                + "	id INTEGER PRIMARY KEY,"
                + " owner TEXT NOT NULL, "
                + " location TEXT NOT NULL, "
                + "	contents TEXT NOT NULL, "
                + "	modified INTEGER NOT NULL"
                + ");";
        jdbi.useHandle(handle -> handle.execute(createChestTableSql));
    }

    public Boolean updateStorefront(Player owner, Location location, String contents) {
        String locationSerialized = serializeLocation(location);

        String insertOrUpdateStorefrontSql = "INSERT OR REPLACE INTO chest (id, owner, location, contents, modified) " +
                "VALUES ((SELECT id FROM chest WHERE location = :location), :owner, :location, :contents, :modified)";

        int updated = jdbi.withHandle(handle -> handle.createUpdate(insertOrUpdateStorefrontSql)
                .bind("location", locationSerialized)
                .bind("owner", owner.getUniqueId().toString())
                .bind("contents", contents)
                .bind("modified", Instant.now().getEpochSecond())
                .execute());

        if (updated == 1) {
            return true;
        } else if (updated == 0) {
            return false;
        } else {
            logger.log(Level.SEVERE, "Updated more than one storefront on a single call");
            return null;
        }
    }

    public Boolean updateStorefront(Location location, String contents) {
        String locationSerialized = serializeLocation(location);

        String updateStorefrontSql = "UPDATE chest SET contents = :contents, modified = :modified " +
                "WHERE id = (SELECT id FROM chest WHERE location = :location)";

        int updated = jdbi.withHandle(handle -> handle.createUpdate(updateStorefrontSql)
                .bind("location", locationSerialized)
                .bind("contents", contents)
                .bind("modified", Instant.now().getEpochSecond())
                .execute());

        if (updated == 1) {
            return true;
        } else if (updated == 0) {
            return false;
        } else {
            logger.log(Level.SEVERE, "Updated more than one storefront on a single call");
            return null;
        }
    }

    public Boolean removeStorefront(Player owner, Location location) {
        String removeStorefrontSql = "DELETE FROM chest WHERE location = ? AND owner = ?";

        int removed = jdbi.withHandle(handle -> handle.execute(
                removeStorefrontSql, serializeLocation(location), owner.getUniqueId().toString()
        ));

        if (removed == 1) {
            return true;
        } else if (removed == 0) {
            return false;
        } else {
            logger.log(Level.SEVERE, "Removed more than one storefront on a single call");
            return null;
        }
    }

    public Boolean removeStorefront(Location location) {
        String removeStorefrontSql = "DELETE FROM chest WHERE location = ?";

        int removed = jdbi.withHandle(handle -> handle.execute(
                removeStorefrontSql, serializeLocation(location)
        ));

        if (removed == 1) {
            return true;
        } else if (removed == 0) {
            return false;
        } else {
            logger.log(Level.SEVERE, "Removed more than one storefront on a single call");
            return null;
        }
    }

    public List<String> getAllContents() {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT contents FROM chest")
                .mapTo(String.class)
                .list());
    }

    public List<Location> getAllLocations() {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT location FROM chest")
                .mapTo(String.class)
                .list()
                .stream()
                .map((string) -> deserializeLocation(string))
                .collect(Collectors.toList()));
    }

    public Optional<String> owner(Location location) {
        String getOwnerFromChestSql = "SELECT owner FROM chest WHERE location = ?";
        return jdbi.withHandle(handle -> handle.select(getOwnerFromChestSql, serializeLocation(location))
                .mapTo(String.class)
                .findFirst());
    }
}
