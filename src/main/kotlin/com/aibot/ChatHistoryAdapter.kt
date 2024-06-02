package com.aibot

import com.aibot.domain.models.MessageData
import com.aibot.domain.models.User
import com.aibot.domain.models.UserInfo
import com.aibot.repositories.MessagesRepository
import com.aibot.repositories.UsersRepository


class ChatHistoryAdapter(
    private val usersRepository: UsersRepository,
    private val messagesRepository: MessagesRepository
) {
    suspend fun getMessages(userId: Long): List<MessageData> {
        val messages = messagesRepository.getMessages(userId)
        return messages
    }

    fun addMessage(messageData: MessageData) {
        return messagesRepository.addMessage(messageData)
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
    fun deleteUser(userId: Long): Boolean {
        if (!hasUser(userId)) return false
        usersRepository.deleteUser(userId)
        return true
    }
    fun deleteMessages(userId: Long) {
        return messagesRepository.deleteMessages(userId)
    }

    fun getUsage(userId: Long): Float {
        return messagesRepository.getUsage(userId)
    }

    fun listUsers(): List<UserInfo> {
        return usersRepository.listUsers().map {
            UserInfo(it, messagesRepository.getUsage(it.userId))
        }
    }
}