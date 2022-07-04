package com.example.testproject

class Constants {

    companion object {
        // Tag for debugging
        const val TAG = "DEBUG_TAG"
        // Code to request to enable bluetooth
        const val REQUEST_ENABLE_BT = 1
        // Coded bytes for interpreting data sent and received
        const val SW1_CLOSED = 0x41 // 'A'
        const val SW1_OPEN = 0x45 // 'E'
        const val SW2_CLOSED = 0x42 // 'B'
        const val SW2_OPEN = 0x46 // 'F'
        const val REQUEST_LED1_ON = 0x4A // 'J'
        const val CONFIRM_LED1_ON = 0x4B // 'K'
        const val REQUEST_LED1_OFF = 0x4C // 'L'
        const val CONFIRM_LED1_OFF = 0x4D // 'M'
        const val REQUEST_LED2_ON = 0x4E // 'N'
        const val CONFIRM_LED2_ON = 0x4F // 'O'
        const val REQUEST_LED2_OFF = 0x50 // 'P'
        const val CONFIRM_LED2_OFF = 0x51 // 'Q'
        const val REQUEST_PASS_ON = 0x01 // 'SOH' - Start of Heading
        const val CONFIRM_PASS_ON = 0x02 // 'STX' - Start of Text
        const val REQUEST_PASS_OFF = 0x04 // 'EOT' - End of Transmission
        const val CONFIRM_PASS_OFF = 0x03 // 'ETX' - End of Text
    }
}