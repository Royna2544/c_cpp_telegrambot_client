package com.royna.tgbotclient.net.data

import com.royna.tgbotclient.net.crypto.AES
import com.royna.tgbotclient.net.crypto.HMAC
import com.royna.tgbotclient.net.io.AESIOAdapter
import com.royna.tgbotclient.net.io.ByteBufferIOAdapter
import com.royna.tgbotclient.net.io.DEBUGIOAdapter
import com.royna.tgbotclient.net.io.HMACIOAdapter
import com.royna.tgbotclient.net.io.IOAdapterCombiner
import com.royna.tgbotclient.net.io.IReadChannel
import com.royna.tgbotclient.net.io.IWriteChannel
import com.royna.tgbotclient.util.Logging
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

data class Packet(var header: Header,
                  var payloadFn : suspend (channel: IWriteChannel) -> Unit,
                  var payloadSize : Int,
                  var hmac: ByteArray? = null) {
    data class Header(
        var command: Command,
        var payloadType: PayloadType,
        var length: Int,
        var sessionToken: String,
        var initVector: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Header

            if (length != other.length) return false
            if (command != other.command) return false
            if (payloadType != other.payloadType) return false
            if (sessionToken != other.sessionToken) return false
            if (!initVector.contentEquals(other.initVector)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = length
            result = 31 * result + command.hashCode()
            result = 31 * result + payloadType.hashCode()
            result = 31 * result + sessionToken.hashCode()
            result = 31 * result + initVector.contentHashCode()
            return result
        }

        /**
         * Returns a string representation of the header.
         */
        override fun toString(): String {
            return "Header(command=$command, payloadType=$payloadType, length=$length, sessionToken='${sessionToken.toByteArray()}')"
        }

        /**
         * Serializes the header to the given buffer.
         * @param buffer The buffer to serialize the header to.
         * @throws IllegalArgumentException if the header is invalid.
         *
         * Note: This function DOES NOT ALLOCATE BUFFER
         */
        suspend fun write(buffer: IWriteChannel) {
            // Validate hmac and initVector lengths
            require(initVector.size == AES.GCM_IV_LENGTH) { "Initialization Vector must be ${AES.GCM_IV_LENGTH} bytes" }
            require(sessionToken.length == SESSION_TOKEN_LENGTH) { "Session Token must be $SESSION_TOKEN_LENGTH bytes" }

            Logging.debug("Write Header: version: $HEADER_VERSION command: $command payloadType: $payloadType length: $length")

            // Write fields to buffer
            buffer.putLong(MAGIC + HEADER_VERSION)       // magic
            buffer.putInt(command.value)                 // command
            buffer.putInt(payloadType.value)             // payload type
            buffer.putInt(length)                       // length
            buffer.put(sessionToken.toByteArray())        // session token
            buffer.putInt(0)                            // padding 1
            buffer.putLong(System.currentTimeMillis())  // nonce
            buffer.put(initVector)                        // Initialization Vector
            buffer.putInt(0)                            // padding 2
        }

        suspend fun dump() {
            val debug = DEBUGIOAdapter()
            write(debug)
            debug.dump("Header")
        }

        companion object {
            const val MAGIC: Long = 0xDEADFACE
            const val HEADER_VERSION: Int = 13
            const val LENGTH = 80
            const val SESSION_TOKEN_LENGTH = 32

            /**
             * Deserializes the header from the given buffer.
             * @param buffer The buffer to deserialize the header from.
             * @return A result containing the deserialized header.
             */
            suspend fun read(buffer: IReadChannel) = runCatching {
                val magic = buffer.getLong()
                require(magic == MAGIC + HEADER_VERSION) { "Invalid magic number" }

                val command: Command = buffer.getEnumByValue { it.value }
                val payloadType: PayloadType = buffer.getEnumByValue { it.value }
                val length = buffer.getInt()
                val sessionToken = ByteArray(SESSION_TOKEN_LENGTH)
                buffer.get(sessionToken)
                buffer.getInt()  // padding 1
                buffer.getLong() // nonce
                val initVectorBuffer = ByteArray(AES.GCM_IV_LENGTH)
                buffer.get(initVectorBuffer)
                buffer.getInt() // padding 2

                Header(
                    command, payloadType, length, sessionToken.decodeToString(), initVectorBuffer
                )
            }
        }
    }

    /**
     * Serializes the header to the given buffer.
     * @param channel The buffer to serialize the packet to.
     * @throws IllegalArgumentException if the header is invalid.
     *
     * Note: This function DOES NOT ALLOCATE BUFFER
     */
    suspend fun write(channel: IWriteChannel) = runCatching {
        Logging.info("write++")
        dump()

        val mHMACBuilder = HMAC.ComputeBuilder(header.sessionToken.toByteArray())
        val hmac = HMACIOAdapter(mHMACBuilder)
        val hasKey = !header.sessionToken.all { it == 0.toChar() }

        // Second payload is appended
        Logging.info("write: HasKey: $hasKey payloadSize: $payloadSize header.length: ${header.length}")
        if (header.length != 0 && hasKey)  {
            val mAESBuilder = AES.EncryptBuilder(header.sessionToken.toByteArray(), header.initVector)
            val aes = AESIOAdapter.Encrypt(
                mAESBuilder,
                IOAdapterCombiner(listOf(channel, hmac))
            )
            header.length = payloadSize + AES.GCM_TAG_BYTES
            header.write(IOAdapterCombiner(listOf(channel, hmac)))
            payloadFn(aes)
            aes.finish()
        } else {
            header.write(IOAdapterCombiner(listOf(channel, hmac)))
            if (header.length > 0) {
                payloadFn(channel)
            }
        }

        // Third compute hmac
        if (hasKey) {
            Logging.debug("Non-empty session token")
            channel.put(mHMACBuilder.done())
        } else {
            Logging.debug("Empty session token")
            channel.put(ByteArray(HMAC.SHA256_LENGTH))
        }
        Logging.info("write--")
    }

    constructor(
        header: Header,
        payload: ByteArray?,
        hmac: ByteArray? = null) : this(header, {channel -> payload?.let {channel.put(it)}} ,
        payload?.size ?: 0, hmac)

    val payload : ByteArray
        get() {
            val byteBuf = ByteBuffer.allocate(payloadSize)
            runBlocking {
                payloadFn(ByteBufferIOAdapter(byteBuf))
            }
            return byteBuf.array()
        }

    companion object {
        private suspend inline fun <reified E : Enum<E>> IReadChannel.getEnumByValue(
            crossinline valueSelector: (E) -> Int
        ): E {
            val intValue = this.getInt()
            return enumValues<E>().find { valueSelector(it) == intValue }
                ?: throw IllegalArgumentException("Invalid enum ${E::class.simpleName} value: $intValue")
        }

        // Lock used below.
        private val obj = Object()

        /**
         * A function to dump hex data of a @code{ByteArray} object.
         * Uses a global lock to ensure thread safety.
         *
         * @param name The string representation of the buffer.
         * @param bytesPerLine The number of bytes to print per line. Default is 16.
         */
        fun ByteArray.hexdump(name: String, bytesPerLine: Int = 16) = synchronized(obj) {
            Logging.debug("Dump buffer $name. Size: ${this.size} bytes")
            this.hexdumpHelper(bytesPerLine) {
                Logging.debug(it)
            }
            Logging.debug("Dump buffer $name done")
        }

        fun ByteArray.hexdump(bytesPerLine: Int = 16) : String {
            val sb = StringBuilder()
            sb.append("") // Add a newline at the beginning
            hexdumpHelper(bytesPerLine) {
                sb.append(it)
            }
            return sb.toString()
        }

        private fun ByteArray.hexdumpHelper(bytesPerLine: Int = 16, consumer: (String) -> Unit) {
            for (i in indices step bytesPerLine) {
                val end = kotlin.math.min(i + bytesPerLine, size)
                // Create a sub-view for the current line
                val chunk = sliceArray(i until end)

                // 1. Print Offset (e.g., 00000010)
                val offsetStr = "%08x".format(i)

                // 2. Print Hex Bytes (e.g., 48 65 6C...)
                // We join the bytes, then add padding if the line is short
                val hexStr = chunk.joinToString(" ") { "%02x".format(it) }
                val padding = "   ".repeat(bytesPerLine - chunk.size)

                // 3. Print ASCII (e.g., Hel...)
                // Check if byte is printable (32..126), otherwise use '.'
                val asciiStr = chunk.map { byte ->
                    val uByte = byte.toInt() and 0xFF // Fix for signed bytes in JVM
                    if (uByte in 32..126) uByte.toChar() else '.'
                }.joinToString("")

                // Combine
                consumer("$offsetStr  $hexStr$padding  $asciiStr\n")
            }
        }

        /**
         * Deserializes the header from the given buffer.
         * @param readChannel The buffer to read the packet from.
         * @return A result containing the deserialized packet.
         */
        suspend fun read(readChannel: IReadChannel) = runCatching {
            Logging.debug("read++")

            // 1. Read header data.
            val headerBytes = ByteArray(Header.LENGTH)
            readChannel.get(headerBytes)
            Logging.debug("Read ${Header.LENGTH} bytes")

            // 2. Make it into Header object.
            val header = Header.read(ByteBufferIOAdapter(ByteBuffer.wrap(headerBytes))).getOrThrow()
            Logging.debug("Header: $header")

            // 3. Read payload.
            val DELIMIT = 1024
            val payloads = mutableListOf<ByteArray>()
            val last = header.length % DELIMIT
            for (i in 0 until (header.length / DELIMIT) + 1) {
                val payload = let {
                    if (i == (header.length / DELIMIT)) {
                        Logging.debug("Get last $last bytes")
                        ByteArray(last)
                    } else {
                        Logging.debug("Get next $DELIMIT bytes")
                        ByteArray(DELIMIT)
                    }
                }
                readChannel.get(payload)
                payloads += payload
            }

            // 4. Get HMAC
            val hmac = ByteArray(HMAC.SHA256_LENGTH)
            readChannel.get(hmac)
            Logging.debug("HMAC: ${hmac.hexdump()}")

            // 5. Match HMAC
            val compare = HMAC.CompareBuilder(header.sessionToken.toByteArray(), hmac)
            compare.update(headerBytes)
            for (payload in payloads) {
                compare.update(payload)
            }
            require(compare.done()) {
                "HMAC Mismatch"
            }
            Logging.debug("HMAC matched")

            val p = Packet(header, {write : IWriteChannel -> payloads.forEach { write.put(it) }}, header.length, hmac)
            p.let { packet ->
                packet.dump()
                val aes = AES.DecryptBuilder(header.sessionToken.toByteArray(), header.initVector)
                if (!header.sessionToken.all { it == 0x0.toChar() }) {
                    Logging.debug("Try Decrypt")
                    packet.payloadFn(AESIOAdapter.Decrypt(aes))
                    try {
                        val decrypted = aes.done()
                        Logging.debug("Decrypted: ${decrypted?.hexdump()}")
                        if (decrypted != null) {
                            packet.payloadSize = decrypted.size
                            packet.payloadFn = { write: IWriteChannel ->
                                write.put(decrypted)
                            }
                        }
                    } catch (e: Exception) {
                        Logging.error("Failed to decrypt", e)
                    }

                }
            }
            Logging.debug("read--")
            p
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (header != other.header) return false
        if (payloadSize != other.payloadSize) return false
        if (!hmac.contentEquals(other.hmac)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + payloadSize.hashCode()
        result = 31 * result + (hmac?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Packet(header=$header, payload=sz:${payloadSize}}, hmac=${hmac?.hexdump()})"
    }

    suspend fun dump() {
        Logging.debug("Dump packet++")
        Logging.debug(toString())
        header.dump()
        if (payloadSize <= 64)
            payload.hexdump("payload") // HEXDUMP adapter
        hmac?.hexdump("hmac")
        Logging.debug("Dump packet--")
    }
}