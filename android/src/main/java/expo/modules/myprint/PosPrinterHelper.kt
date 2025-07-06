package expo.modules.myprint

class PosPrinterHelper {
    companion object {
        // Native methods from your SDK (add JNI bindings or Java wrapper as needed)
        external fun POS_Port_OpenA(portName: String, baudRate: Int, isSerial: Boolean, reserved: Any?): Long
        external fun POS_Port_Close(printerId: Long): Long
        external fun POS_Output_PrintStringA(printerId: Long, text: String): Long
        external fun POS_Output_PrintBmpDirectA(printerId: Long, bmpFilePath: String): Long
        external fun POS_Control_CutPaper(printerId: Long, cutType: Long, feedLines: Long): Long
        external fun POS_Control_FeedLines(printerId: Long, lines: Long): Long
        external fun POS_Status_RTQueryStatus(printerId: Long): Long

        // Status constants (from your SDK)
        const val POS_ES_SUCCESS = 0L
        const val POS_ES_PAPEROUT = 1L
        // ... add other status codes as needed
    }

    fun openPort(): Long {
        val printerId = POS_Port_OpenA("SP-USB1", 1002, false, null)
        if (printerId < 0) {
            POS_Port_Close(printerId)
            throw Exception("Failed to open printer port")
        }
        return printerId
    }

    fun printText(printerId: Long, text: String) {
        val result = POS_Output_PrintStringA(printerId, text)
        checkResult(result, "Print text")
    }

    fun printBitmap(printerId: Long, bmpFilePath: String) {
        val result = POS_Output_PrintBmpDirectA(printerId, bmpFilePath)
        checkResult(result, "Print bitmap")
    }

    fun cutPaper(printerId: Long, fullCut: Boolean = true, feedLines: Long = 3) {
        val result = POS_Control_CutPaper(printerId, if (fullCut) 1L else 0L, feedLines)
        checkResult(result, "Cut paper")
    }

    fun feedLines(printerId: Long, lines: Long) {
        val result = POS_Control_FeedLines(printerId, lines)
        checkResult(result, "Feed lines")
    }

    fun checkStatus(printerId: Long): Long {
        val status = POS_Status_RTQueryStatus(printerId)
        // You can interpret status here or in JS/TS
        return status
    }

    fun closePort(printerId: Long) {
        POS_Port_Close(printerId)
    }

    private fun checkResult(result: Long, action: String) {
        if (result != POS_ES_SUCCESS) {
            throw Exception("[31m$action failed with code $result[0m")
        }
    }
} 