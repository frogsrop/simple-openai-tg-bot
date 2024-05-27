package com.aibot

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aibot.domain.models.User
import com.aibot.repositories.MessagesRepository
import com.aibot.repositories.UsersRepository


class ChatHistoryManager(
    private val usersRepository: UsersRepository,
    private val messagesRepository: MessagesRepository
) {
    fun getMessages(userId: Long): List<ChatMessage> {
        return messagesRepository.getMessages(userId)
    }

    fun addMessage(userId: Long, message: ChatMessage) {
        return messagesRepository.addMessage(userId, message)
    }

    fun addUser(newUser: User) {
        return usersRepository.addUser(newUser)
    }

    fun getUser(userId: Long): User? {
        return usersRepository.getUser(userId)
    }

    fun hasUser(userId: Long): Boolean {
        return getUser(userId) != null
    }

    private fun deleteMessages(userId: Long) {
        return messagesRepository.deleteMessages(userId)
    }

    companion object {
        fun ChatRole.toName(): String {
            return when (this) {
                ChatRole.User -> "User"
                ChatRole.Assistant -> "Assistant"
                ChatRole.System -> "System"
                ChatRole.Function -> "Function"
                else -> error("Bad role")
            }
        }
    }
}