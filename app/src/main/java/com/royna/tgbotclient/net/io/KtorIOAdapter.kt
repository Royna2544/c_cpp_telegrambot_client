package com.royna.tgbotclient.net.io

import io.ktor.network.sockets.Connection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readLong
import io.ktor.utils.io.readPacket
import io.ktor.utils.io.readShort
import io.ktor.utils.io.readText
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeLong
import io.ktor.utils.io.writeShort
import io.ktor.utils.io.writeStringUtf8

class KtorIOAdapter(connection: Connection) : IReadChannel, IWriteChannel {
    // Ktor splits connection into input (Read) and output (Write) channels
    private val input: ByteReadChannel = connection.input
    private val output: ByteWriteChannel = connection.output

    // --- READ OPERATIONS ---

    override suspend fun getLong(): Long {
        // Reads 8 bytes, converts to Long (Big Endian by default)
        return input.readLong()
    }

    override suspend fun getInt(): Int {
        return input.readInt()
    }

    override suspend fun getShort(): Short {
        return input.readShort()
    }

    override suspend fun getByte(): Byte {
        return input.readByte()
    }

    override suspend fun getString(lengthBytes: Int): String {
        val packet = input.readPacket(lengthBytes)
        return packet.readText()
    }

    override suspend fun get(buffer: ByteArray) {
        input.readFully(buffer)
    }

    // --- WRITE OPERATIONS ---

    override suspend fun putLong(data: Long) {
        output.writeLong(data)
        output.flush() // Flush ensures data is sent immediately
    }

    override suspend fun putInt(data: Int) {
        output.writeInt(data)
        output.flush()
    }

    override suspend fun putShort(data: Short) {
        output.writeShort(data)
        output.flush()
    }

    override suspend fun putByte(data: Byte) {
        output.writeByte(data)
        output.flush()
    }

    override suspend fun putString(data: String) {
        output.writeStringUtf8(data)
        output.flush()
    }

    override suspend fun put(data: ByteArray) {
        output.writeFully(data)
        output.flush()
    }

}