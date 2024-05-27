package com.aibot.repositories

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aibot.ChatHistoryManager.Companion.toName
import com.aibot.domain.models.Message
import com.aibot.repositories.tables.Messages
import org.ktorm.database.Database
import org.ktorm.dsl.*

interface MessagesRepository {
    fun selectMessagesByRole(userId: Long, role: ChatRole): List<Message>
    fun getMessages(userId: Long): List<ChatMessage>
    fun addMessage(userId: Long, message: ChatMessage)
    fun deleteMessages(userId: Long)
}

class MessagesRepositoryImpl(
    private val database: Database,
    private val usersRepository: UsersRepository
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

    override fun selectMessagesByRole(userId: Long, role: ChatRole): List<Message> {
        return database.from(Messages)
            .select(
                Messages.messageId,
                Messages.ts,
                Messages.userId,
                Messages.message,
                Messages.role
            )
            .where(Messages.userId.eq(userId).and(Messages.role.eq(role.toName())))
            .map { row ->
                Message(
                    ts = row[Messages.ts]!!,
                    userId = row[Messages.userId]!!,
                    message = row[Messages.message]!!,
                    role = ChatRole(row[Messages.role]!!),

                    )

            }
            .toList()
    }

    override fun getMessages(userId: Long): List<ChatMessage> {
        val userMessages = selectMessagesByRole(userId, ChatRole.User).toMutableList()
        val assistantMessages = selectMessagesByRole(userId, ChatRole.Assistant)
        userMessages.addAll(assistantMessages)
        userMessages.sortBy { it.ts }
        val result = userMessages.map {
            val user = usersRepository.getUser(userId)
            ChatMessage(it.role, it.message, user?.name ?: "")
        }
        return result
    }

    override fun addMessage(userId: Long, message: ChatMessage) {
        database.insert(Messages) {
            set(it.ts, System.currentTimeMillis())
            set(it.userId, userId)
            set(it.message, message.content)
            set(it.role, message.role.toName())
        }
    }

    override fun deleteMessages(userId: Long) {
        database.delete(Messages) {
            it.userId eq userId
        }
    }
}