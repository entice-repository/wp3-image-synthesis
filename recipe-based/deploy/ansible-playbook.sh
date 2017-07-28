#!/bin/bash

if [ "$#" -lt 2 ]; then
	echo "ERROR: Usage $0 <LIMIT-HOST> <YML> [<OPTION>] "
	exit 255
fi

if [ ! -f "$2" ]; then
	echo "ERROR: file '$2' cannot be found."
	exit 254
fi

ansible-playbook \
	--limit $1 \
	--ask-vault-pass \
	-K \
	-v \
	-s \
	$2 \
	${@:3}