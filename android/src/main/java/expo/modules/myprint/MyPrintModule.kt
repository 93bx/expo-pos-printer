package expo.modules.myprint

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL
import java.io.ByteArrayOutputStream
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.app.PendingIntent
import android.hardware.usb.UsbConstants

class MyPrintModule : Module() {
  private var usbPermissionReceiver: BroadcastReceiver? = null
  private var receiverRegistered = false
  private val USB_PERMISSION_ACTION = "expo.modules.myprint.USB_PERMISSION"

  override fun definition() = ModuleDefinition {
    Name("MyPrint")

    Events("onChange")

    AsyncFunction("setValueAsync") { value: String ->
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    AsyncFunction("discoverUsbPrinters") {
      val context = appContext.reactContext
      if (context != null && usbPermissionReceiver == null && !receiverRegistered) {
        usbPermissionReceiver = object : BroadcastReceiver() {
          override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == USB_PERMISSION_ACTION) {
              val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
              val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
              android.util.Log.d("MyPrintModule", "USB_PERMISSION result: device=${device?.deviceName}, granted=$granted")
              if (granted && device != null) {
                sendEvent("onChange", mapOf("usbPermissionGranted" to true, "deviceId" to device.deviceId))
              } else {
                sendEvent("onChange", mapOf("usbPermissionGranted" to false))
              }
            }
          }
        }
        val filter = IntentFilter(USB_PERMISSION_ACTION)
        val flags = if (android.os.Build.VERSION.SDK_INT >= 33) Context.RECEIVER_EXPORTED else 0
        context.registerReceiver(usbPermissionReceiver, filter, flags)
        receiverRegistered = true
      }
      val usbManager = context?.getSystemService(Context.USB_SERVICE) as? UsbManager
      if (usbManager == null) {
        android.util.Log.d("MyPrintModule", "UsbManager is null")
        return@AsyncFunction emptyList<Map<String, Any>>()
      }
      val deviceList = usbManager.deviceList.values
      val result = mutableListOf<Map<String, Any>>()
      val permissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, usbPermissionReceiver?.javaClass ?: BroadcastReceiver::class.java).setAction(USB_PERMISSION_ACTION),
        if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
      )
      for (device in deviceList) {
        if (!usbManager.hasPermission(device)) {
          android.util.Log.d("MyPrintModule", "Requesting permission for device: ${device.deviceName}")
          usbManager.requestPermission(device, permissionIntent)
          continue
        }
        val info = mutableMapOf<String, Any>(
          "deviceId" to device.deviceId,
          "vendorId" to device.vendorId,
          "productId" to device.productId,
          "deviceName" to device.deviceName
        )
        result.add(info)
      }
      android.util.Log.d("MyPrintModule", "Discovered devices: ${result.size}")
      return@AsyncFunction result
    }

    AsyncFunction("printTestPage") { deviceId: Int ->
      val context = appContext.reactContext
      if (context == null) {
        android.util.Log.e("MyPrintModule", "Context is null")
        throw Exception("Context is null")
      }
      val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
      if (usbManager == null) {
        android.util.Log.e("MyPrintModule", "UsbManager is null")
        throw Exception("UsbManager is null")
      }
      val device = usbManager.deviceList.values.find { it.deviceId == deviceId }
      if (device == null) {
        android.util.Log.e("MyPrintModule", "Device not found: $deviceId")
        throw Exception("Device not found: $deviceId")
      }
      if (!usbManager.hasPermission(device)) {
        android.util.Log.e("MyPrintModule", "No permission for device: $deviceId")
        throw Exception("No permission for device: $deviceId")
      }
      val connection = usbManager.openDevice(device)
      if (connection == null) {
        android.util.Log.e("MyPrintModule", "Failed to open device connection")
        throw Exception("Failed to open device connection")
      }
      try {
        val usbInterface = device.getInterface(0)
        val endpoint = (0 until usbInterface.endpointCount)
          .map { usbInterface.getEndpoint(it) }
          .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (endpoint == null) {
          android.util.Log.e("MyPrintModule", "No OUT endpoint found")
          throw Exception("No OUT endpoint found")
        }
        connection.claimInterface(usbInterface, true)
        val initialize = byteArrayOf(0x1B, 0x40)
        connection.bulkTransfer(endpoint, initialize, initialize.size, 2000)
        val builder = ByteArrayOutputStream()
        builder.write(byteArrayOf(0x1B, 0x40))
        builder.write(byteArrayOf(0x1B, 0x64, 0x02))
        builder.write(byteArrayOf(0x1B, 0x61, 0x01))
        builder.write(byteArrayOf(0x1D, 0x21, 0x11))
        builder.write("Hello World\n".toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x0A, 0x1D, 0x21, 0x00, 0x1B, 0x61, 0x00))
        builder.write(byteArrayOf(0x1B, 0x64, 0x04))
        builder.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        connection.bulkTransfer(endpoint, builder.toByteArray(), builder.size(), 2000)
        val feedAndCut = byteArrayOf(0x1D, 0x56, 0x00)
        connection.bulkTransfer(endpoint, feedAndCut, feedAndCut.size, 2000)
        connection.releaseInterface(usbInterface)
        connection.close()
        android.util.Log.d("MyPrintModule", "Test print sent successfully")
        return@AsyncFunction true
      } catch (e: Exception) {
        connection.close()
        android.util.Log.e("MyPrintModule", "Test print failed: ${e.message}")
        throw e
      }
    }

    AsyncFunction("printReceipt") { deviceId: Int, jsonReceipt: String ->
      val context = appContext.reactContext
      if (context == null) throw Exception("Context is null")
      val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
      if (usbManager == null) throw Exception("UsbManager is null")
      val device = usbManager.deviceList.values.find { it.deviceId == deviceId }
      if (device == null) throw Exception("Device not found: $deviceId")
      if (!usbManager.hasPermission(device)) throw Exception("No permission for device: $deviceId")
      val connection = usbManager.openDevice(device) ?: throw Exception("Failed to open device connection")
      try {
        val usbInterface = device.getInterface(0)
        val endpoint = (0 until usbInterface.endpointCount)
          .map { usbInterface.getEndpoint(it) }
          .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (endpoint == null) throw Exception("No OUT endpoint found")
        connection.claimInterface(usbInterface, true)
        val builder = ByteArrayOutputStream()
        builder.write(byteArrayOf(0x1B, 0x40))
        run {
          val widthPx = 240
          val heightPx = 80
          val bmp = android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
          val canvas = android.graphics.Canvas(bmp)
          val paint = android.graphics.Paint()
          paint.color = android.graphics.Color.BLACK
          canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)
          paint.color = android.graphics.Color.WHITE
          paint.textSize = 48f
          paint.textAlign = android.graphics.Paint.Align.CENTER
          val xPos = widthPx / 2f
          val yPos = heightPx / 2f - (paint.descent() + paint.ascent()) / 2
          canvas.drawText("LOGO", xPos, yPos, paint)
          val escpos = bitmapToEscPos(bmp)
          builder.write(byteArrayOf(0x1B, 0x61, 0x01))
          builder.write(escpos)
          builder.write(byteArrayOf(0x0A, 0x0A))
        }
        val width = 45
        val line = "-".repeat(width) + "\n"
        val receipt = org.json.JSONObject(jsonReceipt)
        val storeName = receipt.optString("storeName", "Tijarah 360")
        val storeAddress = receipt.optString("storeAddress", "Saudi Arabia, Riyadh")
        val receiptNumber = receipt.optString("receiptNumber", "#123456")
        val receiptDate = receipt.optString("receiptDate", "2024-01-01 12:00")
        val items = receipt.optJSONArray("items")
        val total = receipt.optDouble("total", 0.0)
        val receiptId = receipt.optString("receiptId", "RCP123456")
        var totalQty = 0
        var totalTax = 0.0
        var totalPrice = 0.0
        if (items != null) {
          for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            totalQty += item.optInt("qty", 1)
            totalTax += item.optDouble("tax", 0.0)
            totalPrice += item.optDouble("price", 0.0) * item.optInt("qty", 1)
          }
        }
        builder.write(byteArrayOf(0x1B, 0x61, 0x01))
        builder.write(byteArrayOf(0x1B, 0x45, 0x01))
        builder.write(byteArrayOf(0x1D, 0x21, 0x11))
        builder.write((storeName + "\n").toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x1B, 0x45, 0x00))
        builder.write(byteArrayOf(0x1D, 0x21, 0x01))
        builder.write(byteArrayOf(0x1B, 0x61, 0x01))
        builder.write((storeAddress + "\n").toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x1B, 0x61, 0x01))
        builder.write((receiptNumber + "\n").toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x1B, 0x61, 0x01))
        builder.write((receiptDate + "\n").toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x1B, 0x61, 0x00))
        builder.write(line.toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x1D, 0x21, 0x01))
        val header = String.format("%-16s %6s %11s %12s\n", "Item", "Qty", "Price", "Tax")
        builder.write(header.toByteArray(Charsets.UTF_8))
        if (items != null) {
          for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = item.optString("name", "Item").take(16)
            val qty = item.optInt("qty", 1)
            val price = item.optDouble("price", 0.0)
            val tax = item.optDouble("tax", 0.0)
            val lineItem = String.format("%-16s %6d %11.2f %12.2f\n", name, qty, price, tax)
            builder.write(lineItem.toByteArray(Charsets.UTF_8))
          }
        }
        builder.write(line.toByteArray(Charsets.UTF_8))
        val totals = String.format("%-16s %6d %11.2f %12.2f\n", "TOTAL", totalQty, totalPrice, totalTax)
        builder.write(totals.toByteArray(Charsets.UTF_8))
        builder.write(byteArrayOf(0x1B, 0x61, 0x01))
        builder.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08))
        val qrData = receiptId.toByteArray(Charsets.UTF_8)
        builder.write(byteArrayOf(0x1D, 0x28, 0x6B, (qrData.size + 3).toByte(), 0x00, 0x31, 0x50, 0x30))
        builder.write(qrData)
        builder.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        builder.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        builder.write(byteArrayOf(0x1B, 0x64, 0x04))
        builder.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        connection.bulkTransfer(endpoint, builder.toByteArray(), builder.size(), 4000)
        connection.releaseInterface(usbInterface)
        connection.close()
        return@AsyncFunction true
      } catch (e: Exception) {
        connection.close()
        throw e
      }
    }
  }

  private fun bitmapToEscPos(bitmap: android.graphics.Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val bytesPerRow = (width + 7) / 8
    val imageBytes = ByteArray(bytesPerRow * height)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val pixel = bitmap.getPixel(x, y)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        val bit = if (luminance < 128) 1 else 0
        if (bit == 1) {
          imageBytes[y * bytesPerRow + x / 8] = (imageBytes[y * bytesPerRow + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
        }
      }
    }
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
    out.write(byteArrayOf((bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte()))
    out.write(byteArrayOf((height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte()))
    out.write(imageBytes)
    return out.toByteArray()
  }
}
