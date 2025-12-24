package com.royna.tgbotclient.net.io

class IOAdapterCombiner(private val adapters: List<IWriteChannel>) : IWriteChannel {
    override suspend fun putLong(data: Long) {
        adapters.forEach { it.putLong(data) }
    }

    override suspend fun putInt(data: Int) {
        adapters.forEach { it.putInt(data) }
    }

    override suspend fun putShort(data: Short) {
        adapters.forEach { it.putShort(data) }
    }

    override suspend fun putByte(data: Byte) {
        adapters.forEach { it.putByte(data) }
    }

    override suspend fun putString(data: String) {
        adapters.forEach { it.putString(data) }
    }

    override suspend fun put(data: ByteArray) {
        adapters.forEach { it.put(data) }
    }
}