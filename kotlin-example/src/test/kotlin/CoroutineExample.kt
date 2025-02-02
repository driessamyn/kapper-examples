import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThan
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.samyn.kapper.coroutines.withConnection
import net.samyn.kapper.example.kotlin.SuperHero
import net.samyn.kapper.example.kotlin.Villain
import net.samyn.kapper.example.kotlin.kapper.NonBlockingSuperHeroService
import net.samyn.kapper.query
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.util.UUID

class CoroutineExample : DbBase() {
    private val heroes =
        (0..100).map {
            SuperHero(UUID.randomUUID(), "Superman - $it", "super-$it@dc.com", 85)
        }

    private val service by lazy { NonBlockingSuperHeroService(getDataSource(postgresql)) }

    // this is a bit naughty
    var inserted = false

    @Test
    @Order(1)
    fun `insert heroes`() {
        val log =
            runBlocking {
                val sb = StringBuilder()
                val insertJob =
                    launch {
                        sb.appendLine("[${Thread.currentThread().name}] Starting to insert ${heroes.size} heroes.")
                        service.insertSlowly(heroes).also { inserted = true }
                        sb.appendLine("\n[${Thread.currentThread().name}] Finished inserting ${heroes.size} heroes")
                    }
                val logJob =
                    launch {
                        // print . until the insertJob has completed.
                        sb.append("[${Thread.currentThread().name}] ")
                        while (insertJob.isActive) {
                            delay(100)
                            sb.append(".")
                        }
                    }
                logJob.join()
                sb.toString()
            }
        println(log)
        // first dot should appear before the query finishes.
        log.lines().also { lines ->
            val dottedLineIndex = lines.indexOfFirst { it.contains(Regex("\\.+")) }
            val finishedLineIndex = lines.indexOfFirst { it.contains("Finished inserting") }
            dottedLineIndex shouldBeLessThan finishedLineIndex
        }
    }

    @Test
    @Order(2)
    fun `select heroes`() {
        if (!inserted) {
            `insert heroes`()
        }
        val log =
            runBlocking {
                val sb = StringBuilder()
                val selectJob =
                    async {
                        sb.appendLine("[${Thread.currentThread().name}] Starting to select heroes.")
                        val heroes = service.listSlowly()
                        sb.appendLine("\n[${Thread.currentThread().name}] Finished selecting heroes")
                        heroes
                    }
                val logJob =
                    launch {
                        // print . until the insertJob has completed.
                        sb.append("[${Thread.currentThread().name}] ")
                        while (selectJob.isActive) {
                            delay(100)
                            sb.append(".")
                        }
                    }
                sb.appendLine("Selected ${selectJob.await().joinToString { it.name }}")
                logJob.join()
                sb.toString()
            }
        println(log)
        // first dot should appear before the query finishes.
        log.lines().also { lines ->
            val dottedLineIndex = lines.indexOfFirst { it.contains(Regex("\\.+")) }
            val finishedLineIndex = lines.indexOfFirst { it.contains("Finished selecting") }
            dottedLineIndex shouldBeLessThan finishedLineIndex
        }
    }

    @Test
    @Order(3)
    fun `parallel connections`() {
        if (!inserted) {
            `insert heroes`()
        }
        runBlocking {
            val heroes =
                async {
                    getDataSource(postgresql).withConnection {
                        it.query<SuperHero>("SELECT * FROM super_heroes")
                    }
                }
            // this creates a second connection!
            val villains =
                async {
                    getDataSource(postgresql).withConnection {
                        it.query<Villain>("SELECT * FROM villains")
                    }
                }
            heroes.await().shouldNotBeEmpty()
            villains.await().shouldBeEmpty()
        }
    }
}
