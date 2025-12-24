package com.royna.tgbotclient.net

import com.google.gson.Gson
import com.royna.tgbotclient.datastore.ChatID
import com.royna.tgbotclient.net.crypto.AES
import com.royna.tgbotclient.net.crypto.SHA
import com.royna.tgbotclient.net.data.Command
import com.royna.tgbotclient.net.data.GenericAck
import com.royna.tgbotclient.net.data.GenericAckException
import com.royna.tgbotclient.net.data.Packet
import com.royna.tgbotclient.net.data.PayloadType
import com.royna.tgbotclient.net.io.IWriteChannel
import com.royna.tgbotclient.net.io.KtorIOAdapter
import com.royna.tgbotclient.util.Logging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import kotlinx.coroutines.Dispatchers
import java.io.File

class SocketContext {
    private suspend fun openConnection() : Connection {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().connect(mAddress)
        Logging.info("Waiting connection on $mAddress")
        val socket = serverSocket.connection()
        Logging.info("Connected")
        return socket
    }

    private suspend fun doOpenSession(channels: Connection) = runCatching {
        Logging.debug("Opened connection")
        var mSessionToken = String(ByteArray(Packet.Header.SESSION_TOKEN_LENGTH))

        Packet(
            header = Packet.Header(
                command = Command.CMD_OPEN_SESSION,
                payloadType = PayloadType.Binary,
                sessionToken = mSessionToken,
                initVector = AES.generateIV(),
                length = 0
            ),
            payload = null
        ).write(KtorIOAdapter(channels))

        val packet = Packet.read(KtorIOAdapter(channels)).getOrThrow()
        if (packet.header.command != Command.CMD_OPEN_SESSION_ACK) {
            Logging.error("Invalid response: ${packet.header.command}")
            throw RuntimeException("Invalid response")
        }
        Logging.debug("Read CMD_OPEN_SESSION_ACK")
        mSessionToken = packet.header.sessionToken
        Logging.debug("Got session token: $mSessionToken")
        mSessionToken
    }

    private suspend fun closeSession(channels: Connection, mSessionToken: String) {
        Packet(
            header = Packet.Header(
                command = Command.CMD_CLOSE_SESSION,
                payloadType = PayloadType.Binary,
                sessionToken = mSessionToken,
                initVector = AES.generateIV(),
                length = 0
            ),
            payload = null
        ).write(KtorIOAdapter(channels))
    }

    private suspend fun readGenericAck(channels: Connection) = runCatching {

        val packet = Packet.read(KtorIOAdapter(channels)).getOrThrow()
        require(packet.header.command == Command.CMD_GENERIC_ACK) {
            "Invalid response: ${packet.header.command}"
        }
        require (packet.payloadSize != 0)
        Logging.debug("Read generic ack")
        Logging.debug("Payload: ${packet.payload.decodeToString()}")
        GenericAck.fromJson(packet.payload.decodeToString())
    }

    enum class UploadOption(val value: Int) {
        MUST_NOT_EXIST(1),
        MUST_NOT_MATCH_CHECKSUM(2),
        ALWAYS(3)
    }

    data class SendMessageData(val chat: ChatID, val message: String)

    suspend fun sendMessage(chatId: Long, message: String) = runCatching {
        val channels = openConnection()
        val sessionToken = doOpenSession(channels).onFailure {
            Logging.error("Failed to open session", it)
        }.getOrThrow()

        val payload = Gson().toJson(SendMessageData(chatId, message)).toByteArray()
        Packet(
            Packet.Header(
                command = Command.CMD_WRITE_MSG_TO_CHAT_ID,
                payloadType = PayloadType.Json,
                length = payload.size,
                sessionToken = sessionToken,
                initVector = AES.generateIV(),
            ),
            payload = payload
        ).write(KtorIOAdapter(channels))
        readGenericAck(channels).onSuccess {
            if (!it.success()) {
                throw GenericAckException(it)
            }
        }.onFailure {
            Logging.error("Failed to read generic ack", it)
        }.getOrThrow()
        closeSession(channels, sessionToken)
    }

    data class GetUptimeData(val start_time: String, val current_time: String, val uptime: String)
    suspend fun getUptime() = runCatching {
         val channels = openConnection()
         val sessionToken = doOpenSession(channels).onFailure {
             Logging.error("Failed to open session", it)
         }.getOrThrow()

         Packet(
             header = Packet.Header (
                 command = Command.CMD_GET_UPTIME,
                 payloadType = PayloadType.Json,
                 length = 0,
                 sessionToken = sessionToken,
                 initVector = AES.generateIV()
             ),
             payload = null
         ).write(KtorIOAdapter(channels))

         var result: GetUptimeData? = null
         // Read the ACK
         runCatching {
             val packet = Packet.read(KtorIOAdapter(channels)).getOrThrow()
             require(packet.header.command == Command.CMD_GET_UPTIME_CALLBACK) {
                 "Invalid response: ${packet.header.command}"
             }
             require (packet.header.length != 0)
             Logging.debug("Read ${packet.header.command}")

             val str = packet.payload!!.decodeToString()
             Logging.debug("Payload: $str")
             result = Gson().fromJson(str, GetUptimeData::class.java)
             if (result == null) {
                 Logging.error("Failed to parse CMD_GET_UPTIME ack")
                 throw RuntimeException("Failed to parse CMD_GET_UPTIME ack")
             }
         }.onFailure {
             Logging.error("Failed to read CMD_GET_UPTIME ack", it)
         }.getOrThrow()
         closeSession(channels, sessionToken)
         result?.uptime
    }

    data class TransferFileData(val destfilepath: String, val srcfilepath: String, val hash: String,
                                val options: Options) {
        data class Options (
            val overwrite: Boolean,
            val hash_ignore: Boolean,
            var dry_run : Boolean
        )
    }
    data class TransferUploadData(val destfilepath: String, val srcfilepath: String, val options: Options) {
        data class Options (
            val overwrite: Boolean,
            val hash_ignore: Boolean,
            var dry_run : Boolean
        )
    }

    suspend fun uploadFile(sourcePath: File, destPath: String) = runCatching {
        val channels = openConnection()
        val sessionToken = doOpenSession(channels).onFailure {
            Logging.error("Failed to open session", it)
        }.getOrThrow()

        Logging.debug("File size: ${sourcePath.length()}")

        val (overwrite, hash_ignore) = when (mUploadOption) {
            UploadOption.MUST_NOT_EXIST -> Pair(false, false)
            UploadOption.MUST_NOT_MATCH_CHECKSUM -> Pair(true, false)
            UploadOption.ALWAYS -> Pair(true, true)
        }
        val fileBuf = sourcePath.readBytes()
        Logging.debug("File size: ${fileBuf.size}")

        val sha = SHA()
        // TODO: Split-compute sha
        sha.update(fileBuf)
        val hash = sha.done()
        Logging.debug("Hash: $hash")

        val data = TransferFileData(
            destfilepath = destPath,
            srcfilepath = sourcePath.absolutePath,
            hash = hash.toString(),
            options = TransferFileData.Options(
                overwrite = overwrite,
                hash_ignore = hash_ignore,
                dry_run = true
            )
        )

        val payload = Gson().toJson(data).toByteArray().also {
            Logging.debug("Payload: ${it.decodeToString()}")
        }

        val uploadDryPacket = Packet(
            header = Packet.Header(
                command = Command.CMD_TRANSFER_FILE,
                payloadType = PayloadType.Json,
                length = payload.size,
                sessionToken = sessionToken,
                initVector = AES.generateIV()
            ),
            payload = payload
        ).write(KtorIOAdapter(channels))
        Logging.debug("Wrote CMD_TRANSFER_FILE")

        readGenericAck(channels).onSuccess {
            if (it.success()) {
                data.options.dry_run = false
                val jsonPayload = Gson().toJson(data).toByteArray()
                Logging.debug("Payload: ${jsonPayload.decodeToString()}, size: ${jsonPayload.size}")
                val payloadCallback : suspend (channel: IWriteChannel) -> Unit = {
                    channel : IWriteChannel ->
                    channel.put(jsonPayload)
                    channel.putByte(0xFFu.toByte())
                    channel.put(fileBuf)
                }
                Packet(
                    header = Packet.Header(
                        command = Command.CMD_TRANSFER_FILE,
                        payloadType = PayloadType.Json,
                        length = jsonPayload.size + fileBuf.size + 1,
                        sessionToken = sessionToken,
                        initVector = AES.generateIV()
                    ),
                    payloadFn = payloadCallback,
                    payloadSize = jsonPayload.size + fileBuf.size + 1
                ).write(KtorIOAdapter(channels))
                Logging.debug("Wrote CMD_TRANSFER_FILE")
                readGenericAck(channels).onFailure { fail ->
                    Logging.error("Failed to read generic ack", fail)
                    throw fail
                }
            } else {
                throw GenericAckException(it)
            }
        }.onFailure {
            Logging.error("Failed to read generic ack", it)
            throw it
        }
        closeSession(channels, sessionToken)
    }

    suspend fun downloadFile(sourcePath: String, destPath: String) = runCatching {
        val channels = openConnection()
        val sessionToken = doOpenSession(channels).onFailure {
            Logging.error("Failed to open session", it)
        }.getOrThrow()

        val data = TransferUploadData(
            destfilepath = destPath,
            srcfilepath = sourcePath,
            options = TransferUploadData.Options(
                overwrite = false,
                hash_ignore = true,
                dry_run = true
            )
        )

        val sendData = Gson().toJson(data).toByteArray().also {
            Logging.debug("Payload: ${it.decodeToString()}")
        }
        Packet(
            Packet.Header(
                Command.CMD_TRANSFER_FILE_REQUEST,
                PayloadType.Json,
                sendData.size,
                sessionToken,
                AES.generateIV()
            ),
            sendData
        ).write(KtorIOAdapter(channels))
        Logging.debug("Wrote CMD_TRANSFER_FILE_REQUEST")

        val packet = Packet.read(KtorIOAdapter(channels)).getOrThrow()
        require(packet.header.command == Command.CMD_TRANSFER_FILE || packet.header.command == Command.CMD_GENERIC_ACK) {
            "Invalid response: ${packet.header.command}"
        }
        Logging.debug("Read ${packet.header.command}")

        when (packet.header.command) {
            Command.CMD_GENERIC_ACK -> {
                val ack = GenericAck.fromJson(packet.payload.decodeToString())
                if (!ack.success()) {
                    throw GenericAckException(ack)
                }
            }
            Command.CMD_TRANSFER_FILE -> {
                val jsonDataOffset = packet.payload.indexOfFirst {
                    it == 0xFF.toByte()
                }
                val jsonData = packet.payload.sliceArray(0 until jsonDataOffset)
                val fileData = packet.payload.sliceArray(jsonDataOffset + 1 until packet.payload.size)
                Logging.debug("Payload: ${jsonData.decodeToString()}")
                Logging.debug("File size: ${fileData.size}")
                File(destPath).writeBytes(fileData)
            }
            else -> {
                throw IllegalArgumentException("Invalid response")
            }
        }
        closeSession(channels, sessionToken)
    }
    fun setUploadFileOptions(options: UploadOption) {
        mUploadOption = options
    }

    var destination: InetSocketAddress
        get() = mAddress
        set(value) {
            mAddress = value
        }

    private var mAddress = InetSocketAddress("127.0.0.1", 0)
    private var mUploadOption = UploadOption.MUST_NOT_EXIST

    companion object {
        private var mInstance: SocketContext? = null
        fun getInstance(): SocketContext {
            if (mInstance == null) {
                mInstance = SocketContext()
            }
            return mInstance!!
        }
    }
}