import { UIAbility } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';

export default class EntryAbility extends UIAbility {
  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index', (error: BusinessError): void => {
      if (error !== undefined && error !== null) {
        console.error(`BlueHotspot loadContent failed: ${error.message}`);
        return;
      }

      windowStage.getMainWindow().then((mainWindow: window.Window): Promise<void> => {
        return mainWindow.setWindowLayoutFullScreen(true).then((): Promise<void> => {
          return mainWindow.setWindowSystemBarProperties({
            statusBarColor: '#00000000',
            navigationBarColor: '#00000000',
            isStatusBarLightIcon: true,
            isNavigationBarLightIcon: true
          });
        });
      }).catch((windowError: BusinessError): void => {
        console.error(`BlueHotspot window appearance failed: ${windowError.message}`);
      });
    });
  }

  onWindowStageDestroy(): void {
  }

  onForeground(): void {
  }

  onBackground(): void {
  }
}
