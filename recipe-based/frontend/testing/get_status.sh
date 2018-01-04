#!/bin/bash

if [ $# -eq 0 ]
  then
    echo "$0 <request_id>"
    exit 1
fi

. ./config.sh

curl -H "Content-Type: application/json" -X GET ${E_BUILD_URL}/$1

