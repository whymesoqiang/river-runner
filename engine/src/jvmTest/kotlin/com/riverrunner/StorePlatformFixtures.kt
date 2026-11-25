package com.riverrunner

interface Settings {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
    /** Only read: version 0.2 stored some career totals as floats. */
    fun getFloat(key: String, default: Float): Float
}

/** Named text files in the app's private storage. */
interface TextFiles {
    fun exists(name: String): Boolean
    fun read(name: String): String?
    /** Replaces the file; on Android through a temporary file, so a crash never leaves half a file. */
    fun write(name: String, text: String)
    fun append(name: String, text: String)
    fun delete(name: String)
    fun rename(from: String, to: String)
}


inline fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)
