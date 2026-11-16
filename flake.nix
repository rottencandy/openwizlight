{
  description = "Minimal Android build environment for coding agents";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f system);

      platformVersion = "36";
      buildToolsVersion = "36.0.0";

      mkShell =
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };

          # Only what Gradle + AGP need to compile, dex, package and sign an APK.
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ platformVersion ];
            buildToolsVersions = [ buildToolsVersion ];
            includeEmulator = false;
            includeSystemImages = false;
            includeNDK = false;
            includeCmake = false;
            toolsVersion = null;
            includeSources = false;
            includeExtras = [ ];
            useGoogleAPIs = false;
          };

          androidSdk = androidComposition.androidsdk;
          sdkRoot = "${androidSdk}/libexec/android-sdk";
        in
        pkgs.mkShell {
          packages = [
            androidSdk
            pkgs.jdk17
            pkgs.gradle
          ];

          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = pkgs.jdk17.home;

          # AGP downloads a prebuilt aapt2 from Maven that is dynamically linked
          # against FHS paths and fails on NixOS; use the SDK's patched binary.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
        };
    in
    {
      devShells = forAllSystems (system: {
        default = mkShell system;
      });
    };
}
