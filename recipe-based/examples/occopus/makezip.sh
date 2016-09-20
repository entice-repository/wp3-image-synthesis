#!/bin/bash
NAME=occopus
rm packer-$NAME.zip
zip -r packer-$NAME.zip build.json scripts http
