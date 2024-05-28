package com.aibot.domain.models

import com.aallam.openai.api.chat.ChatRole

data class Message(
    val ts: Long,
    val userId: Long,
    val message: String,
    val role: Role
)

enum class Role(val chatRole: ChatRole) {
    USER(ChatRole.User),
    ASSISTANT(ChatRole.Assistant),
    SYSTEM(ChatRole.System);

    companion object {
        private val chatRoleToEnumMap = Role.entries.associateBy({ it.chatRole }, { it })

        fun from(role: ChatRole) = chatRoleToEnumMap[role]!!
    }
}
