package com.royna.tgbotclient.net.io
interface IReadChannel {
    suspend fun getLong(): Long
    suspend fun getInt(): Int
    suspend fun getShort(): Short
    suspend fun getByte(): Byte

    // Arrays ARE mutable, so passing them in to be filled is valid (like C++)
    suspend fun get(buffer: ByteArray)

    // Strings are immutable, so you must return them
    suspend fun getString(lengthBytes: Int): String
}