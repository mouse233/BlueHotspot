import { access, ble } from '@kit.ConnectivityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { BleFrame, BleFrameCodec, BleFrameDecoder, BleMessage, BleWriteQueue, newRequestId } from './BleProtocol';
import { BleUuids } from './BleUuids';
import { BluetoothState, ClientSnapshot, ConnectionState, DiscoveredDevice } from '../model/ClientState';

export type ClientStateListener = (snapshot: ClientSnapshot) => void;

const REQUESTED_MTU: number = 247;
const DEFAULT_MTU: number = 23;

export class HarmonyBleCentral {
  private readonly listeners: Array<ClientStateListener> = [];
  private readonly discovered: Map<string, DiscoveredDevice> = new Map<string, DiscoveredDevice>();
  private readonly decoder: BleFrameDecoder = new BleFrameDecoder();
  private readonly writeQueue: BleWriteQueue = new BleWriteQueue();
  private currentClient: ble.GattClientDevice | undefined;
  private currentDeviceId: string = '';
  private commandCharacteristic: ble.BLECharacteristic | undefined;
  private eventCharacteristic: ble.BLECharacteristic | undefined;
  private writeInProgress: boolean = false;
  private ready: boolean = false;
  private mtu: number = DEFAULT_MTU;
  private rssiTimer: number | undefined;
  private scanActive: boolean = false;
  private snapshot: ClientSnapshot = {
    bluetoothState: BluetoothState.Ready,
    connectionState: ConnectionState.Disconnected,
    devices: [],
    hotspotState: 'Unknown',
    deviceName: '',
    lastError: '',
    autoConnectEnabled: true
  };

  private readonly deviceFindListener = (results: Array<ble.ScanResult>): void => {
    results.forEach((result: ble.ScanResult): void => {
      const id: string = result.deviceId;
      const previous: DiscoveredDevice | undefined = this.discovered.get(id);
      const name: string = result.deviceName.length > 0 ? result.deviceName : previous?.name ?? 'Android device';
      this.discovered.set(id, { id: id, name: name, rssi: result.rssi });
      this.snapshot.devices = Array.from(this.discovered.values())
        .sort((left: DiscoveredDevice, right: DiscoveredDevice): number => left.name.localeCompare(right.name));
      if (this.snapshot.autoConnectEnabled && this.snapshot.connectionState === ConnectionState.Scanning &&
        id === this.preferredDeviceId) {
        this.connect(id);
      }
    });
    this.notify();
  };

  private readonly connectionListener = (stateInfo: ble.BLEConnectionChangeState): void => {
    if (stateInfo.deviceId !== this.currentDeviceId) {
      return;
    }
    if (stateInfo.state === 2) {
      this.snapshot.connectionState = ConnectionState.DiscoveringServices;
      this.notify();
      this.discoverServices();
    } else if (stateInfo.state === 0) {
      this.handleDisconnected('BLE connection closed');
    }
  };

  private readonly characteristicListener = (characteristic: ble.BLECharacteristic): void => {
    if (characteristic.characteristicUuid.toLowerCase() !== BleUuids.EVENT) {
      return;
    }
    try {
      this.decoder.append(characteristic.characteristicValue).forEach((frame: BleFrame): void => this.apply(frame));
    } catch (_) {
      this.fail('Invalid message received from Android');
    }
  };

  private readonly mtuListener = (value: number): void => {
    if (value > 0) {
      this.mtu = value;
    }
  };

  private preferredDeviceId: string = '';

  get state(): ClientSnapshot {
    return this.snapshot;
  }

  subscribe(listener: ClientStateListener): void {
    this.listeners.push(listener);
    listener(this.snapshot);
  }

  setPreferredDeviceId(value: string): void {
    this.preferredDeviceId = value;
    if (this.snapshot.autoConnectEnabled && this.snapshot.connectionState === ConnectionState.Scanning) {
      this.tryAutomaticConnection();
    }
  }

  setAutoConnectEnabled(value: boolean): void {
    this.snapshot.autoConnectEnabled = value;
    this.notify();
    if (value && this.snapshot.connectionState === ConnectionState.Scanning) {
      this.tryAutomaticConnection();
    }
  }

  startScanning(): void {
    this.stopScanning();
    this.refreshBluetoothState();
    this.discovered.clear();
    this.snapshot.devices = [];
    this.snapshot.lastError = '';
    this.snapshot.connectionState = ConnectionState.Scanning;
    this.scanActive = true;
    this.notify();
    try {
      const filter: ble.ScanFilter = { serviceUuid: BleUuids.SERVICE };
      const options: ble.ScanOptions = {
        interval: 0,
        dutyMode: ble.ScanDuty.SCAN_MODE_LOW_POWER,
        matchMode: ble.MatchMode.MATCH_MODE_AGGRESSIVE
      };
      ble.on('BLEDeviceFind', this.deviceFindListener);
      ble.startBLEScan([filter], options);
    } catch (error) {
      this.scanActive = false;
      this.snapshot.connectionState = ConnectionState.Disconnected;
      this.fail(this.describeError(error, 'Unable to start BLE scan'));
    }
  }

  stopScanning(): void {
    if (!this.scanActive) {
      return;
    }
    try {
      ble.off('BLEDeviceFind', this.deviceFindListener);
      ble.stopBLEScan();
    } catch (error) {
      this.fail(this.describeError(error, 'Unable to stop BLE scan'));
    } finally {
      this.scanActive = false;
    }
  }

  connect(deviceId: string): void {
    const target: DiscoveredDevice | undefined = this.discovered.get(deviceId);
    if (target === undefined) {
      this.fail('Device is no longer available');
      return;
    }
    this.stopScanning();
    this.closeClient();
    this.currentDeviceId = deviceId;
    this.preferredDeviceId = deviceId;
    this.snapshot.deviceName = target.name;
    this.snapshot.connectionState = ConnectionState.Connecting;
    this.snapshot.hotspotState = 'Unknown';
    this.snapshot.lastError = '';
    this.notify();
    try {
      const client: ble.GattClientDevice = ble.createGattClientDevice(deviceId);
      this.currentClient = client;
      client.on('BLEConnectionStateChange', this.connectionListener);
      client.on('BLECharacteristicChange', this.characteristicListener);
      client.on('BLEMtuChange', this.mtuListener);
      client.connect();
      client.getDeviceName().then((name: string): void => {
        if (name.length > 0 && this.currentDeviceId === deviceId) {
          this.snapshot.deviceName = name;
          this.discovered.set(deviceId, { id: deviceId, name: name, rssi: target.rssi });
          this.snapshot.devices = Array.from(this.discovered.values())
            .sort((left: DiscoveredDevice, right: DiscoveredDevice): number => left.name.localeCompare(right.name));
          this.notify();
        }
      }).catch((): void => undefined);
    } catch (error) {
      this.fail(this.describeError(error, 'Unable to connect to Android device'));
      this.handleDisconnected('Connection failed');
    }
  }

  disconnect(): void {
    this.closeClient();
    this.currentDeviceId = '';
    this.snapshot.connectionState = ConnectionState.Disconnected;
    this.snapshot.deviceName = '';
    this.snapshot.hotspotState = 'Unknown';
    this.notify();
  }

  startHotspot(): void {
    this.send(new BleFrame(BleMessage.START_HOTSPOT, newRequestId()));
  }

  stopHotspot(): void {
    this.send(new BleFrame(BleMessage.STOP_HOTSPOT, newRequestId()));
  }

  ping(): void {
    this.send(new BleFrame(BleMessage.PING, newRequestId()));
  }

  close(): void {
    this.stopScanning();
    this.closeClient();
    this.listeners.length = 0;
  }

  private discoverServices(): void {
    const client: ble.GattClientDevice | undefined = this.currentClient;
    if (client === undefined) {
      return;
    }
    try {
      client.getServices().then((services: Array<ble.GattService>): void => {
        const service: ble.GattService | undefined = services.find((item: ble.GattService): boolean =>
          item.serviceUuid.toLowerCase() === BleUuids.SERVICE);
        const command: ble.BLECharacteristic | undefined = service?.characteristics.find(
          (item: ble.BLECharacteristic): boolean => item.characteristicUuid.toLowerCase() === BleUuids.COMMAND);
        const event: ble.BLECharacteristic | undefined = service?.characteristics.find(
          (item: ble.BLECharacteristic): boolean => item.characteristicUuid.toLowerCase() === BleUuids.EVENT);
        if (service === undefined || command === undefined || event === undefined) {
          this.fail('BlueHotspot service is incomplete');
          return;
        }
        this.commandCharacteristic = command;
        this.eventCharacteristic = event;
        this.snapshot.connectionState = ConnectionState.Subscribing;
        this.notify();
        try {
          client.setBLEMtuSize(REQUESTED_MTU);
          this.mtu = REQUESTED_MTU;
        } catch {
          this.mtu = DEFAULT_MTU;
        }
        try {
          Promise.resolve(client.setCharacteristicChangeNotification(event, true))
            .then((): void => this.finishReady())
            .catch((error: BusinessError): void => {
              this.fail(this.describeError(error, 'Unable to enable hotspot notifications'));
            });
        } catch (error) {
          this.fail(this.describeError(error, 'Unable to enable hotspot notifications'));
        }
      }).catch((error: BusinessError): void => {
        this.fail(this.describeError(error, 'Unable to discover BlueHotspot service'));
      });
    } catch (error) {
      this.fail(this.describeError(error, 'Unable to discover BlueHotspot service'));
    }
  }

  private finishReady(): void {
    if (this.currentClient === undefined || this.eventCharacteristic === undefined) {
      return;
    }
    this.ready = true;
    this.snapshot.connectionState = ConnectionState.Connected;
    this.startRssiUpdates();
    this.notify();
    this.send(new BleFrame(BleMessage.HELLO, newRequestId()));
    this.send(new BleFrame(BleMessage.GET_STATUS, newRequestId()));
  }

  private send(frame: BleFrame): void {
    if (!this.ready || this.commandCharacteristic === undefined) {
      this.fail('Not connected to an Android device');
      return;
    }
    try {
      this.writeQueue.enqueue(BleFrameCodec.encode(frame), this.mtu);
      this.pumpWrites();
    } catch (error) {
      this.fail(this.describeError(error, 'Unable to encode BLE message'));
    }
  }

  private pumpWrites(): void {
    const client: ble.GattClientDevice | undefined = this.currentClient;
    const characteristic: ble.BLECharacteristic | undefined = this.commandCharacteristic;
    if (client === undefined || characteristic === undefined || this.writeInProgress || this.writeQueue.length === 0) {
      return;
    }
    const chunk: ArrayBuffer | undefined = this.writeQueue.take();
    if (chunk === undefined) {
      return;
    }
    characteristic.characteristicValue = chunk;
    this.writeInProgress = true;
    try {
      client.writeCharacteristicValue(characteristic, ble.GattWriteType.WRITE, (error?: BusinessError): void => {
        this.writeInProgress = false;
        if (error !== undefined) {
          this.writeQueue.clear();
          this.fail(this.describeError(error, 'BLE command write failed'));
          return;
        }
        this.pumpWrites();
      });
    } catch (error) {
      this.writeInProgress = false;
      this.writeQueue.clear();
      this.fail(this.describeError(error, 'BLE command write failed'));
    }
  }

  private apply(frame: BleFrame): void {
    switch (frame.type) {
      case BleMessage.STATUS:
        this.snapshot.hotspotState = frame.payload;
        break;
      case BleMessage.HOTSPOT_STARTING:
        this.snapshot.hotspotState = 'STARTING';
        break;
      case BleMessage.HOTSPOT_READY:
        this.snapshot.hotspotState = 'ACTIVE';
        break;
      case BleMessage.HOTSPOT_STOPPED:
        this.snapshot.hotspotState = 'IDLE';
        break;
      case BleMessage.HOTSPOT_FAILED:
        this.snapshot.hotspotState = 'FAILED';
        this.snapshot.lastError = frame.payload.length === 0 ? 'Hotspot operation failed' : frame.payload;
        break;
      case BleMessage.ERROR:
        this.snapshot.lastError = frame.payload.length === 0 ? 'Android rejected the request' : frame.payload;
        break;
      default:
        break;
    }
    this.notify();
  }

  private startRssiUpdates(): void {
    this.stopRssiUpdates();
    this.readRssi();
    this.rssiTimer = setInterval((): void => this.readRssi(), 2000);
  }

  private readRssi(): void {
    const client: ble.GattClientDevice | undefined = this.currentClient;
    if (client === undefined || !this.ready) {
      return;
    }
    try {
      client.getRssiValue().then((rssi: number): void => {
        const current: DiscoveredDevice | undefined = this.discovered.get(this.currentDeviceId);
        if (current !== undefined) {
          this.discovered.set(this.currentDeviceId, { id: current.id, name: current.name, rssi: rssi });
          this.snapshot.devices = Array.from(this.discovered.values())
            .sort((left: DiscoveredDevice, right: DiscoveredDevice): number => left.name.localeCompare(right.name));
          this.notify();
        }
      }).catch((): void => undefined);
    } catch (_) {
      // RSSI is best-effort and must not tear down an otherwise healthy session.
    }
  }

  private stopRssiUpdates(): void {
    if (this.rssiTimer !== undefined) {
      clearInterval(this.rssiTimer);
      this.rssiTimer = undefined;
    }
  }

  private tryAutomaticConnection(): void {
    if (this.preferredDeviceId.length > 0 && this.discovered.has(this.preferredDeviceId)) {
      this.connect(this.preferredDeviceId);
    }
  }

  private handleDisconnected(message: string): void {
    const pairingRequired: boolean = this.snapshot.connectionState === ConnectionState.Pairing;
    this.stopRssiUpdates();
    this.ready = false;
    this.commandCharacteristic = undefined;
    this.eventCharacteristic = undefined;
    this.writeQueue.clear();
    this.writeInProgress = false;
    this.decoder.reset();
    this.snapshot.connectionState = pairingRequired ? ConnectionState.Pairing : ConnectionState.Disconnected;
    if (message.length > 0) {
      this.snapshot.lastError = message;
    }
    this.notify();
  }

  private closeClient(): void {
    this.stopRssiUpdates();
    const client: ble.GattClientDevice | undefined = this.currentClient;
    if (client !== undefined) {
      try {
        client.disconnect();
      } catch (_) {
        // The client may already be disconnected.
      }
      try {
        client.close();
      } catch (_) {
        // close() is intentionally idempotent for teardown purposes.
      }
    }
    this.currentClient = undefined;
    this.ready = false;
    this.commandCharacteristic = undefined;
    this.eventCharacteristic = undefined;
    this.writeQueue.clear();
    this.writeInProgress = false;
    this.decoder.reset();
  }

  private fail(message: string): void {
    this.snapshot.lastError = message;
    if (message.toLowerCase().includes('pair') || message.toLowerCase().includes('encrypt') ||
      message.toLowerCase().includes('bond') || message.toLowerCase().includes('permission')) {
      this.snapshot.connectionState = ConnectionState.Pairing;
    }
    this.notify();
  }

  private refreshBluetoothState(): void {
    try {
      const state: access.BluetoothState = access.getState();
      this.snapshot.bluetoothState = state === access.BluetoothState.STATE_ON ||
        state === access.BluetoothState.STATE_BLE_ON ? BluetoothState.Ready : BluetoothState.PoweredOff;
    } catch (error) {
      this.snapshot.bluetoothState = BluetoothState.Unsupported;
      this.snapshot.lastError = this.describeError(error, 'Unable to read Bluetooth state');
    }
    this.notify();
  }

  private describeError(error: Object, fallback: string): string {
    const businessError: BusinessError = error as BusinessError;
    if (businessError.code === 2901005 || businessError.code === 2901006 || businessError.code === 2901007) {
      return `${fallback}: pairing or link encryption is required`;
    }
    if (businessError.message !== undefined && businessError.message.length > 0) {
      return `${fallback}: ${businessError.message}`;
    }
    return fallback;
  }

  private notify(): void {
    this.listeners.forEach((listener: ClientStateListener): void => listener(this.snapshot));
  }
}
