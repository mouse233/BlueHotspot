import { Context } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { HarmonyBleCentral } from '../ble/HarmonyBleCentral';
import { ClientSnapshot, DiscoveredDevice } from '../model/ClientState';

export type StoreListener = (snapshot: ClientSnapshot) => void;

const PREFERENCES_NAME: string = 'bluehotspot_client';
const AUTO_CONNECT_KEY: string = 'auto_connect_enabled';
const PREFERRED_DEVICE_ID_KEY: string = 'preferred_device_id';

export class ClientStore {
  private readonly central: HarmonyBleCentral = new HarmonyBleCentral();
  private readonly listeners: Array<StoreListener> = [];
  private snapshot: ClientSnapshot = this.copySnapshot(this.central.state);
  private prefStore: preferences.Preferences | undefined;

  constructor() {
    this.central.subscribe((next: ClientSnapshot): void => {
      this.snapshot = this.copySnapshot(next);
      this.listeners.forEach((listener: StoreListener): void => listener(this.snapshot));
    });
  }

  get state(): ClientSnapshot {
    return this.snapshot;
  }

  subscribe(listener: StoreListener): void {
    this.listeners.push(listener);
    listener(this.snapshot);
  }

  async initialize(context: Context): Promise<void> {
    try {
      this.prefStore = await preferences.getPreferences(context, PREFERENCES_NAME);
      const autoConnect: Object = await this.prefStore.get(AUTO_CONNECT_KEY, true);
      const preferredId: Object = await this.prefStore.get(PREFERRED_DEVICE_ID_KEY, '');
      this.central.setAutoConnectEnabled(autoConnect as boolean);
      if (typeof preferredId === 'string') {
        this.central.setPreferredDeviceId(preferredId);
      }
    } catch (_) {
      // The BLE client remains usable when preferences cannot be opened.
    }
  }

  startScanning(): void {
    this.central.startScanning();
  }

  connect(device: DiscoveredDevice): void {
    this.central.connect(device.id);
    this.savePreferredDevice(device.id);
  }

  disconnect(): void {
    this.central.disconnect();
  }

  startHotspot(): void {
    this.central.startHotspot();
  }

  stopHotspot(): void {
    this.central.stopHotspot();
  }

  setAutoConnectEnabled(value: boolean): void {
    this.central.setAutoConnectEnabled(value);
    this.writePreference(AUTO_CONNECT_KEY, value);
  }

  close(): void {
    this.central.close();
    this.listeners.length = 0;
  }

  private savePreferredDevice(deviceId: string): void {
    this.central.setPreferredDeviceId(deviceId);
    this.writePreference(PREFERRED_DEVICE_ID_KEY, deviceId);
  }

  private writePreference(key: string, value: boolean | string): void {
    const store: preferences.Preferences | undefined = this.prefStore;
    if (store === undefined) {
      return;
    }
    store.put(key, value).then((): Promise<void> => store.flush()).catch((): void => undefined);
  }

  private copySnapshot(value: ClientSnapshot): ClientSnapshot {
    return {
      bluetoothState: value.bluetoothState,
      connectionState: value.connectionState,
      devices: value.devices.map((device: DiscoveredDevice): DiscoveredDevice => ({
        id: device.id,
        name: device.name,
        rssi: device.rssi
      })),
      hotspotState: value.hotspotState,
      deviceName: value.deviceName,
      lastError: value.lastError,
      autoConnectEnabled: value.autoConnectEnabled
    };
  }
}
