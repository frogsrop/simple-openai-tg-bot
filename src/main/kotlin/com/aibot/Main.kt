package com.aibot

import com.aallam.openai.api.BetaOpenAI
import kotlinx.coroutines.*

@OptIn(BetaOpenAI::class)
fun main(args: Array<String>) {
    val manager = ChatHistoryManager()
    runBlocking {
        println("Reading")
        manager.readCheckpoint()
        val res = manager.get(5721158050)
        res.forEach{
            println("${it.name ?: "Megumin"}: ${it.content}")
        }
    }
}
