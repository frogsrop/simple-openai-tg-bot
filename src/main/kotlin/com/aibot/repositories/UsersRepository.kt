package com.aibot.repositories

import com.aibot.domain.models.User
import com.aibot.domain.models.UserPermission
import com.aibot.repositories.tables.Users
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.support.sqlite.insertOrUpdate

interface UsersRepository {
    fun addUser(newUser: User)
    fun getUser(userId: Long): User?
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
                        name VARCHAR
                    )"""
                )
            }
        }
    }

    override fun addUser(newUser: User) {
        database.insertOrUpdate(Users) {
            set(it.user_id, newUser.userId)
            set(it.name, newUser.name)
            set(it.permission, newUser.permission.value)
            onConflict(it.user_id) {
                set(it.name, newUser.name)
                set(it.permission, newUser.permission.value)
            }
        }
    }

    override fun getUser(userId: Long): User? {
        val users = database.from(Users)
            .select(
                Users.user_id,
                Users.name,
                Users.permission
            )
            .where(Users.user_id.eq(userId))
            .map { row ->
                User(
                    userId = row[Users.user_id]!!,
                    name = row[Users.name]!!,
                    permission = UserPermission.from(row[Users.permission]!!)!!
                )
            }

        return users.firstOrNull()
    }
}