PKG := dev.pomo
MODULES := app wear

# Until kotlin-devkit is a submodule: make DEVKIT=../kotlin-devkit <target>
DEVKIT ?= kotlin-devkit
include $(DEVKIT)/common.mk

.PHONY: wear wear-install

## wear: Build the Wear OS debug APK
wear:
	$(GRADLE) :wear:assembleDebug

## wear-install: Install it on the watch (WATCH=<adb serial or ip:port>)
wear-install: wear
	@[ -n "$(WATCH)" ] || { echo "Usage: make wear-install WATCH=<adb serial or ip:port>"; exit 1; }
	$(ADB) -s $(WATCH) install -r wear/build/outputs/apk/debug/*.apk
