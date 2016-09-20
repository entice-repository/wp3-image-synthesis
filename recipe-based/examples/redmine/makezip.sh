#!/bin/bash
NAME=redmine
rm packer-$NAME.zip
zip -r packer-$NAME.zip build.json cookbooks httpdir
