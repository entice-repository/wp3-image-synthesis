#!/bin/sh

# virtual-image-launcher
# IP MUST BE UPDATED accordingly to hosting IP/hostname!!!
export PUBLIC_VIRTUAL_IMAGE_COMPOSER_REST_URL=http://IP:8080/virtual-image-composer/rest
# VIRTUAL_IMAGE_MANAGER_REST_URL: see virtual-image-composer

# installer-storage
export INSTALLER_STORAGE_TOKEN=entice
export INSTALLER_STORAGE_PATH=/mnt/installers

# virtual-image-composer
export VIRTUAL_IMAGE_MANAGER_REST_URL=http://localhost:8080/virtual-image-manager/rest

# virtual-image-decomposer
export VIRTUAL_IMAGE_DECOMPOSER_TOKEN=entice
export VIRTUAL_IMAGE_DECOMPOSER_PATH=/mnt/decomposer
export INSTALLER_STORAGE_URL=http://localhost:8080/installer-storage/rest/installers
export VIRTUAL_IMAGE_COMPOSER_REST_URL=http://localhost:8080/virtual-image-composer/rest
# S3:
export S3_ENDPOINT=https://s3.lpds.sztaki.hu
export S3_BUCKET_NAME=entice-fragments
export AWS_ACCESS_KEY_ID=Entice-admin
export AWS_SECRET_ACCESS_KEY=7d1...
# knowledge-base:
# export FRAGMENT_STORAGE_TOKEN=entice
# export FRAGMENT_STORAGE_URL=https://knowledgebase.url:8443/fragment-storage/rest/fragments

# virtual-image-manager
export VIRTUAL_IMAGE_MANAGER_TOKEN=entice
# VIRTUAL_IMAGE_DECOMPOSER_TOKEN: see virtual-image-decomposer
export VIRTUAL_IMAGE_DECOMPOSER_REST_URL=http://localhost:8080/virtual-image-decomposer/rest
# INSTALLER_STORAGE_URL: see virtual-image-decomposer

# fragment-storage (NOT USED)
export FRAGMENT_STORAGE_TOKEN=entice
export FRAGMENT_STORAGE_PATH=/mnt/fragments
