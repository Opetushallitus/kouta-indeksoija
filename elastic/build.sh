#!/bin/bash
cd "${0%/*}"
docker build -t elasticsearch-kouta .
docker tag elasticsearch-kouta 190073735177.dkr.ecr.eu-west-1.amazonaws.com/utility/elasticsearch-kouta:8.15.0
cd -
