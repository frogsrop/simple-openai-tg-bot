package com.aibot.domain.models

import com.aallam.openai.api.chat.ChatRole

data class Message(
    val ts: Long,
    val userId: Long,
    val message: String,
    val role: ChatRole
)