package com.vaultzip.session

interface PasswordSessionStore {
    fun put(key: String, password: CharArray)
    fun get(key: String): CharArray?
    fun clear(key: String)
    fun clearAll()
}
