#!/bin/bash -eux

export DEBIAN_FRONTEND=noninteractive;

# Apt cleanup.
apt autoremove
apt update

sync
