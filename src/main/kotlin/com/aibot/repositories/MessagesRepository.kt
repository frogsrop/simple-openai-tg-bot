package com.aibot.repositories

import com.aibot.domain.models.MessageData
import com.aibot.domain.models.Role
import com.aibot.repositories.tables.Messages
import org.ktorm.database.Database
import org.ktorm.dsl.*

interface MessagesRepository {
    fun selectMessages(userId: Long): List<MessageData>
    fun getMessages(userId: Long): List<MessageData>
    fun addMessage(messageData: MessageData)
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
                        resource TEXT,
                        role TEXT
                    )"""
                )
            }
        }
    }

    override fun selectMessages(userId: Long): List<MessageData> {
        return database.from(Messages)
            .select(
                Messages.messageId,
                Messages.ts,
                Messages.userId,
                Messages.message,
                Messages.resource,
                Messages.role
            )
            .where(Messages.userId.eq(userId))
            .map { row ->
                MessageData(
                    ts = row[Messages.ts]!!,
                    userId = row[Messages.userId]!!,
                    message = row[Messages.message]!!,
                    resource = row[Messages.resource]!!,
                    role = Role.valueOf(row[Messages.role]!!),
                )
            }
            .toList()
    }

    override fun getMessages(userId: Long): List<MessageData> {
        val chatMessages = selectMessages(userId)
        return chatMessages.sortedBy { it.ts }
    }

    override fun addMessage(messageData: MessageData) {
        database.insert(Messages) {
            set(it.ts, messageData.ts)
            set(it.userId, messageData.userId)
            set(it.message, messageData.message)
            set(it.resource, messageData.resource)
            set(it.role, messageData.role.name)
        }
    }

    override fun deleteMessages(userId: Long) {
        database.delete(Messages) {
            it.userId eq userId
        }
    }
}