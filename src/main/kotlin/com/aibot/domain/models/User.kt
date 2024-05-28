package com.aibot.domain.models

data class User(
    val userId: Long,
    val name: String,
    val permission: UserPermission
)

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