package com.vaultzip.session

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryPasswordSessionStore @Inject constructor() : PasswordSessionStore {

    private val sessions = LinkedHashMap<String, CharArray>()

    override fun put(key: String, password: CharArray) {
        sessions[key]?.fill('\u0000')
        sessions[key] = password.copyOf()
    }

    override fun get(key: String): CharArray? {
        return sessions[key]?.copyOf()
    }

    override fun clear(key: String) {
        sessions.remove(key)?.fill('\u0000')
    }

    override fun clearAll() {
        sessions.values.forEach { it.fill('\u0000') }
        sessions.clear()
    }
}
