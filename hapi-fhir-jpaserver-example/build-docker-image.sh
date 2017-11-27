#!/bin/sh

cd ..
mvn package -Dmaven.test.skip=true
cd hapi-fhir-jpaserver-example
docker build -t rsna/s4s-fhir-broker .

