export enum BluetoothState {
  Unsupported = 'Unsupported',
  PoweredOff = 'PoweredOff',
  Ready = 'Ready'
}

export enum ConnectionState {
  Disconnected = 'Disconnected',
  Scanning = 'Scanning',
  Connecting = 'Connecting',
  Pairing = 'Pairing',
  DiscoveringServices = 'DiscoveringServices',
  Subscribing = 'Subscribing',
  Connected = 'Connected'
}

export interface DiscoveredDevice {
  id: string;
  name: string;
  rssi: number;
}

export interface ClientSnapshot {
  bluetoothState: BluetoothState;
  connectionState: ConnectionState;
  devices: Array<DiscoveredDevice>;
  hotspotState: string;
  deviceName: string;
  lastError: string;
  autoConnectEnabled: boolean;
}

export function connectionStateLabel(value: ConnectionState): string {
  switch (value) {
    case ConnectionState.Scanning:
      return 'Scanning';
    case ConnectionState.Connecting:
      return 'Connecting';
    case ConnectionState.Pairing:
      return 'Pairing required';
    case ConnectionState.DiscoveringServices:
      return 'Discovering services';
    case ConnectionState.Subscribing:
      return 'Enabling notifications';
    case ConnectionState.Connected:
      return 'Connected';
    default:
      return 'Disconnected';
  }
}

export function hotspotStateLabel(value: string): string {
  return value.length === 0 ? 'Unknown' : value;
}
