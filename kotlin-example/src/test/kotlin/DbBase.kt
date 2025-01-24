import com.zaxxer.hikari.HikariDataSource
import net.samyn.kapper.execute
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments.arguments
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DbBase {
    companion object {
        @Container
        val postgresql = PostgreSQLContainer("postgres:16")

        @Container
        val mysql = MySQLContainer("mysql:8.4")

        val allContainers =
            mapOf(
                "PostgreSQL" to postgresql,
                "MySQL" to mysql,
            )

        @JvmStatic
        fun databaseContainers() = allContainers.map { arguments(named(it.key, it.value)) }

        val initScripts =
            mapOf(
                PostgreSQLContainer::class.java to "postgresql.sql",
                MySQLContainer::class.java to "mysql.sql",
            )
    }

    val dbScripts = System.getProperty("db-migrations") ?: "../db"

    @BeforeAll
    fun setup() {
        allContainers.values.forEach { container ->
            setupDatabase(container)
        }
    }

    private fun setupDatabase(container: JdbcDatabaseContainer<*>) {
        val schemaFile =
            Path.of(dbScripts)
                .resolve(initScripts[container::class.java]!!).toFile()
        getDataSource(container).connection.use { connection ->
            // initialising the DB the primitive way.
            connection.execute(schemaFile.readText())
        }
    }

    // KapperExample using HikariCP, but other pools can be used natively
    protected fun getDataSource(container: JdbcDatabaseContainer<*>) =
        HikariDataSource().apply {
            jdbcUrl = container.jdbcUrl + "?allowMultiQueries=true"
            username = container.username
            password = container.password
        }
}
