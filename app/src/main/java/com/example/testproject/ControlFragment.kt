package com.example.testproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.example.testproject.databinding.FragmentControlBinding
import java.io.IOException
import java.io.OutputStream
import java.lang.Thread.sleep
import java.util.*
import kotlin.collections.ArrayList

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ControlFragment : Fragment() {

    // Booleans to know if the respective element is enabled or disabled
    private var sw1Enabled = false
    private var sw2Enabled = false
    private var led1Enabled = false
    private var led2Enabled = false
    private var passEnabled = false
    // Booleans to confirm if an action has been requested
    private var led1Requested = false
    private var led2Requested = false
    private var passRequested = false
    // Bluetooth management
    private lateinit var mmDevice : BluetoothDevice
    private lateinit var mmSocket : BluetoothSocket
    private var stopWorker = false
    private var commsError = false
    // If a bluetooth device has been connected/paired
    private var connected = false
    private var paired = false
    // The name of the default connection
    private lateinit var deviceName : String
    private lateinit var deviceAddress: String

    private val viewModel: ItemViewModel by activityViewModels()
    private var pairedDevices = ArrayList<BluetoothDevice>()
    private lateinit var bluetoothDevice: SharedPreferences

    private var _binding: FragmentControlBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var receiveDataThread: ReceiveDataThread
    private lateinit var sendDataThread: SendDataThread
    private lateinit var timeOutThread: TimeOutThread

    private lateinit var btHandler: Handler

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        btHandler = Handler(Looper.getMainLooper())
        bluetoothDevice = PreferenceManager.getDefaultSharedPreferences(requireContext())
        deviceName = bluetoothDevice.getString("device_name", "0").toString()
        deviceAddress = bluetoothDevice.getString("device_address", "0").toString()
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Sets up bluetooth if possible on the device
        bluetoothSetUp()

        timeOutThread = TimeOutThread()
        timeOutThread.start()

        binding.passthroughButton.setOnClickListener {
            passRequested = true
            if(passEnabled) {
                bluetoothSend(Constants.REQUEST_PASS_OFF)
                bluetoothSend(Constants.REQUEST_PASS_OFF)
            }
            else {
                bluetoothSend(Constants.REQUEST_PASS_ON)
                bluetoothSend(Constants.REQUEST_PASS_ON)
            }
        }
        binding.SW1Button.setOnClickListener {
            Toast.makeText(context, "SW-1 Is not controlled in application", Toast.LENGTH_SHORT).show()

        }
        binding.SW2Button.setOnClickListener {
            Toast.makeText(context, "SW-2 Is not controlled in application", Toast.LENGTH_SHORT).show()

        }
        binding.LED1Button.setOnClickListener {
            led1Requested = true
            if(led1Enabled) {
                // Sends a request to turn LED#1 off
                bluetoothSend(Constants.REQUEST_LED1_OFF)
            }
            else {
                // Sends a request to turn LED#1 on
                bluetoothSend(Constants.REQUEST_LED1_ON)
            }
        }
        binding.LED2Button.setOnClickListener {
            led2Requested = true
            if(led2Enabled) {
                // Sends a request to turn LED#2 off
                bluetoothSend(Constants.REQUEST_LED2_OFF)
            }
            else {
                // Sends a request to turn LED#2 on
                bluetoothSend(Constants.REQUEST_LED2_ON)
            }
        }
        // Pass through send
        binding.imageButton2.setOnClickListener {
            bluetoothSend(binding.textInput.text.toString())
        }
    }

    override fun onResume() {
        deviceName = bluetoothDevice.getString("device_name", "0").toString()
        deviceAddress = bluetoothDevice.getString("device_address", "0").toString()
        super.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothClose()
        viewModel.clearList()
        _binding = null
    }

    /**
     * Sets up bluetooth by creating a bluetooth socket and colling related functions
     */
    @SuppressLint("MissingPermission")
    private fun bluetoothSetUp() {
        bluetoothEnable()
        val bluetoothManager = getSystemService(requireContext(), BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        // If the adapter is null the device doesn't support bluetooth
        if(!bluetoothActivate(bluetoothAdapter)) {
            Toast.makeText(requireContext(),"This device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        bluetoothPairDevice(bluetoothAdapter!!)

        if(paired) {
            bluetoothConnect()
            if (connected) {
                receiveDataThread = ReceiveDataThread(mmSocket)
                sendDataThread = SendDataThread(mmSocket)
                receiveDataThread.start()
                sendDataThread.start()
            }
            else {
                val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
                dialog.setMessage("Could not connect to device $deviceName")
                dialog.setTitle("Connection Failed")
                dialog.setNegativeButton("OK", null)
                val alertDialog = dialog.create()
                alertDialog.show()
            }
        }
    }

    /**
     * Sends bluetooth data in the form of the hex codes
     *      Only used when pass through is disabled
     * @param code - the Hex code
     */
    private fun bluetoothSend(code: Int) {
        if (!connected) {
            Toast.makeText(activity, "Not Connected", Toast.LENGTH_SHORT).show()
            return
        }
        else {
            timeOutThread.sentData()
            sendDataThread.send(code)
            binding.textSendRecieve.append(code.toChar().toString())
        }
    }

    /**
     * Sends bluetooth data in the form of a byte array from a string
     *      Only works if Pass Through is enabled
     * @param str - A String that will be turned into a ByteArray
     */
    private fun bluetoothSend(str: String) {
        if (!connected) {
            Toast.makeText(activity, "Not Connected", Toast.LENGTH_SHORT).show()
            return
        }
        else if (passEnabled) {
            val message = str.toByteArray()
            for (byte in message) {
                sendDataThread.send(byte.toInt())
            }
            binding.textSendRecieve.append(str)
            Toast.makeText(requireContext(), "Sending Data", Toast.LENGTH_SHORT).show()
        }
        else {
            Toast.makeText(
                activity, "Cancelled: Pass Through not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Closes all bluetooth communication threads and closes the bluetooth socket
     */
    private fun bluetoothClose() {
        if(connected) {
            stopWorker = true
            sendDataThread.cancel()
            receiveDataThread.cancel()
            mmSocket.close()
        }
    }

    /**
     * Requests bluetooth permissions if not already granted
     *      Bluetooth permissions are automatically granted most of the time
     */
    private fun bluetoothEnable() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (activity != null) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                    Constants.REQUEST_ENABLE_BT
                )
            }
        }
    }

    /**
     * Checks to see if it is possible to enable bluetooth on the device then activates it
     * @param bluetoothAdapter - the devices bluetooth adapter information
     * @return - returns true if bluetooth is active on this device otherwise false
     */
    private fun bluetoothActivate(bluetoothAdapter: BluetoothAdapter?) : Boolean {
        if (bluetoothAdapter == null) {
            return false
        }
        // Request bluetooth enable if not
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ContextCompat.startActivity(requireContext(), enableBtIntent, null)
        }
        return true
    }

    /**
     * Parses through all paired devices to find the saved device
     *      if saved device does not exist tells used to select one from DeviceListFragment
     */
    @SuppressLint("MissingPermission")
    private fun bluetoothPairDevice(bluetoothAdapter: BluetoothAdapter) {
        viewModel.selectList(bluetoothAdapter)
        val bluetoothDevices = bluetoothAdapter.bondedDevices

        if (bluetoothDevices.isNotEmpty()) {
            for (device in bluetoothDevices) {
                // Automatically connects to the previously connected device
                //      unless it isn't currently paired
                if (device.name == deviceName && device.address == deviceAddress) {
                    mmDevice = device
                    paired = true
                }
                pairedDevices.add(device)
            }
        }

        if(!paired) {
            // Alert that tells the user to connect to a bluetooth device
            //      only if there are no paired devices
            val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
            dialog.setMessage("Could not find selected device.\nPlease select another device")
            dialog.setTitle("Device Not Found")
            dialog.setNegativeButton("OK") { _, _ ->
                NavHostFragment.findNavController(this)
                    .navigate(R.id.action_FirstFragment_to_SecondFragment)
            }
            val alertDialog = dialog.create()
            alertDialog.show()
        }
    }

    /**
     * Attempts to connect to the selected bluetooth device
     */
    @SuppressLint("MissingPermission")
    private fun bluetoothConnect() {
        val uuid: UUID = mmDevice.uuids[0].uuid
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid)
        connected = try {
            mmSocket.connect()
            true
        } catch (e : Exception) {
            false
        }
    }

    /**
     * The thread that has control over sending data
     * @param btSocket - The bluetooth socket to create the outputStream
     */
    private inner class SendDataThread(private val btSocket: BluetoothSocket) : Thread() {
        private val bluetoothOutStream: OutputStream = mmSocket.outputStream
        /**
         * Sends data to the connected bluetooth device
         *      Call from the main activity
         * @param data - an Int containing the ASCII code for the char sent
         */
        fun send(data: Int) {
            try {
                bluetoothOutStream.write(data)
            } catch (e: IOException) {
                btHandler.post {
                    communicationError()
                }
                return
            }
        }
        /**
         * Cancels the bluetooth connection
         */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(Constants.TAG, "Error: Could not close socket", e)
            }
        }
    }

    /**
     * The thread that has control over the constant listening for received bluetooth data
     * @param btSocket - The bluetooth socket to create the inputStream
     */
    private inner class ReceiveDataThread(private val btSocket: BluetoothSocket) : Thread() {
        /**
         * Runs the constant input listening and processing
         */
        override fun run() {
            //TODO FIX BUG: either here or in the arduino code failure to send/receive coherent data
            val inputStream = btSocket.inputStream
            val buffer = ByteArray(1024)
            var bytes = 0
            while (true) {
                try {
                    bytes = inputStream.read(buffer, bytes, 1024 - bytes)

                    for (i in 0 until bytes) {
                        btHandler.post {
                            processData(buffer[i].toInt())
                            displayChanges()
                            if(!commsError) {binding.textSendRecieve.append(buffer[i].toInt().toChar().toString())}
                            if(!passEnabled) { binding.textSendRecieve.append("\n") }
                        }
                    }
                    bytes = 0
                } catch (e :IOException) {
                    break
                }
            }
        }
        /**
         * Cancels the bluetooth connection
         */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(Constants.TAG, "Error: Could not close socket", e)
            }
        }
    }

    /**
     * Checks to see if the app has received any data in 2 seconds from any data sent
     */
    private inner class TimeOutThread : Thread() {
        //TODO : Creates a bug where you cant receive data
        var received = false

        fun sentData() {
            received = false
//            Timer().schedule(timerTask {
//                if(!received) {
//                    communicationError()
//                }
//            }, 2000)
//            val start = LocalTime.now()
//            while (true) {
//                if (received) {
//                    break
//                }
//                if (LocalTime.now().toSecondOfDay() - start.toSecondOfDay() >= 200) {
//                    communicationError()
//                    break
//                }
//            }
        }

        fun received() {
            received = true
        }
    }

    /**
     * Processes all incoming data from the arduino
     */
    private fun processData(code: Int) {
        timeOutThread.received()
        if(!passEnabled && !commsError) {
            when(code) {
                Constants.SW1_CLOSED -> { sw1Enabled = false }
                Constants.SW1_OPEN -> { sw1Enabled = true }

                Constants.SW2_CLOSED -> { sw2Enabled = false }
                Constants.SW2_OPEN -> { sw2Enabled = true }

                Constants.CONFIRM_LED1_ON -> {
                    if (led1Requested) {
                        led1Enabled = true
                        led1Requested = false
                    } else {
                        communicationError()
                        led1Requested = false
                    }
                }
                Constants.CONFIRM_LED1_OFF -> {
                    if (led1Requested) {
                        led1Enabled = false
                        led1Requested = false
                    } else {
                        communicationError()
                        led1Requested = false
                    }
                }

                Constants.CONFIRM_LED2_ON -> {
                    if (led2Requested) {
                        led2Enabled = true
                        led2Requested = false
                    } else {
                        communicationError()
                        led2Requested = false
                    }
                }

                Constants.CONFIRM_LED2_OFF -> {
                    if (led2Requested) {
                        led2Enabled = false
                        led2Requested = false
                    } else {
                        communicationError()
                        led2Requested = false
                    }
                }

                Constants.CONFIRM_PASS_ON -> {
                    if (passRequested) {
                        passEnabled = true
                        passRequested = false
                    } else {
                        communicationError()
                        passRequested = false
                    }
                }

                else -> { communicationError() }
            }
        }
        else if(!commsError) {
            if (code == Constants.CONFIRM_PASS_OFF) {
                if (passRequested) {
                    passEnabled = false
                    passRequested = false
                }
                else {
                    communicationError()
                    passRequested = false
                }
            }
        }
    }

    /**
     * Displays changes in UI status due to confirmation from Arduino
     */
    private fun displayChanges() {
        if(!commsError) {
            if(led1Enabled) {
                binding.LED1Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                binding.LED1Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(led2Enabled) {
                binding.LED2Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                binding.LED2Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(sw1Enabled) {
                binding.SW1Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                binding.SW1Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(sw2Enabled) {
                binding.SW2Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                binding.SW2Indicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
            if(passEnabled) {
                binding.passthroughIndicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_checked_mtrl)
            } else {
                binding.passthroughIndicator.setImageResource(
                    androidx.appcompat.R.drawable.btn_checkbox_unchecked_mtrl)
            }
        }
    }

    /**
     * The process of notifying the user that their has been a communications error
     *      between the phone and arduino
     */
    private fun communicationError() {
        commsError = true
        blinkLED()
        val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
        dialog.setMessage("Communications Error between the Arduino and the phone")
        dialog.setTitle("IO ERROR")
        dialog.setNegativeButton("OK") { _, _ -> commsError = false }
        val alertDialog = dialog.create()
        alertDialog.show()
    }

    /**
     * Flashes the LEDs twice with a 1/2 second wait in-between each flash
     */
    private fun blinkLED() {

        sendDataThread.send(Constants.REQUEST_LED1_OFF)
        sendDataThread.send(Constants.REQUEST_LED2_OFF)

        sendDataThread.send(Constants.REQUEST_LED1_ON)
        sendDataThread.send(Constants.REQUEST_LED2_ON)

        sleep(500)

        sendDataThread.send(Constants.REQUEST_LED1_OFF)
        sendDataThread.send(Constants.REQUEST_LED2_OFF)

        sleep(500)

        sendDataThread.send(Constants.REQUEST_LED1_ON)
        sendDataThread.send(Constants.REQUEST_LED2_ON)

        sleep(500)

        sendDataThread.send(Constants.REQUEST_LED1_OFF)
        sendDataThread.send(Constants.REQUEST_LED2_OFF)
    }
}
