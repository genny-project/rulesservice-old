#!/bin/bash
mvn clean package
mvn eclipse:eclipse
rm -Rf .vertx/*
