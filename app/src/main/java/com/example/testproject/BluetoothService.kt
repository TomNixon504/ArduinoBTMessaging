package com.example.testproject

import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "DEBUG_TAG"
// Defines several constants used when transmitting messages between the
// service and the UI.
const val MESSAGE_RECEIVED: Int = 0
const val MESSAGE_SENDING: Int = 1
const val CREATE_TOAST: Int = 2

class BluetoothService(private val handler: Handler) {

    private lateinit var thread: ConnectedThread

    fun start(mmSocket: BluetoothSocket) {
        thread = ConnectedThread(mmSocket)
        thread.start()
    }

    fun send(data: Int) {
        thread.send(data)
    }

    fun stop() {
        thread.cancel()
    }

    /**
     * The thread that allows the BluetoothService to run in the background
     * @param mmSocket - the socket of the connected bluetooth device
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val bluetoothInStream: InputStream = mmSocket.inputStream
        private val bluetoothOutStream: OutputStream = mmSocket.outputStream
        private val inputBuffer: ByteArray = ByteArray(1024)

        /**
         * Runs the constant input listening
         */
        override fun run() {
            var inCount: Int

            // Always checking for bluetooth input from the connected device
            while (true) {
                // Read from the InputStream.
                inCount = try {
                    bluetoothInStream.read(inputBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Error: Input stream disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                val input = handler.obtainMessage(
                    MESSAGE_RECEIVED, inCount, -1,
                    inputBuffer)
                input.sendToTarget()
            }
        }

        /**
         * Sends data to the connected bluetooth device
         *      Call from the main activity
         * @param data - an Int containing the ASCII code for the char sent
         */
        fun send(data: Int) {
            try {
                bluetoothOutStream.write(data)
            } catch (e: IOException) {
                Log.e(TAG, "Error: Could not send data", e)

                // Send a failure message back to the activity.
                val errorMessage = handler.obtainMessage(CREATE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Error: Could not send data")
                }
                errorMessage.data = bundle
                handler.sendMessage(errorMessage)
                return
            }

            // Share the sent message with the UI activity.
            val sentData = handler.obtainMessage(
                MESSAGE_SENDING, -1, -1, inputBuffer)
            sentData.sendToTarget()
        }

        /**
         * Cancels the bluetooth connection
         *      Call from the main activity
         */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error: Could not close socket", e)
            }
        }
    }
}