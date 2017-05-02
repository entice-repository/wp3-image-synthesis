#!/bin/bash
set -euxv

echo Downloading snapshot image...
if [ ! -f target-image.status ]; then
	curl $CURL_OPTIONS $SNAPSHOT_URL -o $TARGET_IMAGE_FILE || error ${LINENO} "ERROR: Cannot download target image: $SNAPSHOT_URL" 31
	touch target-image.status 
fi

# cleanup: TARGET_IMAGE_FILE