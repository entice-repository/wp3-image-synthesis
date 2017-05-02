#!/bin/bash
set -euxv

echo Downloading source image...
if [ ! -f source-image.status ]; then
	curl $CURL_OPTIONS $SOURCE_BASE_IMAGE_URL -o $SOURCE_IMAGE_FILE || error ${LINENO} "ERROR: Cannot download base image: ${SOURCE_BASE_IMAGE_URL}" 1
	touch source-image.status
fi
	
echo Mounting source image...
mkdir -p $SOURCE_IMAGE_DIR
mountSourceImage

echo Downloading assembly script...
curl -H 'Init: false' $CURL_OPTIONS $MERGE_SCRIPT_URL -o $MERGE_SCRIPT || error ${LINENO} "ERROR: Cannot download assembly script: ${MERGE_SCRIPT_URL}" 2
chmod u+x $MERGE_SCRIPT

echo Running assembly script...
cp -f "${MERGE_SCRIPT}" "${SOURCE_IMAGE_DIR}"
cd "${SOURCE_IMAGE_DIR}"
sh "${MERGE_SCRIPT}"
rm "${MERGE_SCRIPT}"
echo Source virtual image is done

echo Unmounting source image...
unmountSourceImage

# cleanup: SOURCE_IMAGE_FILE, SOURCE_IMAGE_DIR, MERGE_SCRIPT 
