import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import net.samyn.kapper.example.kotlin.SuperHero
import net.samyn.kapper.example.kotlin.SuperHeroRepository
import net.samyn.kapper.example.kotlin.Villain
import net.samyn.kapper.execute
import net.samyn.kapper.query
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Example {
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

        val initScripts = mapOf(
            PostgreSQLContainer::class.java to "postgresql.sql",
            MySQLContainer::class.java to "mysql.sql",
        )
    }

    val dbScripts = System.getProperty("db-migrations") ?: "../db"

    val batman = SuperHero(UUID.randomUUID(), "Batman", "batman@dc.com", 85)
    val spiderMan = SuperHero(UUID.randomUUID(), "Spider-man", "spider@marvel.com", 62)

    @BeforeAll
    fun setup() {
        allContainers.values.forEach { container ->
            setupDatabase(container)
        }
    }

    private fun setupDatabase(container: JdbcDatabaseContainer<*>) {
        val schemaFile = Path.of(dbScripts)
            .resolve(initScripts[container::class.java]!!).toFile()
        getDataSource(container).connection.use { connection ->
            // initialising the DB the primitive way.
            connection.execute(schemaFile.readText())
        }
    }

    // Example using HikariCP, but other pools can be used natively
    private fun getDataSource(container: JdbcDatabaseContainer<*>) =
        HikariDataSource().apply {
            jdbcUrl = container.jdbcUrl + "?allowMultiQueries=true"
            username = container.username
            password = container.password
        }

    @ParameterizedTest()
    @MethodSource("databaseContainers")
    fun example(container: JdbcDatabaseContainer<*>) {
        container.isRunning.shouldBeTrue()
        getDataSource(container).use { ds ->
            val repo = SuperHeroRepository(ds)
            repo.list().shouldBeEmpty()

            // insert heroes
            repo.insertHero(batman)
            repo.insertHero(spiderMan)

            // find all
            repo.list().shouldContainAll(batman, spiderMan)

            // find by ID
            repo.findById(batman.id).shouldBe(batman)

            // find by name
            repo.findByName(spiderMan.name).shouldBe(spiderMan)

            // insert some battles
            val catwoman = Villain(UUID.randomUUID(), "Catwoman")
            repo.insertBattle(
                batman,
                catwoman,
                LocalDateTime.of(2014, 7, 13, 23, 47),
            )
            val joker = Villain(UUID.randomUUID(), "Joker")
            repo.insertBattle(
                batman,
                joker,
                LocalDateTime.of(1940, 3, 5, 14, 30),
            )
            // another insert of battle with joker should not create a new Villain
            repo.insertBattle(
                batman,
                joker,
                LocalDateTime.of(1994, 1, 19, 22, 5)
            )

            // find battles - example of a join
            val battles = repo.findBattles(batman.name)
            battles.shouldHaveSize(3)
            battles.map { it.villain }.shouldContainAll("Catwoman", "Joker")

            // example of complex query and custom mapper
            data class PopularMovie(
                val title: String,
                val grossed: Long,
                val comparedToAnnualAverage: Double)
            val movies = ds.connection.use { conn ->
                conn.query("""
                    SELECT 
                     title,
                     release_date, 
                     gross_worldwide, 
                     AVG(gross_worldwide) OVER() AS total_average_gross,
                     AVG(gross_worldwide) OVER(PARTITION BY EXTRACT(YEAR FROM release_date)) AS average_annual_gross
                    FROM movies 
                    ORDER BY gross_worldwide DESC
                """.trimIndent(),
                    { rs, _ ->
                        val gross = rs.getLong("gross_worldwide")
                        val annualAvgGross = rs.getInt("average_annual_gross")
                        PopularMovie(
                            rs.getString("title"),
                            gross,
                            annualAvgGross.toDouble()/gross
                        )
                    })
            }

            movies
                .filter { it.comparedToAnnualAverage > 2.0 }
                .sortedByDescending { it.comparedToAnnualAverage }
                .forEach {
                    println("${it.title} took ${it.grossed}, or more than double the annual average.")
                    it.comparedToAnnualAverage.shouldBeGreaterThan(2.0)
            }
        }
    }
}