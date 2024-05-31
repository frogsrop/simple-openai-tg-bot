package com.aibot.domain.models

data class User(
    val userId: Long,
    val name: String,
    val permission: UserPermission,
    val model: Model
)

enum class Model(val id: String, val description: String) {
    GPT_3_5_TURBO(
        "gpt-3.5-turbo",
        """GPT-3.5 Turbo models can understand and generate natural language or code. Returns a maximum of 4,096 output tokens."""
    ),
    GPT_4(
        "gpt-4",
        """GPT-4 is a large multimodal model (accepting text or image inputs and outputting text) that can solve difficult problems with greater accuracy than any of our previous models, thanks to its broader general knowledge and advanced reasoning capabilities."""
    ),
    GPT_4_TURBO(
        "gpt-4-turbo",
        """GPT-4 Turbo with Vision. The latest GPT-4 Turbo model with vision capabilities."""
    ),
    GPT_4O(
        "gpt-4o",
        """GPT-4o (“o” for “omni”) is our most advanced model. It is multimodal (accepting text or image inputs and outputting text), and it has the same high intelligence as GPT-4 Turbo but is much more efficient—it generates text 2x faster and is 50% cheaper."""
    );
    companion object {
        private val idToEnumMap = Model.entries.associateBy({ it.id }, { it })

        fun from(id: String) = idToEnumMap[id]
    }
}

enum class UserPermission(
    val value: Int
) {
    DEFAULT(0),
    PREMIUM(1),
    ADMIN(2);

    companion object {
        private val valueToEnumMap = entries.associateBy({ it.value }, { it })

        fun from(value: Int) = valueToEnumMap[value]
    }
}