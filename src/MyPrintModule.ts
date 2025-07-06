import { NativeModule, requireNativeModule } from 'expo';

import { MyPrintModuleEvents } from './MyPrint.types';

declare class MyPrintModule extends NativeModule<MyPrintModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
  printTestPage(deviceId: number): Promise<boolean>;
  printReceipt(deviceId: number, jsonReceipt: string): Promise<boolean>;
}

export default requireNativeModule<MyPrintModule>('MyPrint');
