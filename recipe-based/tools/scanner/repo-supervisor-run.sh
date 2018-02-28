#!/bin/bash

JSON_OUTPUT=1 node ./repo-supervisor/dist/cli.js $1 | jq '.' 

exit 0
