#!/bin/bash

if [ -n "$S3_ENDPOINT" ] && [ -n "$S3_BUCKET_NAME" ]; then
	FRAGMENT_URL=${S3_ENDPOINT}/${S3_BUCKET_NAME}/${FRAGMENT_ID}
	mv ${DELTA_FILE} ${FRAGMENT_ID}
	aws --endpoint-url ${S3_ENDPOINT} --no-verify-ssl s3 cp ${FRAGMENT_ID} s3://${S3_BUCKET_NAME} --grants read=uri=http://acs.amazonaws.com/groups/global/AllUsers 2> upload.status || error ${LINENO} "ERROR: Cannot upload fragment to S3 storage: ${FRAGMENT_URL}" 41 
	mv ${FRAGMENT_ID} ${DELTA_FILE}
else
	FRAGMENT_URL="${FRAGMENT_STORAGE_URL}${FRAGMENT_ID}"
	curl ${CURL_OPTIONS} -X POST --upload-file ${DELTA_FILE} -H "Token: ${FRAGMENT_STORAGE_TOKEN}" -H "Content-Type: application/gzip" ${FRAGMENT_URL} || error ${LINENO} "ERROR: Cannot upload fragment: ${FRAGMENT_URL}" 41
fi

if [ -n "$KNOWLEDGE_BASE_REF' ]; then
    echo Uploading fragment to Knowledge Bas
    # http://193.2.72.90:7070/JerseyREST/rest/gui/optimized_vmi_upload/<original-image-id>; the parameters are: file_upload (type: File) and parent_vmi_id (type: String)
    curl -F "parent_vmi_id=$SOURCE_VIRTUAL_IMAGE_ID" -F "file_upload=@${DELTA_FILE}" http://193.2.72.90:7070/JerseyREST/rest/gui/optimized_vmi_upload/${TARGET_VIRTUAL_IMAGE_ID}
fi

echo "${FRAGMENT_URL}" > url

# cleanup: DELTA_FILE