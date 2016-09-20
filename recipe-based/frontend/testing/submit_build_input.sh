#!/bin/bash

if [ $# -eq 0 ]
  then
    echo "Usage: $0 <build-input.json>"
    exit 1
fi

curl -H "Content-Type: application/json" -X POST http://localhost:4000/api/imagebuilder/build --data-binary @$1

