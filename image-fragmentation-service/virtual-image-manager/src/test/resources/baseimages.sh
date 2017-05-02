#!/bin/bash
echo OBSOLETE
# curl -X POST -s -H "Token: entice" -H "Content-Type: application/json" -d '{owner:admin,url:"http://host"}' http://localhost:8080/virtual-image-manager/rest/baseimages/ > baseImageId || exit 1
# cat baseImageId
# curl http://localhost:8080/virtual-image-manager/rest/baseimages/$(cat baseImageId)
# echo
# curl -X DELETE -H "token: entice" -H "user: admin" http://localhost:8080/virtual-image-manager/rest/baseimages/$(cat baseImageId) || exit 1
# echo Base image deleted