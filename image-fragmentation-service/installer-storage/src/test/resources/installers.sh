#!/bin/bash
INSTALLER_ID=mysql-server
curl http://localhost:8080/installer-storage/rest/installers/
echo
curl http://localhost:8080/installer-storage/rest/installers/$INSTALLER_ID
echo
curl http://localhost:8080/installer-storage/rest/installers/$INSTALLER_ID/script
echo
curl http://localhost:8080/installer-storage/rest/installers/$INSTALLER_ID/init
echo