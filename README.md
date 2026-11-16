# OpenWiZLight

An open source Android app for controlling [WiZ](https://www.wizconnected.com/) smart lights over the local network. It talks to the devices directly, so it needs no cloud account.

Current features:
- Discover WiZ devices on the local network and show their model, IP, MAC and firmware version.

## Building

You need [Nix](https://nixos.org/) with flakes enabled. The flake provides the JDK, Gradle and Android SDK.

```sh
make build    # build the debug APK
make run      # build, install and launch on a phone connected over adb
```

The devicd must be on the same Wi-Fi network as the lights.

## Credits

The protocol implementation is based on [pywizlight](https://github.com/sbidy/pywizlight).
