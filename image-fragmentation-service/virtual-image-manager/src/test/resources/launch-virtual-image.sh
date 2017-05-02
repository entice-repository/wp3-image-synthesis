#!/bin/bash
if [ $# < 1 ]; then
  echo Missing parameter: virtual image id
  exit 1
fi
JSON='{virtualImageId:"'$1'",keypair:ahajnal_keypair,cloud:sztaki,endpoint:"http://cfe2.lpds.sztaki.hu:4567",accessKey:"ahajnal@sztaki.hu",secretKey:60a...,instanceType:m1.medium}'
curl -X POST -v -H "Content-Type: application/json" -d $JSON http://localhost:8080/virtual-image-launcher/rest/launcher
