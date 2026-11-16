APK := build/outputs/apk/debug/hello-debug.apk
APP_ID := com.example.hello

# Run commands inside the flake's dev shell unless already in one.
ifdef IN_NIX_SHELL
	NIX :=
else
	NIX := nix develop --command
endif

.PHONY: help build install run clean

help: ## Show available targets
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-10s %s\n", $$1, $$2}'

build: ## Build the debug APK
	$(NIX) gradle assembleDebug

install: build ## Build and install the debug APK on a connected device
	$(NIX) adb install -r $(APK)

run: install ## Build, install and launch the app
	$(NIX) adb shell am start -n $(APP_ID)/.MainActivity

clean: ## Remove build outputs
	$(NIX) gradle clean
