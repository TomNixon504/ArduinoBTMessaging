package com.example.testproject

class Constants {

    companion object {
        // Code to request to enable bluetooth
        const val REQUEST_ENABLE_BT = 1
        // Coded bytes for interpreting data sent and received
        const val CODE_SW1_CLOSED = 0x41 // 'A'
        const val CODE_SW1_OPEN = 0x45 // 'E'
        const val CODE_SW2_CLOSED = 0x42 // 'B'
        const val CODE_SW2_OPEN = 0x46 // 'F'
        const val CODE_REQUEST_LED1_ON = 0x4A // 'J'
        const val CODE_CONFIRM_LED1_ON = 0x4B // 'K'
        const val CODE_REQUEST_LED1_OFF = 0x4C // 'L'
        const val CODE_CONFIRM_LED1_OFF = 0x4D // 'M'
        const val CODE_REQUEST_LED2_ON = 0x4E // 'N'
        const val CODE_CONFIRM_LED2_ON = 0x4F // 'O'
        const val CODE_REQUEST_LED2_OFF = 0x50 // 'P'
        const val CODE_CONFIRM_LED2_OFF = 0x51 // 'Q'
        const val CODE_REQUEST_PASS_ON = 0x01 // 'SOH' - Start of Heading
        const val CODE_CONFIRM_PASS_ON = 0x02 // 'STX' - Start of Text
        const val CODE_REQUEST_PASS_OFF = 0x04 // 'EOT' - End of Transmission
        const val CODE_CONFIRM_PASS_OFF = 0x03 // 'ETX' - End of Text


        const val ACTION_RECEIVE_BLUETOOTH = "bluetooth receiver"

        const val ACTION_SW1_CLOSED = "SW1 closed"
        const val ACTION_SW1_OPEN = "SW1 open"
        const val ACTION_SW2_CLOSED = "SW2 closed"
        const val ACTION_SW2_OPEN = "SW2 open"
        const val ACTION_REQUEST_LED1_ON = "request LED1 on"
        const val ACTION_CONFIRM_LED1_ON = "confirm LED1 on"
        const val ACTION_REQUEST_LED1_OFF = "request LED1 off"
        const val ACTION_CONFIRM_LED1_OFF = "confirm LED1 off"
        const val ACTION_REQUEST_LED2_ON = "request LED2 on"
        const val ACTION_CONFIRM_LED2_ON = "confirm LED2 on"
        const val ACTION_REQUEST_LED2_OFF = "request LED2 off"
        const val ACTION_CONFIRM_LED2_OFF = "confirm LED2 off"
        const val ACTION_REQUEST_PASS_ON = "request PASS on"
        const val ACTION_CONFIRM_PASS_ON = "confirm PASS on"
        const val ACTION_REQUEST_PASS_OFF = "request PASS off"
        const val ACTION_CONFIRM_PASS_OFF = "confirm PASS off"
    }
}