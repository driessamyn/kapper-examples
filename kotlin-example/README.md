# Kapper Example - Kotlin

## DB structure

See [db](../db) for the DB structure used in this example.
Separate scripts are provided for [PostgreSQL](../db/postgresql.sql) and [MySQL](../db/mysql.sql).

## Repository class

[Universe.kt](./src/main/kotlin/net/samyn/kapper/example/kotlin/Universe.kt) has an example repository class using the kapper API:

### Simple SELECT query

```kotlin
fun list(): List<SuperHero> = dataSource.connection.use {
    it.query<SuperHero>("SELECT * FROM super_heroes")
}
```

Executes a simple SELECT query on the `super_heroes` table and maps the results to the `SuperHero` data class.

### Find by ID

```kotlin
fun findById(id: UUID) = dataSource.connection.use {
    it.querySingle<SuperHero>(
        "SELECT * FROM super_heroes WHERE id = :id", "id" to id)
}

fun findByName(name: String) = dataSource.connection.use {
    it.querySingle<SuperHero>(
        "SELECT * FROM super_heroes WHERE name = :name", "name" to name)
}
```

The above examples select a single row by using the ID or name and also automatically maps to the `SuperHero` class.

### Join query

```kotlin
fun findBattles(superHeroName: String) = dataSource.connection.use {
    it.query<SuperHeroBattle>(
        """
         SELECT s.name as superhero, v.name as villain, b.battle_date as date
         FROM super_heroes as s
         INNER JOIN battles as b on s.id = b.super_hero_id
         INNER JOIN villains as v on v.id = b.villain_id
         WHERE s.name = :name 
    """.trimIndent(), "name" to superHeroName
    )
}
```

This example uses a SQL query that has multiple joins and auto-maps the result to the `SuperHeroBattle` data class.

This is an example to illustrate how Kapper does not interfere with the SQL query language and it does not keep a strict mapping between the relational and the object model.
Instead, it simply executes the query that is provided and maps to the object that is specified.
This avoids needing to learn another language or API to facilitate JOINs, or other complex queries, or wasteful multiple queries and code based joining of data that is often seen when people use ORMs "out the box".

You can also see here how data from the `super_heroes` table is used by multiple data classes.
Kapper is not opinionated about the mapping between the relational and object model.

### Simple INSERT

```kotlin
fun insertHero(superHero: SuperHero) = dataSource.connection.use {
    it.execute(
        """
         INSERT INTO super_heroes(id, name, email, age) 
         VALUES (:id, :name, :email, :age)
    """.trimIndent(),
        "id" to superHero.id,
        "name" to superHero.name,
        "email" to superHero.email,
        "age" to superHero.age
    )
}
```

The above is a simple example of an insert.
Kapper currently doesn't support passing an object into teh execute function, instead it requires each parameter to be specified individually.
This makes it very easy to understand the expected behaviour (for example, when ID values are generated in code vs the DB).
However, if there is sufficient demand, an additional API will be introduced.

### Transactional INSERT (IF NOT EXIST)

```kotlin
fun insertBattle(
    superHero: SuperHero,
    villain: Villain,
    date: LocalDateTime,
) = dataSource.connection.use {
    try {
        it.autoCommit = false
        it.execute(
            """
             INSERT INTO super_heroes(id, name, email, age) 
             VALUES (:id, :name, :email, :age)
             ${ignoreConflict("id")}
              """.trimIndent(),
            "id" to superHero.id,
            "name" to superHero.name,
            "email" to superHero.email,
            "age" to superHero.age
        )
        it.execute(
            """
             INSERT INTO villains(id, name) 
             VALUES (:id, :name)
             ${ignoreConflict("id")}
              """.trimIndent(),
            "id" to villain.id,
            "name" to villain.name,
        )
        it.execute(
            """
             INSERT INTO battles(super_hero_id, villain_id, battle_date, updated_ts)
             VALUES (:super_hero_id, :villain_id, :date, NOW())
              """.trimIndent(),
            "super_hero_id" to superHero.id,
            "villain_id" to villain.id,
            "date" to date,
        )
        it.commit()
    } catch (ex: SQLException) {
        it.rollback()
        throw ex
    }
}

// example of something non-trivial that impacts the Query
//  in this case, we want to support both mySQL and postgreSQL in their
//  own way of doing an insert and ignoring the duplicate.
private fun ignoreConflict(updateCol: String) =
    if(DbType.MYSQL == dbType) {
        "ON DUPLICATE KEY UPDATE $updateCol=$updateCol"
    } else {
        "ON CONFLICT DO NOTHING"
    }
```

In the above example, a DB transaction is used to ensure the inserts are atomic.
As you can see, Kapper doesn't interfere with the transaction handling, in fact it is completely unaware of the existence of the transaction.
This is because Kapper is simply an extension of the JDBC DB Connection rather than wrapping existing APIs.

Secondly, the example shows how it is possible to use DB native functionality, in this case to support an insert only if the data does not exist.
In PostgreSQL, this is supported by the `ON CONFLICT DO NOTHING` hint and in mySQL, we use `ON DUPLICATE KEY UPDATE`.

Because Kapper uses SQL queries are provided, it never needs to "catch up" with specific DB features.
Users or Kapper are in complete control.
This is further illustrated by the example below which uses a complex Window query and custome mapper.

## Running the examples

[Example](./src/test/kotlin/Example.kt) has a JUnit test that uses all examples in the `SuperHeroRepository`.
This test uses [TestContainers](https://testcontainers.com/) and is executed against PostgreSQL and mySQL.

## A more complex example

[Example](./src/test/kotlin/Example.kt) contains an additional, more complex, query, which serves as an example of how complex SQL queries can be used with Kapper without the need for a complex or new API.

In this example, we use a Window function to calculate averages for a year, and map this to a new `PopularMovie` data class.

```kotlin
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
```
