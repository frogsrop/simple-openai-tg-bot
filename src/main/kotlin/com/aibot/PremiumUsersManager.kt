package com.aibot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.ConcurrentHashMap

class PremiumUsersManager {
    private val premiumUsersPath = "../../premium_users.txt"
    private val premiumUsers: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    suspend fun addUser(userId: Long) {
        premiumUsers.add(userId)
        saveUsers()
    }

    suspend fun removeUser(userId: Long) {
        premiumUsers.remove(userId)
        saveUsers()
    }

    fun getUsers(): List<Long> {
        return premiumUsers.toList()
    }

    fun checkPremium(userId: Long): Boolean {
        return premiumUsers.contains(userId)
    }

    suspend fun readUsers() {
        withContext(Dispatchers.IO) {
            FileInputStream(premiumUsersPath)
                .use { input ->
                    val inputStream = ObjectInputStream(input)
                    val users = inputStream.readObject() as List<Long>
                    premiumUsers.addAll(users)
                }
        }
    }

    private suspend fun saveUsers() {
        withContext(Dispatchers.IO) {
            FileOutputStream(premiumUsersPath)
                .use { output ->
                    val outputStream = ObjectOutputStream(output)
                    outputStream.writeObject(getUsers())
                }
        }
    }
}