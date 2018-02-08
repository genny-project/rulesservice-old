#!/bin/bash

if [ -z "${1}" ]; then
   version="latest"
else
   version="${1}"
fi

docker build  -t gennyproject/rulesservice:${version} .
#docker build  --no-cache -t gennyproject/rulesservice:${version} .
