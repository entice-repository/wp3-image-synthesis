#!/bin/sh

cd ${GIT_EXPORT_DIR}/recipe-based/frontend/ 
# Update webservice endpoint reported in urls for result, images, etc.
sed -ir "s/(ENDPOINT *= *\").*/\1${WEBSERVICE_ENDPOINT}\"/" config.py
exec "$@"