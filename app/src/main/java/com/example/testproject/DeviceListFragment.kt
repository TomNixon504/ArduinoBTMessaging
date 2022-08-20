package com.example.testproject

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import androidx.preference.PreferenceManager
import com.example.testproject.databinding.FragmentDeviceListBinding


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class DeviceListFragment : Fragment() {

    private val viewModel: ItemViewModel by activityViewModels()
    private var pairedDevices = ArrayList<BluetoothDevice>()
    private lateinit var bluetoothDevice: SharedPreferences
    private var _binding: FragmentDeviceListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            for (device in viewModel.currentList.value?.bondedDevices!!) {
                pairedDevices.add(device)
            }
        } catch (e : Exception) {
            // Alert that tells the user to connect to a bluetooth device
            //      only if there are no paired devices
            val dialog: AlertDialog.Builder = AlertDialog.Builder(context)
            dialog.setMessage("No possible connections\nPlease connect to a device")
            dialog.setTitle("No Devices Found")
            dialog.setNegativeButton("OK", null)
            val alertDialog = dialog.create()
            alertDialog.show()
        }
        bluetoothDevice = PreferenceManager.getDefaultSharedPreferences(requireContext())
        _binding = FragmentDeviceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val names = ArrayList<String>()

        for (device in pairedDevices) {
            names.add(device.name)
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
        binding.deviceList.adapter = adapter

        binding.deviceList.onItemClickListener = AdapterView.OnItemClickListener{_, _, position, _ ->
            val device: BluetoothDevice = pairedDevices[position]
            val name: String = device.name
            val address: String = device.address

            val editor = bluetoothDevice.edit()
            editor.putString("device_name", name)
            editor.putString("device_address", address)
            editor.commit()
            Toast.makeText(requireContext(), name, Toast.LENGTH_SHORT).show()
            findNavController(this).navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}