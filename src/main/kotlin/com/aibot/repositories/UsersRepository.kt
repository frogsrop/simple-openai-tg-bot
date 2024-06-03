package com.aibot.repositories

import com.aibot.domain.models.Model
import com.aibot.domain.models.User
import com.aibot.domain.models.UserPermission
import com.aibot.repositories.tables.Users
import kotlinx.serialization.builtins.IntArraySerializer
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.support.sqlite.insertOrUpdate

interface UsersRepository {
    fun addUser(newUser: User)
    fun getUser(userId: Long): User?
    fun deleteUser(userId: Long)
    fun listUsers(): List<User>
}

class KtormUsersRepository(
    private val database: Database
) : UsersRepository {
    init {
        database.useConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                        CREATE TABLE IF NOT EXISTS users (
                        user_id LONG PRIMARY KEY,
                        permission INTEGER,
                        name VARCHAR,
                        model VARCHAR
                    )"""
                )
            }
        }
    }

    override fun addUser(newUser: User) {
        database.insertOrUpdate(Users) {
            set(it.userId, newUser.userId)
            set(it.name, newUser.name)
            set(it.permission, newUser.permission.ordinal)
            set(it.model, newUser.model.id)
            onConflict(it.userId) {
                set(it.name, newUser.name)
                set(it.permission, newUser.permission.ordinal)
                set(it.model, newUser.model.id)
            }
        }
    }

    override fun getUser(userId: Long): User? {
        val users = database.from(Users)
            .select(
                Users.userId,
                Users.name,
                Users.permission,
                Users.model
            )
            .where(Users.userId.eq(userId))
            .map { row ->
                User(
                    userId = row[Users.userId]!!,
                    name = row[Users.name]!!,
                    permission = UserPermission.from(row[Users.permission]!!)!!,
                    model = Model.from(row[Users.model]!!)!!
                )
            }

        return users.firstOrNull()
    }

    override fun deleteUser(userId: Long) {
        database.delete(Users) {
            it.userId eq userId
        }
    }

    override fun listUsers(): List<User> {
        return database.from(Users)
            .select(
                Users.userId,
                Users.name,
                Users.permission,
                Users.model
            ).map { row ->
                val user = User(
                    userId = row[Users.userId]!!,
                    name = row[Users.name]!!,
                    permission = UserPermission.from(row[Users.permission]!!)!!,
                    model = try { Model.from(row[Users.model]!!)!! } catch (e: NullPointerException) { Model.GPT_3_5_TURBO }
                )
                addUser(user)
                user
            }.toList()
    }
}