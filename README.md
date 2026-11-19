# OpenWiZLight

An Android app for controlling [WiZ](https://www.wizconnected.com/) smart lights over the local network. Talks to the devices directly, no cloud account needed.

The device must be on the same Wi-Fi network as the lights.

## Building

You need [Nix](https://nixos.org/) with flakes enabled. The flake provides the JDK, Gradle and Android SDK.

## Credits

The protocol implementation is based on [pywizlight](https://github.com/sbidy/pywizlight).
