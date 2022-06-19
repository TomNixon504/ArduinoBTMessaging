package com.example.testproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import com.example.testproject.databinding.FragmentControlBinding
import java.util.*

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
    // If a bluetooth device has been connected
    private var connected = false
    // The name of the default connection
    private var deviceName : String = "H-C-2010-06-01"

    private var _binding: FragmentControlBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var bluetoothService: BluetoothService
    private val receiver: BroadcastReceiver = BluetoothBroadcastReceiver()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION).apply {
            addAction(Constants.ACTION_RECEIVE_BLUETOOTH)
        }
        

        // Sets up bluetooth if possible on the device
        bluetoothSetUp()

        binding.passthroughButton.setOnClickListener {
            passRequested = true
            if(passEnabled) {
                bluetoothSend(Constants.CODE_REQUEST_PASS_OFF)
                bluetoothSend(Constants.CODE_REQUEST_PASS_OFF)
            }
            else {
                bluetoothSend(Constants.CODE_REQUEST_PASS_ON)
                bluetoothSend(Constants.CODE_REQUEST_PASS_ON)
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
                bluetoothSend(Constants.CODE_REQUEST_LED1_OFF)
            }
            else {
                // Sends a request to turn LED#1 off
                bluetoothSend(Constants.CODE_REQUEST_LED1_ON)
            }
        }
        binding.LED2Button.setOnClickListener {
            led2Requested = true
            if(led1Enabled) {
                // Sends a request to turn LED#2 off
                bluetoothSend(Constants.CODE_REQUEST_LED2_OFF)
            }
            else {
                // Sends a request to turn LED#2 off
                bluetoothSend(Constants.CODE_REQUEST_LED2_ON)
            }
        }

        binding.imageButton2.setOnClickListener {
            bluetoothSend(binding.textInput.text.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothClose()
        _binding = null
    }

    /**
     * Sets up bluetooth
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

        bluetoothConnect(bluetoothAdapter!!)

        if(connected) {
            bluetoothCommunication()
            bluetoothService = BluetoothService(Handler(Looper.getMainLooper()))
            bluetoothService.start(mmSocket)
        }
    }


    /**
     * Sends bluetooth data in the form of the hex codes
     * @param code - the Hex code
     */
    private fun bluetoothSend(code: Int) {
        if (!connected) {
            Toast.makeText(activity, "Not Connected", Toast.LENGTH_SHORT).show()
            return
        }
        else {
            bluetoothService.send(code)
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
                bluetoothService.send(byte.toInt())
            }

            binding.textSendRecieve.append(str)
            Toast.makeText(requireContext(), "Sending Data", Toast.LENGTH_SHORT).show()
        }
        else {
            Toast.makeText(
                activity, "Cancelled: Pass Through not enabled", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun bluetoothClose() {
        if(connected) {
            stopWorker = true

            bluetoothService.stop()
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
     * Checks to see if it is possible to enable bluetooth on the device then enables it
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

    @SuppressLint("MissingPermission")
    private fun bluetoothConnect(bluetoothAdapter: BluetoothAdapter) {
        val bluetoothDevices = bluetoothAdapter.bondedDevices

        if (bluetoothDevices.isNotEmpty()) {
            for (device in bluetoothDevices) {
                // Automatically connects to the previously connected device
                //      unless it isn't currently paired
                if (device.name == deviceName) {
                    mmDevice = device
                    connected = true
                    Toast.makeText(requireContext(), "Bluetooth Device Found", Toast.LENGTH_LONG).show()
                    break
                }
            }
        }
        if(!connected) {
            // Alert that tells the user to connect to a bluetooth device
            //      only if there are no paired devices
            val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
            dialog.setMessage("No possible connections\nPlease connect to a device")
            dialog.setTitle("No Devices Found")
            dialog.setNegativeButton("OK", null)
            val alertDialog = dialog.create()
            alertDialog.show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothCommunication() {
        val uuid: UUID = mmDevice.uuids[0].uuid
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid)
        mmSocket.connect()
    }

//    private fun bluetoothReceiveData() {
//
//        val bluetoothReceiverThread = Thread {
//            fun run() {
//                val buffer = ByteArray(1024)
//                var numBytes: Int
//                while (!Thread.currentThread().isInterrupted && stopWorker) {
//                    try {
//                        if (mmInputStream.available() != 0) {
//                            try {
//                                numBytes = mmInputStream.read(buffer)
//                                for (i in 0..numBytes) {
//                                    processData(buffer[i])
//                                    displayChanges()
//                                    binding.textSendRecieve.append(buffer[i].toInt().toString())
//                                }
//                            } catch (ex: IOException) {
//                                stopWorker = true
//                            }
//                        }
//                    } catch (ex: IOException) {
//                        stopWorker = true
//                    }
//                }
//            }
//        }
//
//        bluetoothReceiverThread.start()

//        stopWorker = false
//        val workerThread = Thread {
//            Looper.prepare()
//            while (!Thread.currentThread().isInterrupted && !stopWorker) {
//                try {
//                    val input = mmInputStream.readBytes()
//                    for (i in input.indices) {
//                        val b = input[i]
//                        requireActivity().runOnUiThread {
//                            processData(b)
//                            displayChanges()
//                        }
//                        binding.textSendRecieve.append(b.toInt().toString())
//                    }
//                } catch (ex: IOException) {
//                    stopWorker = true
//                }
//            }
//        }
//        workerThread.start()
//    }

    private fun processData(byte: Byte) {

        when(byte.toInt()) {
            Constants.CODE_SW1_CLOSED -> {
                sw1Enabled = false
            }
            Constants.CODE_SW1_OPEN -> {
                sw1Enabled = true
            }

            Constants.CODE_SW2_CLOSED -> {
                sw2Enabled = false
            }
            Constants.CODE_SW2_OPEN -> {
                sw2Enabled = true
            }

            Constants.CODE_CONFIRM_LED1_ON -> {
                if (led1Requested) {
                    led1Enabled = true
                    led1Requested = false
                } else {
                    communicationError()
                    led1Requested = false
                }
            }
            Constants.CODE_CONFIRM_LED1_OFF -> {
                if (led1Requested) {
                    led1Enabled = false
                    led1Requested = false
                } else {
                    communicationError()
                    led1Requested = false
                }
            }

            Constants.CODE_CONFIRM_LED2_ON -> {
                if (led2Requested) {
                    led2Enabled = true
                    led2Requested = false
                } else {
                    communicationError()
                    led2Requested = false
                }
            }

            Constants.CODE_CONFIRM_LED2_OFF -> {
                if (led2Requested) {
                    led2Enabled = false
                    led2Requested = false
                } else {
                    communicationError()
                    led2Requested = false
                }
            }

            Constants.CODE_CONFIRM_PASS_ON -> {
                if (passRequested) {
                    passEnabled = true
                    passRequested = false
                } else {
                    communicationError()
                    passRequested = false
                }
            }

            Constants.CODE_CONFIRM_PASS_OFF -> {
                if (passRequested) {
                    passEnabled = false
                    passRequested = false
                }
                else {
                    communicationError()
                    passRequested = false
                }
            }

            else -> {
                communicationError()
            }
        }

    }

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

    private fun communicationError() {
        //TODO: figure out what to do on communication error
        val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
        dialog.setMessage("Communications Error between the Arduino and the phone")
        dialog.setTitle("IO ERROR")
        dialog.setNegativeButton("OK", null)
        val alertDialog = dialog.create()
        alertDialog.show()
    }
}