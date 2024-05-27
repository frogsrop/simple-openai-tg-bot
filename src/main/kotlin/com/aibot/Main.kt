package com.aibot

import kotlinx.coroutines.*

fun main(args: Array<String>) {
    val manager = SqlChatHistoryManager.build("../../history")
    runBlocking {
        val res = manager.getMessages(2030771392)
        res.forEach{
            println("${it.name ?: "Megumin"}: ${it.content}")
        }
    }
}
