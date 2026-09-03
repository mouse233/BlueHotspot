import { ClientStore } from '../store/ClientStore';
import {
  BluetoothState,
  ClientSnapshot,
  ConnectionState,
  DiscoveredDevice,
  connectionStateLabel,
  hotspotStateLabel
} from '../model/ClientState';

@Entry
@Component
struct Index {
  private readonly store: ClientStore = new ClientStore();
  @State private snapshot: ClientSnapshot = this.store.state;

  aboutToAppear(): void {
    this.store.subscribe((next: ClientSnapshot): void => {
      this.snapshot = next;
    });
    const context = this.getUIContext().getHostContext();
    if (context !== undefined) {
      this.store.initialize(context).then((): void => undefined);
    }
    this.store.startScanning();
  }

  aboutToDisappear(): void {
    this.store.close();
  }

  build(): void {
    List({ space: 12 }) {
      ListItem() {
        this.titleBar();
      }
      .padding({ top: 12, bottom: 4 });

      ListItem() {
        this.preferenceRow();
      }

      ListItem() {
        this.sectionHeader('Android devices', `${this.snapshot.devices.length}`);
      }
      .padding({ top: 8, bottom: 2 });

      ListItemGroup({ style: ListItemGroupStyle.CARD }) {
        if (this.snapshot.devices.length === 0) {
          ListItem() {
            this.emptyDeviceRow();
          }
        } else {
          ForEach(this.snapshot.devices, (device: DiscoveredDevice): void => {
            ListItem() {
              this.deviceRow(device);
            }
            .enabled(this.snapshot.connectionState === ConnectionState.Scanning ||
              this.snapshot.connectionState === ConnectionState.Disconnected)
            .onClick((): void => {
              this.store.connect(device);
            });
          }, (device: DiscoveredDevice): string => device.id);
        }

        ListItem() {
          this.actionRow('Scan again', this.snapshot.bluetoothState === BluetoothState.Ready);
        }
        .enabled(this.snapshot.bluetoothState === BluetoothState.Ready)
        .onClick((): void => {
          this.store.startScanning();
        });
      }
      .divider({ strokeWidth: 1, startMargin: 16, endMargin: 16 });

      ListItem() {
        this.sectionHeader('Connection', '');
      }
      .padding({ top: 8, bottom: 2 });

      ListItemGroup({ style: ListItemGroupStyle.CARD }) {
        ListItem() {
          this.connectionRow();
        }
        ListItem() {
          this.statusRow('Bluetooth', this.bluetoothLabel());
        }
        ListItem() {
          this.statusRow('Hotspot', hotspotStateLabel(this.snapshot.hotspotState));
        }
      }
      .divider({ strokeWidth: 1, startMargin: 16, endMargin: 16 });

      ListItem() {
        this.sectionHeader('Controls', '');
      }
      .padding({ top: 8, bottom: 2 });

      ListItemGroup({ style: ListItemGroupStyle.CARD }) {
        if (this.snapshot.connectionState === ConnectionState.Connected) {
          ListItem() {
            this.actionRow('Start hotspot', this.snapshot.hotspotState !== 'STARTING' &&
              this.snapshot.hotspotState !== 'ACTIVE');
          }
          .enabled(this.snapshot.hotspotState !== 'STARTING' && this.snapshot.hotspotState !== 'ACTIVE')
          .onClick((): void => {
            this.store.startHotspot();
          });

          ListItem() {
            this.actionRow('Stop hotspot', this.snapshot.hotspotState === 'ACTIVE');
          }
          .enabled(this.snapshot.hotspotState === 'ACTIVE')
          .onClick((): void => {
            this.store.stopHotspot();
          });

          ListItem() {
            this.actionRow('Disconnect', true);
          }
          .onClick((): void => {
            this.store.disconnect();
          });
        } else {
          ListItem() {
            this.actionRow('Scan for Android device', this.snapshot.bluetoothState === BluetoothState.Ready);
          }
          .enabled(this.snapshot.bluetoothState === BluetoothState.Ready)
          .onClick((): void => {
            this.store.startScanning();
          });
        }
      }
      .divider({ strokeWidth: 1, startMargin: 16, endMargin: 16 });

      if (this.snapshot.lastError.length > 0) {
        ListItem() {
          Text(this.snapshot.lastError)
            .fontSize(14)
            .fontColor(Color.Red)
            .width('100%');
        }
        .padding({ top: 8, bottom: 16 });
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16 });
  }

  @Builder
  titleBar(): void {
    Row() {
      Text('BlueHotspot')
        .fontSize(32)
        .fontWeight(FontWeight.Bold)
        .layoutWeight(1);
      Button('About')
        .onClick((): void => {
          this.showAbout();
        });
    }
    .width('100%');
  }

  @Builder
  preferenceRow(): void {
    Row() {
      Text('Automatic connection')
        .fontSize(16)
        .layoutWeight(1);
      Toggle({ type: ToggleType.Switch, isOn: this.snapshot.autoConnectEnabled })
        .onChange((isOn: boolean): void => {
          this.store.setAutoConnectEnabled(isOn);
        });
    }
    .width('100%');
  }

  @Builder
  sectionHeader(title: string, count: string): void {
    Row() {
      Text(title)
        .fontSize(21)
        .fontWeight(FontWeight.Medium);
      if (count.length > 0) {
        Text(count)
          .fontSize(15)
          .fontColor(Color.Gray)
          .margin({ left: 8 });
      }
    }
    .width('100%');
  }

  @Builder
  emptyDeviceRow(): void {
    Text('No Android devices found')
      .fontSize(16)
      .fontColor(Color.Gray)
      .width('100%');
  }

  @Builder
  deviceRow(device: DiscoveredDevice): void {
    Row() {
      Column({ space: 3 }) {
        Text(device.name)
          .fontSize(16)
          .fontWeight(FontWeight.Medium);
        Text(device.rssi === 0 ? 'Signal unavailable' : `${device.rssi} dBm`)
          .fontSize(14)
          .fontColor(Color.Gray);
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1);

      if (this.snapshot.connectionState === ConnectionState.Connecting &&
        this.snapshot.deviceName === device.name) {
        LoadingProgress()
          .width(24)
          .height(24);
      } else if (this.snapshot.connectionState === ConnectionState.Connected &&
        this.snapshot.deviceName === device.name) {
        Text('Connected')
          .fontSize(14)
          .fontColor(Color.Green);
      } else {
        Text('›')
          .fontSize(24)
          .fontColor(Color.Gray);
      }
    }
    .width('100%')
    .padding(16);
  }

  @Builder
  connectionRow(): void {
    Row() {
      Text(this.snapshot.connectionState === ConnectionState.Connected ? 'Connected' : 'Not connected')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .layoutWeight(1);
      Text(connectionStateLabel(this.snapshot.connectionState))
        .fontSize(14)
        .fontColor(Color.Gray);
    }
    .width('100%')
    .padding(16);
  }

  @Builder
  statusRow(title: string, value: string): void {
    Row() {
      Text(title)
        .fontSize(16)
        .layoutWeight(1);
      Text(value)
        .fontSize(16)
        .fontColor(Color.Gray);
    }
    .width('100%')
    .padding(16);
  }

  @Builder
  actionRow(title: string, enabled: boolean): void {
    Row() {
      Text(title)
        .fontSize(16)
        .fontColor(enabled ? Color.Blue : Color.Gray)
        .layoutWeight(1);
      Text('›')
        .fontSize(24)
        .fontColor(Color.Gray);
    }
    .width('100%')
    .padding(16);
  }

  private bluetoothLabel(): string {
    switch (this.snapshot.bluetoothState) {
      case BluetoothState.Ready:
        return 'Ready';
      case BluetoothState.PoweredOff:
        return 'Powered off';
      default:
        return 'Unavailable';
    }
  }

  private showAbout(): void {
    this.getUIContext().showAlertDialog({
      title: 'About BlueHotspot',
      message: 'BlueHotspot lets this HarmonyOS device control an Android device\'s already-configured Wi-Fi hotspot over encrypted Bluetooth Low Energy (BLE).',
      primaryButton: {
        value: 'Close',
        action: (): void => {}
      },
      autoCancel: true
    });
  }
}
