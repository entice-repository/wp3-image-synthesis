#!/bin/bash
NAME=wordpress
rm packer-$NAME.zip
zip -r packer-$NAME.zip build.json cookbooks httpdir
