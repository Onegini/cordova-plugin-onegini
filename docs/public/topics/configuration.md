# Configuration

<!-- toc --> 

## Configuring the Onegini SDKs

The following steps describe how to configure our native Android and iOS SDKs for your platform.
When you add a platform to your Cordova project, the Onegini Cordova Plugin will automatically try to invoke the [Onegini SDK Configurator](https://github.com/Onegini/onegini-sdk-configurator).
The SDK Configurator then reads the Token Server Configuration from configuration zip files you can obtain from your Token Server admin panel.
Configuration settings not relevant to your Token Server will be read from your project's `config.xml` file.

### Install the SDK Configurator

Download the latest 3.x release for your platform from the [release page](https://github.com/Onegini/onegini-sdk-configurator/releases) and extract the zip file.
You will then need to point the Onegini Cordova Plugin to your Configurator executable.

The easiest way the Plugin can find your Configurator is by setting the `ONEGINI_SDK_CONFIGURATOR_PATH` environment variable.
```bash
export ONEGINI_SDK_CONFIGURATOR_PATH=/path/to/onegini-sdk-configurator
```

The Plugin will also try to find the configurator in your `$PATH` if `ONEGINI_SDK_CONFIGURATOR_PATH` is not set. If you set the Configurator in your `PATH`, you 
can also [run the configurator from the command line](https://github.com/Onegini/onegini-sdk-configurator#usage).

### Adding the Token Server configuration to your project

Download the configuration zips from your Token Server admin panel under `Configuration > Applications > My App`. Under _Platform versions_ click `Actions > 
Export Platform version config`.

The easiest way the plugin can find your configuration files is to store them as `onegini-config-android.zip` and `onegini-config-ios.zip` in the root of your 
Cordova Project.

You can specify alternative locations for these files by [Customizing the configuration hook](#customizing-the-configuration-hook).


### Setting application preferences

Apart from your Token Server Configuration, the Configurator will read some properties from your `config.xml` file. Changes in these preferences are only 
picked up after re-running the Configurator. The following preferences can be set:

| Preference                   | Default value | Description
|------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------
| OneginiRootDetectionEnabled  | true          | Enable Root detection in your application. This disable your application on rooted Android devices.
| OneginiDebugDetectionEnabled | true          | Enable Debug detection. This will disable attempts to attach a debugger to your application on Android and iOS.
| OneginiWebView               | None          | The webview used to handle registration. See also: [user registration topic guide](./user-registration.md).

Add these to your `config.xml` file as follows:

```xml
<preference name="Preference" value="Value" />
```

For more information on Cordova Preferences refer to the [Cordova Documentation](https://cordova.apache.org/docs/en/latest/config_ref/#preference).


### Customizing the configuration hook

The configuration hook is used to invoke the Configurator. The hook can be customized by setting the following environment variables:

| Name                            | Default value                                | Description
|---------------------------------|----------------------------------------------|---------------------------------------------------------------------------------------------------------
| `ONEGINI_AUTOCONFIGURE`         | true                                         | Enable or disable the configuration hook, you will need to manually configure your platforms if disabled.
| `ONEGINI_SDK_CONFIGURATOR_PATH` | resolved from `$PATH`                        | Location of the Onegini SDK Configurator executable.
| `ONEGINI_CONFIG_ANDROID_PATH`   | `<project_root>/onegini-config-android.zip`  | Location of the Token Server configuration zip file for Android.
| `ONEGINI_CONFIG_IOS_PATH`       | `<project_root>/onegini-config-ios.zip`      | Location of the Token Server configuration zip file for iOS.

For example, to change the location of the Android and iOS configuration zips you can set:

```bash
export ONEGINI_CONFIG_ANDROID_PATH=/path/to/android-config.zip
export ONEGINI_CONFIG_IOS_PATH=/path/to/ios-config.zip
```

### Customizing the resolve dependencies hook

This hook is only triggered for iOS platforms since the Android the SDK is downloaded and managed by Gradle which is automatically invoked when building your Android project.

The resolve dependencies hook can be customized using the following environment variables:

| Name                                   | Default value                                            | Description
|----------------------------------------|----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| `ONEGINI_SDK_DOWNLOAD_PATH`            | `<project_root>/plugins/cordova-plugin-onegini/ios-sdk`  | The iOS SDK is quite big and if you want to prevent downloading it every time you can specify a different location that is not cleaned when you remove or add the plugin to your Cordova project. The hook will check whether the SDK is already downloaded before downloading it again.
| `ONEGINI_STRIP_SIMULATOR_ARCHITECTURE` | false                                                    | When submitting an app to the App Store, Apple does not allow simulator (x86_64 and i386) architectures to be present in the iOS SDK. When this variable is set to true, SDK will be stripped of the simulator architectures which allows the app to be submitted to the App Store.

For example, to change the location of the SDK download path you can set:
```bash
export ONEGINI_SDK_DOWNLOAD_PATH=/opt/onegini/sdk
```

For example, to strip simulator architectures you can set:
```bash
export ONEGINI_STRIP_SIMULATOR_ARCHITECTURE=true
```