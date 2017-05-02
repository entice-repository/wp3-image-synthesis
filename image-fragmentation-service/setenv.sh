# virtual-image-launcher
# IP MUST BE UPDATED accordingly to hosting IP/hostname!!!
PUBLIC_VIRTUAL_IMAGE_COMPOSER_REST_URL=http://IP:8083/virtual-image-composer/rest
# VIRTUAL_IMAGE_MANAGER_REST_URL: see virtual-image-composer

# installer-storage
INSTALLER_STORAGE_TOKEN=entice
INSTALLER_STORAGE_PATH=/mnt/installers

# virtual-image-composer
VIRTUAL_IMAGE_MANAGER_REST_URL=http://localhost:8080/virtual-image-manager/rest

# virtual-image-decomposer
VIRTUAL_IMAGE_DECOMPOSER_TOKEN=entice
VIRTUAL_IMAGE_DECOMPOSER_PATH=/mnt/decomposer
INSTALLER_STORAGE_URL=http://localhost:8080/installer-storage/rest/installers
VIRTUAL_IMAGE_COMPOSER_REST_URL=http://localhost:8080/virtual-image-composer/rest
# S3:
S3_ENDPOINT=https://s3.lpds.sztaki.hu
S3_BUCKET_NAME=entice-fragments
AWS_ACCESS_KEY_ID=Entice-admin
AWS_SECRET_ACCESS_KEY=7d1...
# knowledge-base:
# FRAGMENT_STORAGE_TOKEN=entice
# FRAGMENT_STORAGE_URL=https://knowledgebase.url:8443/fragment-storage/rest/fragments

# virtual-image-manager
VIRTUAL_IMAGE_MANAGER_TOKEN=entice
# VIRTUAL_IMAGE_DECOMPOSER_TOKEN: see virtual-image-decomposer
VIRTUAL_IMAGE_DECOMPOSER_REST_URL=http://localhost:8080/virtual-image-decomposer/rest
# INSTALLER_STORAGE_URL: see virtual-image-decomposer

# fragment-storage (NOT USED)
FRAGMENT_STORAGE_TOKEN=entice
FRAGMENT_STORAGE_PATH=/mnt/fragments
