import MyPrintModule from './MyPrintModule';

export function hello(): string {
    return MyPrintModule.hello();
  }
export { default } from './MyPrintModule';
export * from  './MyPrint.types';

