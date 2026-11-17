APK := build/outputs/apk/debug/OpenWiZLight-debug.apk
RELEASE_DIR := build/outputs/apk/release
APP_ID := io.github.rottencandy.openwizlight

# Run commands inside the flake's dev shell unless already in one.
ifdef IN_NIX_SHELL
	NIX :=
else
	NIX := nix develop --command
endif

.PHONY: help build test release install run clean

help: ## Show available targets
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-10s %s\n", $$1, $$2}'

build: ## Build the debug APK
	$(NIX) gradle assembleDebug

test: ## Run the JVM unit tests
	$(NIX) gradle testDebugUnitTest

release: ## Build the shrunk release APK, signed if keystore.properties exists
	$(NIX) gradle assembleRelease
	@ls $(RELEASE_DIR)/*.apk

install: build ## Build and install the debug APK on a connected device
	$(NIX) adb install -r $(APK)

run: install ## Build, install and launch the app
	$(NIX) adb shell am start -n $(APP_ID)/.MainActivity

clean: ## Remove build outputs
	$(NIX) gradle clean
