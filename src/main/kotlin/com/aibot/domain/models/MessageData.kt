package com.aibot.domain.models

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aibot.aibot.BuildConfig
import com.aibot.domain.models.MessageData.Companion.toChatMessage
import com.github.kotlintelegrambot.Bot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MessageData(
    val userId: Long,
    val message: String,
    val resource: String,
    val role: Role,
    val usage: Float,
    val ts: Long = System.currentTimeMillis()
) {
    companion object {
        private suspend fun Bot.getImageUrl(fileId: String) = withContext(Dispatchers.IO) {
            val file = getFile(fileId)
            file.second?.let { return@withContext null }
            return@withContext file.first?.body()?.result?.filePath?.let { "https://api.telegram.org/file/bot${BuildConfig.botApiKey}/${it}" }
        }

        suspend fun MessageData.toChatMessage(bot: Bot, user: User): ChatMessage {
            val name = when (role) {
                Role.USER -> user.name.ifEmpty { null }
                Role.ASSISTANT -> "Megumin"
                Role.SYSTEM -> null
            }
            if (this.resource.isEmpty()) {
                return ChatMessage(this.role.chatRole, this.message, name)
            } else {
                val image = bot.getImageUrl(this.resource) ?: error("url is null")
                return ChatMessage(this.role.chatRole, listOf(ImagePart(image)), name)
            }
        }
    }
}

enum class Role(val chatRole: ChatRole) {
    USER(ChatRole.User),
    ASSISTANT(ChatRole.Assistant),
    SYSTEM(ChatRole.System);

    companion object {
        private val chatRoleToEnumMap = Role.entries.associateBy({ it.chatRole }, { it })

        fun from(role: ChatRole) = chatRoleToEnumMap[role]!!
    }
}
