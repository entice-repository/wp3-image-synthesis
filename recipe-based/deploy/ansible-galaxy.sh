#!/bin/sh
#set -eu

ansible-galaxy install $1 --roles-path ./roles/ -r requirements.yml
