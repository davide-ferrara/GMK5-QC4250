.DEFAULT_GOAL := help

DEVICE ?=
HEADLESS ?=

.PHONY: help setup build build-stable start run run-stable test install install-stable launch launch-stable set-home-stable stop doctor

help:
	@printf '%s\n' \
		'Golf Mk5 launcher' \
		'' \
		'  make setup                     Install the local Android toolchain' \
		'  make run                       Build, install, and launch debug' \
		'  make run-stable                Build, install, and launch stable (R8)' \
		'  make build                     Compile debug' \
		'  make build-stable              Compile stable (R8)' \
		'  make start [HEADLESS=1]        Start the Android emulator' \
		'  make test                      Run UI tests and restore the debug app' \
		'  make install DEVICE=<serial>   Install debug on an ADB device' \
		'  make install-stable DEVICE=... Build and install stable (R8)' \
		'  make launch DEVICE=<serial>    Launch debug on one ADB device' \
		'  make launch-stable DEVICE=...  Launch stable on one ADB device' \
		'  make set-home-stable DEVICE=... Select stable as default HOME' \
		'  make stop                      Stop the local emulator' \
		'  make doctor                    Check tools and connected ADB devices'

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

run-stable:
	./launcher.sh run-stable

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
