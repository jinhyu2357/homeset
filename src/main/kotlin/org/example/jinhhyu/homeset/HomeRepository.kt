package org.example.jinhhyu.homeset

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import org.bukkit.Location

data class HomeRecord(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

class HomeRepository private constructor(private val connection: Connection) {
    companion object {
        fun connect(databaseFile: File): HomeRepository {
            Class.forName("org.sqlite.JDBC")
            val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
            val connection = DriverManager.getConnection(jdbcUrl)
            return HomeRepository(connection)
        }
    }

    @Throws(SQLException::class)
    fun initializeSchema() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS homes (
                    player_uuid TEXT NOT NULL,
                    home_name TEXT NOT NULL,
                    world_name TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    PRIMARY KEY (player_uuid, home_name)
                );
                """.trimIndent()
            )
        }
    }

    @Throws(SQLException::class)
    fun saveHome(playerId: UUID, homeName: String, location: Location) {
        val world = location.world ?: throw IllegalArgumentException("Cannot save home without world.")
        connection.prepareStatement(
            """
            INSERT INTO homes (player_uuid, home_name, world_name, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid, home_name) DO UPDATE SET
                world_name = excluded.world_name,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                yaw = excluded.yaw,
                pitch = excluded.pitch;
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.setString(2, homeName)
            statement.setString(3, world.name)
            statement.setDouble(4, location.x)
            statement.setDouble(5, location.y)
            statement.setDouble(6, location.z)
            statement.setFloat(7, location.yaw)
            statement.setFloat(8, location.pitch)
            statement.executeUpdate()
        }
    }

    @Throws(SQLException::class)
    fun findHome(playerId: UUID, homeName: String): HomeRecord? {
        connection.prepareStatement(
            """
            SELECT world_name, x, y, z, yaw, pitch
            FROM homes
            WHERE player_uuid = ? AND home_name = ?;
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.setString(2, homeName)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    return null
                }

                return HomeRecord(
                    worldName = resultSet.getString("world_name"),
                    x = resultSet.getDouble("x"),
                    y = resultSet.getDouble("y"),
                    z = resultSet.getDouble("z"),
                    yaw = resultSet.getFloat("yaw"),
                    pitch = resultSet.getFloat("pitch")
                )
            }
        }
    }

    @Throws(SQLException::class)
    fun listHomes(playerId: UUID): List<String> {
        connection.prepareStatement(
            """
            SELECT home_name
            FROM homes
            WHERE player_uuid = ?
            ORDER BY home_name COLLATE NOCASE;
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.executeQuery().use { resultSet ->
                val homes = mutableListOf<String>()
                while (resultSet.next()) {
                    homes += resultSet.getString("home_name")
                }
                return homes
            }
        }
    }

    @Throws(SQLException::class)
    fun deleteHome(playerId: UUID, homeName: String): Boolean {
        connection.prepareStatement(
            """
            DELETE FROM homes
            WHERE player_uuid = ? AND home_name = ?;
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.setString(2, homeName)
            return statement.executeUpdate() > 0
        }
    }

    fun close() {
        try {
            connection.close()
        } catch (_: SQLException) {
            // Suppress errors during plugin shutdown.
        }
    }
}
