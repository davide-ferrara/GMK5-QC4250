.DEFAULT_GOAL := help

DEVICE ?=
HEADLESS ?=

.PHONY: help setup build start run test install launch set-home stop doctor

help:
	@printf '%s\n' \
		'Golf Mk5 launcher' \
		'' \
		'  make setup                     Install the local Android toolchain' \
		'  make run                       Build/install both apps, then launch the launcher' \
		'  make build                     Compile the launcher and radio release APKs' \
		'  make start [HEADLESS=1]        Start the Android emulator' \
		'  make test                      Run UI tests and restore the production app' \
		'  make install DEVICE=<serial>   Build and install both apps on an ADB device' \
		'  make launch DEVICE=<serial>    Launch on one ADB device' \
		'  make set-home DEVICE=<serial>  Select it as default HOME' \
		'  make stop                      Stop the local emulator' \
		'  make doctor                    Check tools and connected ADB devices'

setup:
	./launcher.sh setup

build:
	./launcher.sh build

start:
	./launcher.sh start $(if $(HEADLESS),--headless,)

run:
	./launcher.sh run

test:
	./launcher.sh test

install:
	./launcher.sh install $(DEVICE)

launch:
	./launcher.sh launch $(DEVICE)

set-home:
	./launcher.sh set-home $(DEVICE)

stop:
	./launcher.sh stop

doctor:
	./launcher.sh doctor
