# OpenWiZLight

An Android app for controlling [WiZ](https://www.wizconnected.com/) smart lights over the local network. Talks to the devices directly, no cloud account needed.

## Building

You need [Nix](https://nixos.org/) with flakes enabled. The flake provides the JDK, Gradle and Android SDK.

```sh
make build    # build the debug APK
make run      # build, install and launch on a phone connected over adb
make release  # build the shrunk release APK
```

`make release` signs the APK when a `keystore.properties` file (gitignored) exists in the project root, and otherwise produces an unsigned APK:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

The devicd must be on the same Wi-Fi network as the lights.

## Credits

The protocol implementation is based on [pywizlight](https://github.com/sbidy/pywizlight).
