#!/bin/bash

if [ $# -eq 0 ]
  then
    echo "Usage: $0 <build-input.json>"
    exit 1
fi

. ./config.sh

curl -H "Content-Type: application/json" -X POST ${E_BUILD_URL} --data-binary @$1

