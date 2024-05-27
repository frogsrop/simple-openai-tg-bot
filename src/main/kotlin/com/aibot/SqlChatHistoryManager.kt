package com.aibot

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

data class User(val userId: Long, val name: String)
data class Message(val ts: Long, val userId: Long, val message: String, val role: ChatRole)
class SqlChatHistoryManager private constructor(val path: String) {
    private lateinit var connection: Connection
    private lateinit var statement: Statement
    fun connect() {
        connection = DriverManager.getConnection("jdbc:sqlite:$path")
        statement = connection.createStatement()
    }

    private fun selectMessagesByRole(userId: Long, role: ChatRole): List<Message> {
        val selectUserMessagesQuery = "SELECT * FROM $MESSAGES WHERE user_id = $userId and role = \"${role.toName()}\""
        val resultSet = statement.executeQuery(selectUserMessagesQuery)

        val finalArray = mutableListOf<Message>()
        while (resultSet.next()) {
            val ts = resultSet.getLong("ts")
            val message = resultSet.getString("message")
            val roles = resultSet.getString("role")
            finalArray.add(Message(ts, userId, message, role))
        }
        return finalArray
    }

    fun getMessages(userId: Long): List<ChatMessage> {
        val userMessages = selectMessagesByRole(userId, ChatRole.User).toMutableList()
        val assistantMessages = selectMessagesByRole(userId, ChatRole.Assistant)
        userMessages.addAll(assistantMessages)
        userMessages.sortBy { it.ts }
        val result = userMessages.map {
            val user = getUser(userId)
            ChatMessage(it.role, it.message, user?.name ?: "")
        }
        return result
    }

    fun addMessage(userId: Long, message: ChatMessage) {
        val ts = System.currentTimeMillis()
        val insertQuery =
            "INSERT INTO $MESSAGES (ts, user_id, message, role) VALUES ($ts, $userId, \"${message.content}\", \"${message.role.toName()}\")"
        statement.executeUpdate(insertQuery)
    }

    fun addUser(newUser: User) {
        val oldUser = getUser(newUser.userId)
        if (oldUser == null) {
            val insertUserQuery =
                "INSERT INTO $USERS_TABLE (user_id, name) VALUES (${newUser.userId}, \"${newUser.name}\")"
            statement.executeUpdate(insertUserQuery)
        } else {
            val updateUserQuery = "UPDATE $USERS_TABLE SET name = \"${newUser.name}\" WHERE user_id = ${newUser.userId}"
            statement.executeUpdate(updateUserQuery)
        }
    }

    fun getUser(userId: Long): User? {
        val query = statement.executeQuery("select * from $USERS_TABLE where user_id=$userId")
        return if (query.next()) {
            User(query.getLong("user_id"), query.getString("name"))
        } else {
            null
        }
    }

    fun hasUser(userId: Long): Boolean {
        return getUser(userId) != null
    }

    private fun clearMessages(userId: Long) {
        val deleteMessagesQuery = "DELETE FROM $MESSAGES WHERE user_id = $userId"
        statement.executeUpdate(deleteMessagesQuery)
    }

    private fun createTable() {
        statement.executeUpdate(
            """CREATE TABLE IF NOT EXISTS $USERS_TABLE (
                            user_id LONG PRIMARY KEY,
                            name VARCHAR
            )"""
        )
        statement.executeUpdate(
            """CREATE TABLE IF NOT EXISTS $MESSAGES (
                message_id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts LONG,
                user_id LONG,
                message TEXT,
                role STRING,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )"""
        )
    }

    companion object {
        val USERS_TABLE = "users"
        val MESSAGES = "user_messages"

        fun ChatRole.toName(): String {
           return when (this) {
                ChatRole.User -> "User"
                ChatRole.Assistant -> "Assistant"
                ChatRole.System -> "System"
                ChatRole.Function -> "Function"
                else -> error("Bad role")
            }
        }

        fun build(path: String = "..\\..\\sample.db"): SqlChatHistoryManager {
            val history = SqlChatHistoryManager(path)
            history.connect()
            history.statement.queryTimeout = 5
            history.createTable()
            return history
        }
    }
}