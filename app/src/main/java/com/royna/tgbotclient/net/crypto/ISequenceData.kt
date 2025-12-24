package com.royna.tgbotclient.net.crypto

import com.royna.tgbotclient.util.Logging

// Interface to commonize
// update(), done() methods.
interface ISequenceData<T, V> {
    fun update(data: ByteArray, inputOffset: Int, inputLength : Int) : T
    fun update(data: ByteArray) : T
    fun done() : V

    /**
     * Verify if this @code{ByteArray| is suitable to be a input data.
     * Basically it checks if isEmpty() is false
     *
     * @throws IllegalArgumentException if the data is empty.
     */
    fun ByteArray.verifyInputData() {
        if (isEmpty()) {
            throw IllegalArgumentException("Data must not be empty")
        }
    }
}