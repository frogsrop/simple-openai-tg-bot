package com.aibot.repositories

import com.aibot.domain.models.Message
import com.aibot.domain.models.Role
import com.aibot.repositories.tables.Messages
import org.ktorm.database.Database
import org.ktorm.dsl.*

interface MessagesRepository {
    fun selectMessages(userId: Long): List<Message>
    fun getMessages(userId: Long): List<Message>
    fun addMessage(userId: Long, message: Message)
    fun deleteMessages(userId: Long)
}

class KtormMessagesRepository(
    private val database: Database
) : MessagesRepository {

    init {
        database.useConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                        CREATE TABLE IF NOT EXISTS messages (
                        message_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        ts INTEGER,
                        user_id INTEGER REFERENCES users(user_id),
                        message TEXT,
                        role TEXT
                    )"""
                )
            }
        }
    }

    override fun selectMessages(userId: Long): List<Message> {
        return database.from(Messages)
            .select(
                Messages.messageId,
                Messages.ts,
                Messages.userId,
                Messages.message,
                Messages.role
            )
            .where(Messages.userId.eq(userId))
            .map { row ->
                Message(
                    ts = row[Messages.ts]!!,
                    userId = row[Messages.userId]!!,
                    message = row[Messages.message]!!,
                    role = Role.valueOf(row[Messages.role]!!),
                )
            }
            .toList()
    }

    override fun getMessages(userId: Long): List<Message> {
        val chatMessages = selectMessages(userId)
        return chatMessages.sortedBy { it.ts }
    }

    override fun addMessage(userId: Long, message: Message) {
        database.insert(Messages) {
            set(it.ts, message.ts)
            set(it.userId, message.userId)
            set(it.message, message.message)
            set(it.role, message.role.name)
        }
    }

    override fun deleteMessages(userId: Long) {
        database.delete(Messages) {
            it.userId eq userId
        }
    }
}