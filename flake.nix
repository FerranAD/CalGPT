{
  description = "Android development environment with Android Studio";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        androidSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" "34" "33" ];
          buildToolsVersions = [ "35.0.0" "34.0.0" "33.0.2" ];
          abiVersions = [ "x86_64" "arm64-v8a" ];
          includeEmulator = true;
          includeSystemImages = true;
          includeNDK = false;
        };

      in {
        devShells.default = pkgs.mkShell {
          ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";
          ANDROID_HOME     = "${androidSdk.androidsdk}/libexec/android-sdk";
          JAVA_HOME        = pkgs.jdk17.home;

          packages = with pkgs; [
            # Core
            jdk17
            gradle
            git
            unzip
            which

            # Android
            androidSdk.androidsdk
            android-tools        # adb, fastboot
            android-studio
	    windsurf
          ];

          shellHook = ''
            export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
            echo "Android Studio + SDK ready"
            echo "SDK: $ANDROID_HOME"
          '';
        };
      }
    );
}

