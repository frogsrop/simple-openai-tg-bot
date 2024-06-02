package com.aibot.domain.models

data class User(
    val userId: Long,
    val name: String,
    val permission: UserPermission,
    val model: Model
) {
    companion object {

        fun buildDefault(id: Long, name: String): User = User(id, name, UserPermission.DEFAULT, Model.GPT_3_5_TURBO)
        fun buildAdmin(id: Long, name: String): User = buildDefault(id, name).copy(permission = UserPermission.ADMIN)

        fun buildPremium(id: Long, name: String): User = buildDefault(id, name).copy(permission = UserPermission.PREMIUM)}
}

data class UserInfo(val user: User, val usage: Float)

enum class Model(val id: String, val description: String, val imgModel: Boolean, val inputPrice: Float, val outputPrice: Float) {
    GPT_3_5_TURBO(
        "gpt-3.5-turbo",
        """0.5$ per 1M tokens, GPT-3.5 Turbo models can understand and generate natural language or code. Returns a maximum of 4,096 output tokens.""",
        false,
        0.5f,
        1.5f
    ),
    GPT_4(
        "gpt-4",
        """30$ per 1M tokens, GPT-4 is a large multimodal model (accepting text or image inputs and outputting text) that can solve difficult problems with greater accuracy than any of our previous models, thanks to its broader general knowledge and advanced reasoning capabilities.""",
        false,
        30f,
        60f
    ),
    GPT_4_TURBO(
        "gpt-4-turbo",
        """10$ per 1M tokens, GPT-4 Turbo with Vision. The latest GPT-4 Turbo model with vision capabilities.""",
        true,
        10f,
        30f
    ),
    GPT_4O(
        "gpt-4o",
        """5$ per 1M tokens, GPT-4o (“o” for “omni”) is our most advanced model. It is multimodal (accepting text or image inputs and outputting text), and it has the same high intelligence as GPT-4 Turbo but is much more efficient—it generates text 2x faster and is 50% cheaper.""",
        true,
        5f,
        15f
    );
    companion object {
        private val idToEnumMap = Model.entries.associateBy({ it.id }, { it })

        fun from(id: String) = idToEnumMap[id]
    }
}

enum class UserPermission {
    DEFAULT,
    PREMIUM,
    ADMIN;

    companion object {
        fun from(value: Int) = entries[value]
    }
}