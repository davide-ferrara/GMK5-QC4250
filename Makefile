.DEFAULT_GOAL := help

DEVICE ?=
HEADLESS ?=

.PHONY: help setup build start run dev test install launch stop doctor

help:
	@printf '%s\n' \
		'Golf Mk5 launcher' \
		'' \
		'  make setup                     Install the local Android toolchain' \
		'  make dev                       Start, build, install, and launch' \
		'  make run                       Build, install, and launch' \
		'  make build                     Compile the debug APK' \
		'  make start                     Open the graphical emulator' \
		'  make start HEADLESS=1          Start the emulator without a window' \
		'  make test                      Run UI tests and restore the app' \
		'  make install                   Install on the local emulator' \
		'  make install DEVICE=<serial>   Install on one explicit ADB device' \
		'  make launch                    Launch on the local emulator' \
		'  make launch DEVICE=<serial>    Launch on one explicit ADB device' \
		'  make stop                      Stop the local emulator' \
		'  make doctor                    Check tools, acceleration, and ADB'

setup:
	./launcher.sh setup

build:
	./launcher.sh build

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

launch:
	./launcher.sh launch $(DEVICE)

stop:
	./launcher.sh stop

doctor:
	./launcher.sh doctor
