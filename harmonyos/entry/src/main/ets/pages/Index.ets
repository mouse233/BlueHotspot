import { ClientStore } from '../store/ClientStore';
import {
  BluetoothState,
  ClientSnapshot,
  ConnectionState,
  DiscoveredDevice,
  connectionStateLabel,
  hotspotStateLabel
} from '../model/ClientState';

const PAGE_BACKGROUND: string = '#F7F8FC';
const CARD_BACKGROUND: string = '#FFFFFF';
const PRIMARY_TEXT: string = '#1F2329';
const SECONDARY_TEXT: string = '#737A87';
const DIVIDER_COLOR: string = '#E9ECF2';
const ACCENT_COLOR: string = '#356AE6';
const ERROR_COLOR: string = '#D14343';

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
    Scroll() {
      Column({ space: 24 }) {
        this.header();
        this.preferenceCard();
        this.devicesSection();
        this.connectionSection();
        this.controlsSection();
        if (this.snapshot.lastError.length > 0) {
          this.errorCard();
        }
        this.footer();
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 32 });
    }
    .scrollable(ScrollDirection.Vertical)
    .width('100%')
    .height('100%')
    .backgroundColor(PAGE_BACKGROUND);
  }

  @Builder
  header(): void {
    Row({ space: 12 }) {
      Column({ space: 4 }) {
        Text('BlueHotspot')
          .fontSize(30)
          .fontWeight(FontWeight.Bold)
          .fontColor(PRIMARY_TEXT)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis });
        Text('Control an Android hotspot over BLE')
          .fontSize(14)
          .fontColor(SECONDARY_TEXT)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis });
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1);

      Button('About')
        .height(40)
        .padding({ left: 16, right: 16 })
        .fontSize(15)
        .fontColor(ACCENT_COLOR)
        .backgroundColor('#E9F0FF')
        .borderRadius(20)
        .onClick((): void => {
          this.showAbout();
        });
    }
    .width('100%')
    .alignItems(VerticalAlign.Center);
  }

  @Builder
  preferenceCard(): void {
    Column() {
      Row() {
        Column({ space: 3 }) {
          Text('Automatic connection')
            .fontSize(16)
            .fontColor(PRIMARY_TEXT);
          Text('Reconnect to the last selected device')
            .fontSize(13)
            .fontColor(SECONDARY_TEXT);
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1);

        Toggle({ type: ToggleType.Switch, isOn: this.snapshot.autoConnectEnabled })
          .selectedColor(ACCENT_COLOR)
          .onChange((isOn: boolean): void => {
            this.store.setAutoConnectEnabled(isOn);
          });
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 14, bottom: 14 });
    }
    .width('100%')
    .backgroundColor(CARD_BACKGROUND)
    .borderRadius(16)
    .clip(true);
  }

  @Builder
  devicesSection(): void {
    Column({ space: 10 }) {
      this.sectionHeader('Android devices', `${this.snapshot.devices.length}`);
      Column() {
        if (this.snapshot.devices.length === 0) {
          this.infoRow('⌕', 'No Android devices found', 'Start a scan to find nearby servers');
        } else {
          ForEach(this.snapshot.devices, (device: DiscoveredDevice, index: number): void => {
            this.deviceRow(device);
            if (index < this.snapshot.devices.length - 1) {
              Divider()
                .color(DIVIDER_COLOR)
                .margin({ left: 52, right: 16 });
            }
          }, (device: DiscoveredDevice): string => device.id);
        }

        Divider()
          .color(DIVIDER_COLOR)
          .margin({ left: 16, right: 16 });
        this.actionRow(
          '↻',
          'Scan again',
          'Search for nearby Android devices',
          this.snapshot.bluetoothState === BluetoothState.Ready,
          (): void => {
            this.store.startScanning();
          }
        );
      }
      .width('100%')
      .backgroundColor(CARD_BACKGROUND)
      .borderRadius(16)
      .clip(true);
    }
    .width('100%');
  }

  @Builder
  deviceRow(device: DiscoveredDevice): void {
    Row({ space: 12 }) {
      Text('◉')
        .fontSize(24)
        .fontColor(ACCENT_COLOR)
        .width(24)
        .textAlign(TextAlign.Center);

      Column({ space: 3 }) {
        Text(device.name)
          .fontSize(16)
          .fontColor(PRIMARY_TEXT)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis });
        Text(device.rssi === 0 ? 'Signal unavailable' : `${device.rssi} dBm`)
          .fontSize(13)
          .fontColor(SECONDARY_TEXT);
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1);

      if (this.snapshot.connectionState === ConnectionState.Connecting &&
        this.snapshot.deviceName === device.name) {
        Text('Connecting')
          .fontSize(14)
          .fontColor(SECONDARY_TEXT);
      } else if (this.snapshot.connectionState === ConnectionState.Connected &&
        this.snapshot.deviceName === device.name) {
        Text('Connected')
          .fontSize(14)
          .fontColor(ACCENT_COLOR);
      } else {
        Text('›')
          .fontSize(28)
          .fontColor(SECONDARY_TEXT);
      }
    }
    .width('100%')
    .padding({ left: 16, right: 12, top: 14, bottom: 14 })
    .enabled(this.snapshot.connectionState === ConnectionState.Scanning ||
      this.snapshot.connectionState === ConnectionState.Disconnected)
    .onClick((): void => {
      this.store.connect(device);
    });
  }

  @Builder
  connectionSection(): void {
    Column({ space: 10 }) {
      this.sectionHeader('Connection', '');
      Column() {
        Row({ space: 12 }) {
          Text(this.snapshot.connectionState === ConnectionState.Connected ? '●' : '○')
            .fontSize(24)
            .fontColor(this.snapshot.connectionState === ConnectionState.Connected ? ACCENT_COLOR : SECONDARY_TEXT)
            .width(24)
            .textAlign(TextAlign.Center);
          Column({ space: 3 }) {
            Text(this.snapshot.deviceName.length > 0 ? this.snapshot.deviceName : 'No Android device')
              .fontSize(16)
              .fontColor(PRIMARY_TEXT)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis });
            Text(connectionStateLabel(this.snapshot.connectionState))
              .fontSize(13)
              .fontColor(SECONDARY_TEXT);
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1);
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 14, bottom: 14 });

        Divider().color(DIVIDER_COLOR).margin({ left: 16, right: 16 });
        this.statusRow('Bluetooth', this.bluetoothLabel());
        Divider().color(DIVIDER_COLOR).margin({ left: 16, right: 16 });
        this.statusRow('Hotspot', hotspotStateLabel(this.snapshot.hotspotState));
      }
      .width('100%')
      .backgroundColor(CARD_BACKGROUND)
      .borderRadius(16)
      .clip(true);
    }
    .width('100%');
  }

  @Builder
  statusRow(title: string, value: string): void {
    Row() {
      Text(title)
        .fontSize(16)
        .fontColor(PRIMARY_TEXT)
        .layoutWeight(1);
      Text(value)
        .fontSize(15)
        .fontColor(SECONDARY_TEXT)
        .textAlign(TextAlign.End)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis });
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 13, bottom: 13 });
  }

  @Builder
  controlsSection(): void {
    Column({ space: 10 }) {
      this.sectionHeader('Controls', '');
      Column() {
        if (this.snapshot.connectionState === ConnectionState.Connected) {
          this.actionRow(
            '▶',
            'Start hotspot',
            'Start the Android device hotspot',
            this.snapshot.hotspotState !== 'STARTING' && this.snapshot.hotspotState !== 'ACTIVE',
            (): void => {
              this.store.startHotspot();
            }
          );
          Divider().color(DIVIDER_COLOR).margin({ left: 52, right: 16 });
          this.actionRow(
            '■',
            'Stop hotspot',
            'Stop the Android device hotspot',
            this.snapshot.hotspotState === 'ACTIVE',
            (): void => {
              this.store.stopHotspot();
            }
          );
          Divider().color(DIVIDER_COLOR).margin({ left: 52, right: 16 });
          this.actionRow(
            '×',
            'Disconnect',
            'Close the current BLE connection',
            true,
            (): void => {
              this.store.disconnect();
            }
          );
        } else {
          this.actionRow(
            '⌕',
            'Scan for Android device',
            'Find a BlueHotspot Android server',
            this.snapshot.bluetoothState === BluetoothState.Ready,
            (): void => {
              this.store.startScanning();
            }
          );
        }
      }
      .width('100%')
      .backgroundColor(CARD_BACKGROUND)
      .borderRadius(16)
      .clip(true);
    }
    .width('100%');
  }

  @Builder
  actionRow(icon: string, title: string, subtitle: string, enabled: boolean, action: () => void): void {
    Row({ space: 12 }) {
      Text(icon)
        .fontSize(23)
        .fontColor(enabled ? ACCENT_COLOR : SECONDARY_TEXT)
        .width(24)
        .textAlign(TextAlign.Center);
      Column({ space: 3 }) {
        Text(title)
          .fontSize(16)
          .fontColor(enabled ? PRIMARY_TEXT : SECONDARY_TEXT);
        Text(subtitle)
          .fontSize(13)
          .fontColor(SECONDARY_TEXT)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis });
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1);
      Text('›')
        .fontSize(28)
        .fontColor(enabled ? SECONDARY_TEXT : DIVIDER_COLOR);
    }
    .width('100%')
    .padding({ left: 16, right: 12, top: 14, bottom: 14 })
    .enabled(enabled)
    .onClick((): void => {
      action();
    });
  }

  @Builder
  infoRow(icon: string, title: string, subtitle: string): void {
    Row({ space: 12 }) {
      Text(icon)
        .fontSize(26)
        .fontColor(SECONDARY_TEXT)
        .width(24)
        .textAlign(TextAlign.Center);
      Column({ space: 3 }) {
        Text(title)
          .fontSize(16)
          .fontColor(PRIMARY_TEXT);
        Text(subtitle)
          .fontSize(13)
          .fontColor(SECONDARY_TEXT);
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1);
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 18, bottom: 18 });
  }

  @Builder
  sectionHeader(title: string, count: string): void {
    Row({ space: 8 }) {
      Text(title)
        .fontSize(21)
        .fontWeight(FontWeight.Medium)
        .fontColor(PRIMARY_TEXT);
      if (count.length > 0) {
        Text(count)
          .fontSize(15)
          .fontColor(SECONDARY_TEXT);
      }
    }
    .width('100%');
  }

  @Builder
  errorCard(): void {
    Row({ space: 12 }) {
      Text('!')
        .fontSize(17)
        .fontWeight(FontWeight.Bold)
        .fontColor(ERROR_COLOR)
        .width(28)
        .height(28)
        .textAlign(TextAlign.Center)
        .border({ width: 2, color: ERROR_COLOR, radius: 14 });
      Text(this.snapshot.lastError)
        .fontSize(14)
        .fontColor(ERROR_COLOR)
        .layoutWeight(1);
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#FFF1F1')
    .borderRadius(16);
  }

  @Builder
  footer(): void {
    Text('BlueHotspot HarmonyOS client')
      .width('100%')
      .fontSize(12)
      .fontColor('#9AA1AD')
      .textAlign(TextAlign.Center)
      .margin({ top: 2 });
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
      title: 'BlueHotspot HarmonyOS client',
      message: 'Controls an existing BlueHotspot Android server over BLE. No hotspot credentials are read, stored, or transferred.',
      primaryButton: {
        value: 'Close',
        action: (): void => {}
      },
      autoCancel: true
    });
  }
}
