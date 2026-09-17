.DEFAULT_GOAL := help

DEVICE ?=
HEADLESS ?=

.PHONY: help setup build build-stable start run dev test install install-stable launch launch-stable set-home-stable stop doctor

help:
	@printf '%s\n' \
		'Golf Mk5 launcher' \
		'' \
		'  make setup                     Install the local Android toolchain' \
		'  make dev                       Start, build, install, and launch' \
		'  make run                       Build, install, and launch' \
		'  make build                     Compile the dev APK' \
		'  make build-stable              Compile the stable HOME APK' \
		'  make start                     Open the graphical emulator' \
		'  make start HEADLESS=1          Start the emulator without a window' \
		'  make test                      Run UI tests and restore the app' \
		'  make install                   Install the dev APK on the emulator' \
		'  make install DEVICE=<serial>   Install dev on one ADB device' \
		'  make install-stable DEVICE=... Explicitly update/install stable' \
		'  make launch                    Launch dev on the emulator' \
		'  make launch DEVICE=<serial>    Launch dev on one ADB device' \
		'  make launch-stable DEVICE=...  Launch stable on one ADB device' \
		'  make set-home-stable DEVICE=... Select stable as default HOME' \
		'  make stop                      Stop the local emulator' \
		'  make doctor                    Check tools, acceleration, and ADB'

setup:
	./launcher.sh setup

build:
	./launcher.sh build

build-stable:
	./launcher.sh build-stable

start:
	./launcher.sh start $(if $(HEADLESS),--headless,)

run:
	./launcher.sh run

dev:
	./launcher.sh dev

test:
	./launcher.sh test

install:
	./launcher.sh install $(DEVICE)

install-stable:
	./launcher.sh install-stable $(DEVICE)

launch:
	./launcher.sh launch $(DEVICE)

launch-stable:
	./launcher.sh launch-stable $(DEVICE)

set-home-stable:
	./launcher.sh set-home-stable $(DEVICE)

stop:
	./launcher.sh stop

doctor:
	./launcher.sh doctor
