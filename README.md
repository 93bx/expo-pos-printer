# expo-pos-printer

A React Native Expo module for printing receipts using ESC/POS commands on Android devices with USB thermal printers.

## Features

- **USB Printer Discovery**: Automatically discover and connect to USB thermal printers
- **ESC/POS Support**: Full ESC/POS command support for thermal receipt printers
- **Receipt Printing**: Print formatted receipts with store information, items, totals, and QR codes
- **Logo Support**: Print custom logos at the top of receipts
- **Test Printing**: Print test pages to verify printer connectivity
- **Cross-Platform**: Built for Expo with React Native support
- **TypeScript**: Full TypeScript support with type definitions

## Prerequisites

- React Native project with Expo SDK 53+
- Android device or emulator (iOS support planned)
- USB thermal printer compatible with ESC/POS commands
- USB OTG cable for Android device connection

## Installation

```bash
npm install expo-pos-printer
```

### Expo Installation

```bash
npx expo install expo-pos-printer
```

## Usage

### Basic Setup

```typescript
import { requireNativeModule } from 'expo-modules-core';

const PosPrinter = requireNativeModule('MyPrint');
```

### Discover USB Printers

```typescript
import { useState, useEffect } from 'react';

function PrinterDiscovery() {
  const [printers, setPrinters] = useState([]);
  const [selectedPrinter, setSelectedPrinter] = useState(null);

  const discoverPrinters = async () => {
    try {
      const devices = await PosPrinter.discoverUsbPrinters();
      setPrinters(devices);
      if (devices.length > 0) {
        setSelectedPrinter(devices[0]);
      }
    } catch (error) {
      console.error('Failed to discover printers:', error);
    }
  };

  return (
    <View>
      <Button title="Discover Printers" onPress={discoverPrinters} />
      {printers.map(printer => (
        <Text key={printer.deviceId}>
          {printer.deviceName} (ID: {printer.deviceId})
        </Text>
      ))}
    </View>
  );
}
```

### Print Test Page

```typescript
const printTestPage = async (deviceId: number) => {
  try {
    const success = await PosPrinter.printTestPage(deviceId);
    if (success) {
      console.log('Test page printed successfully');
    }
  } catch (error) {
    console.error('Failed to print test page:', error);
  }
};
```

### Print Receipt

```typescript
const printReceipt = async (deviceId: number, receiptData: ReceiptData) => {
  try {
    const success = await PosPrinter.printReceipt(deviceId, JSON.stringify(receiptData));
    if (success) {
      console.log('Receipt printed successfully');
    }
  } catch (error) {
    console.error('Failed to print receipt:', error);
  }
};

// Receipt data structure
interface ReceiptData {
  storeName: string;
  storeAddress: string;
  receiptNumber: string;
  receiptDate: string;
  items: Array<{
    name: string;
    qty: number;
    price: number;
    tax: number;
  }>;
  total: number;
  receiptId: string;
}

// Example usage
const receiptData: ReceiptData = {
  storeName: "My Store",
  storeAddress: "123 Main St, City, State",
  receiptNumber: "#12345",
  receiptDate: "2024-01-15 14:30",
  items: [
    { name: "Product 1", qty: 2, price: 10.99, tax: 1.10 },
    { name: "Product 2", qty: 1, price: 5.50, tax: 0.55 }
  ],
  total: 27.48,
  receiptId: "RCP123456"
};
```

## API Reference

### Methods

#### `discoverUsbPrinters(): Promise<PrinterDevice[]>`

Discovers all connected USB printers and returns an array of printer devices.

**Returns:**
```typescript
interface PrinterDevice {
  deviceId: number;
  deviceName: string;
  vendorId: number;
  productId: number;
}
```

#### `printTestPage(deviceId: number): Promise<boolean>`

Prints a test page to verify printer connectivity and basic functionality.

**Parameters:**
- `deviceId`: The ID of the printer device

**Returns:** `Promise<boolean>` - `true` if successful, throws error if failed

#### `printReceipt(deviceId: number, jsonReceipt: string): Promise<boolean>`

Prints a formatted receipt with store information, items, totals, and QR code.

**Parameters:**
- `deviceId`: The ID of the printer device
- `jsonReceipt`: JSON string containing receipt data

**Returns:** `Promise<boolean>` - `true` if successful, throws error if failed

### Events

The module emits events for USB permission changes:

```typescript
interface PrinterEvents {
  onChange: (params: {
    usbPermissionGranted?: boolean;
    deviceId?: number;
    value?: string;
  }) => void;
}
```

## Native Implementation

### Android (Kotlin)

The module uses native Android USB APIs to communicate with thermal printers:

#### Key Components

1. **USB Manager Integration**
   - Uses `UsbManager` to discover and manage USB devices
   - Handles USB permission requests automatically
   - Supports USB OTG connections

2. **ESC/POS Command Implementation**
   - Full ESC/POS command set support
   - Text formatting (bold, double size, alignment)
   - QR code generation and printing
   - Bitmap/logo printing support

3. **Receipt Formatting**
   - 80mm paper width optimization
   - Column alignment for items and totals
   - Automatic line spacing and cutting

#### Native Code Structure

```kotlin
class MyPrintModule : Module() {
  // USB permission handling
  private var usbPermissionReceiver: BroadcastReceiver? = null
  
  // Module definition with async functions
  override fun definition() = ModuleDefinition {
    Name("MyPrint")
    Events("onChange")
    
    AsyncFunction("discoverUsbPrinters") { /* USB discovery logic */ }
    AsyncFunction("printTestPage") { deviceId: Int -> /* Test print logic */ }
    AsyncFunction("printReceipt") { deviceId: Int, jsonReceipt: String -> /* Receipt print logic */ }
  }
}
```

#### ESC/POS Commands Used

- `ESC @` - Initialize printer
- `ESC a n` - Text alignment (0=left, 1=center, 2=right)
- `ESC E n` - Bold text (0=off, 1=on)
- `GS ! n` - Text size selection
- `GS v 0` - Print raster bitmap
- `GS ( k` - QR code commands
- `GS V n` - Cut paper

### Platform Support

- **Android**: ✅ Full support with native USB implementation
- **iOS**: 🚧 Planned (requires Bluetooth printer support)
- **Web**: ❌ Not supported (requires native USB access)

## Configuration

### Android Permissions

The following permissions are automatically added to your Android manifest:

```xml
<uses-permission android:name="android.permission.USB_PERMISSION" />
<uses-feature android:name="android.hardware.usb.host" />
```

### Expo Configuration

Add the module to your `expo-module.config.json`:

```json
{
  "platforms": ["android"],
  "android": {
    "modules": ["expo.modules.myprint.MyPrintModule"]
  }
}
```

## Troubleshooting

### Common Issues

1. **No printers found**
   - Ensure USB OTG cable is properly connected
   - Check if printer is powered on and in USB mode
   - Verify Android device supports USB host mode

2. **Permission denied**
   - The module automatically requests USB permissions
   - Ensure you grant permission when prompted
   - Some devices may require manual permission in settings

3. **Print quality issues**
   - Check printer paper and ribbon
   - Verify printer supports ESC/POS commands
   - Test with different text sizes and alignments

4. **Connection lost**
   - Reconnect USB cable
   - Restart the discovery process
   - Check for Android USB debugging conflicts

### Debug Logging

Enable debug logging to troubleshoot issues:

```typescript
// The module automatically logs USB events and print operations
// Check your device logs for "MyPrintModule" tags
```

## Example Project

See the `my-example` directory for a complete working example with:
- Printer discovery and selection
- Receipt creation interface
- Settings management
- Log viewing

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

MIT License - see LICENSE file for details


## Changelog

### v0.1.0
- Initial release
- USB printer discovery
- ESC/POS receipt printing
- Test page printing
- Android support
