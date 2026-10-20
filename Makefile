PKG := dev.pomo

# Until kotlin-devkit is a submodule: make DEVKIT=../kotlin-devkit <target>
DEVKIT ?= kotlin-devkit
include $(DEVKIT)/common.mk
