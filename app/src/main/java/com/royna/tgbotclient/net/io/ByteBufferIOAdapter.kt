package com.royna.tgbotclient.net.io

import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.bytestring.getByteString
import kotlinx.io.bytestring.putByteString
import java.nio.ByteBuffer

class ByteBufferIOAdapter(private val mSourceBuffer: ByteBuffer) : IReadChannel, IWriteChannel {
    override suspend fun getLong(): Long {
        return mSourceBuffer.getLong()
    }

    override suspend fun getInt(): Int {
        return mSourceBuffer.getInt()
    }

    override suspend fun getShort(): Short {
        return mSourceBuffer.getShort()
    }

    override suspend fun getByte(): Byte {
        return mSourceBuffer.get()
    }

    override suspend fun get(buffer: ByteArray) {
        mSourceBuffer.get(buffer)
    }

    override suspend fun getString(lengthBytes: Int): String {
        return mSourceBuffer.getByteString(lengthBytes).toString()
    }

    override suspend fun putLong(data: Long) {
        mSourceBuffer.putLong(data)
    }

    override suspend fun putInt(data: Int) {
        mSourceBuffer.putInt(data)
    }

    override suspend fun putShort(data: Short) {
        mSourceBuffer.putShort(data)
    }

    override suspend fun putByte(data: Byte) {
        mSourceBuffer.put(data)
    }

    override suspend fun putString(data: String) {
        mSourceBuffer.putByteString(data.encodeToByteString())
    }

    override suspend fun put(data: ByteArray) {
        mSourceBuffer.put(data)
    }
}