#!/bin/bash
prj=$1
./build.sh
./build-docker.sh
cd ../prj_$(echo $prj)
./build-docker.sh
cd ../rulesservice
