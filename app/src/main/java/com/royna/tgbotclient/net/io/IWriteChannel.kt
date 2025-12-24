package com.royna.tgbotclient.net.io

interface IWriteChannel {
    suspend fun putLong(data: Long)
    suspend fun putInt(data: Int)
    suspend fun putShort(data: Short)
    suspend fun putByte(data: Byte)
    suspend fun putString(data: String)
    suspend fun put(data: ByteArray)
}