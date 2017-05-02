#!/bin/bash
JSON='{cloud:sztaki,endpoint:"http://cfe2.lpds.sztaki.hu:4567",accessKey:"ahajnal@sztaki.hu",secretKey:70a9dbcd3e11ddbf17c31c3155f472ba6945b5f9,instanceType:m1.medium,cloudImageId:ami-00001204,virtualImageId:21869367-abe2-43c7-9422-08373c043caa}'
curl -X POST -v -H "Content-Type: application/json" -d $JSON http://localhost:8080/virtual-image-launcher/rest/launcher
