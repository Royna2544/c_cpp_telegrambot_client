package com.royna.tgbotclient.data

import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import tgbot.proto.socket.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class BotRepository {
    lateinit var service : SocketServiceGrpcKt.SocketServiceCoroutineStub


    // --- Simple Unary Calls ---

    suspend fun getBotInfo(): Result<SocketServiceOuterClass.BotInfo> = runCatching {
        service.info(Empty.getDefaultInstance())
    }

    suspend fun sendMessage(chatId: Long, text: String): Result<SocketServiceOuterClass.GenericResponse> = runCatching {
        val request = SocketServiceOuterClass.SendMessageRequest.newBuilder()
            .setChatId(chatId)
            .setText(text)
            .build()
        service.sendMessage(request)
    }

    // --- Complex File Upload (3-Step) ---

    suspend fun uploadFile(localFile: File, remotePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // STEP 1: Handshake
            val handshakeReq = SocketServiceOuterClass.FileTransferRequest.newBuilder()
                .setFilePath(remotePath)
                .setFileSize(localFile.length())
                .setIsUpload(true)
                .setOverwriteExisting(true)
                .build()

            val handshakeResp = service.requestFileTransfer(handshakeReq)
            if (!handshakeResp.accepted) {
                throw Exception("Server rejected upload: ${handshakeResp.rejectMessage}")
            }

            val uuid = handshakeResp.uuid
            val chunkSize = 64 * 1024 // 64KB

            // STEP 2: Streaming Loop
            // Create a Flow that emits FileChunks from the disk
            val fileFlow: Flow<SocketServiceOuterClass.FileChunk> = flow {
                val buffer = ByteArray(chunkSize)
                var chunkIndex = 0
                FileInputStream(localFile).use { input ->
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        val chunk = SocketServiceOuterClass.FileChunk.newBuilder()
                            .setUuid(uuid)
                            .setChunkIdx(chunkIndex++)
                            .setChunkData(ByteString.copyFrom(buffer, 0, bytesRead))
                            .build()
                        emit(chunk)
                        bytesRead = input.read(buffer)
                    }
                }
            }

            // Send the flow to the server
            service.uploadFileLoop(fileFlow).collect { resp ->
                if (!resp.success) throw Exception("Upload failed at chunk (Server Error)")
            }

            // STEP 3: Finalize
            val endReq = SocketServiceOuterClass.FileTransferRequest.newBuilder()
                .setUuid(uuid)
                .setFilePath(remotePath)
                .setIsUpload(true)
                .build()

            val endResp = service.endFileTransfer(endReq)
            if (endResp.code != SocketServiceOuterClass.GenericResponseCode.Success) {
                throw Exception("Finalize failed: ${endResp.message}")
            }

            "Upload Complete"
        }
    }

    // --- Complex File Download (3-Step) ---

    suspend fun downloadFile(remotePath: String, destFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // STEP 1: Handshake
            val handshakeReq = SocketServiceOuterClass.FileTransferRequest.newBuilder()
                .setFilePath(remotePath)
                .setIsUpload(false)
                .build()

            val handshakeResp = service.requestFileTransfer(handshakeReq)
            if (!handshakeResp.accepted) {
                throw Exception("Server rejected download")
            }

            val uuid = handshakeResp.uuid
            val chunkCount = handshakeResp.chunkCount

            // STEP 2: Streaming Loop
            // Generate requests for chunks 0..N
            val requestsFlow = flow {
                for (i in 0 until chunkCount) {
                    emit(
                        SocketServiceOuterClass.FileChunkRequest.newBuilder()
                            .setUuid(uuid)
                            .setChunkIdx(i)
                            .build()
                    )
                }
            }

            FileOutputStream(destFile).use { output ->
                // Bi-directional stream: We send requests, server sends data
                service.downloadFileLoop(requestsFlow).collect { resp ->
                    if (!resp.success) throw Exception("Download failed at chunk")
                    // Write directly to disk
                    resp.chunk.chunkData.writeTo(output)
                }
            }

            // STEP 3: Finalize
            val endReq = SocketServiceOuterClass.FileTransferRequest.newBuilder()
                .setUuid(uuid)
                .setFilePath(remotePath)
                .setIsUpload(false)
                .build()

            service.endFileTransfer(endReq)
            "Download Complete"
        }
    }

    data class InetAddress (
        val hostname: String,
        val port: Int
    )

    var destination = DefaultDestination
        set(value) {
            field = value
            service = SocketServiceGrpcKt.SocketServiceCoroutineStub(
                ManagedChannelBuilder.forAddress(value.hostname, value.port).usePlaintext().build())
        }

    init {
        // Trigger service update (dirty)
        destination = DefaultDestination
    }

    companion object {
        val DefaultDestination = InetAddress("127.0.0.1", 8080)

        private var mInstance: BotRepository? = null
        fun getInstance(): BotRepository {
            if (mInstance == null) {
                mInstance = BotRepository()
            }
            return mInstance!!
        }
    }
}