#!/usr/bin/env bash

(cd server; sbt ';clean;test;fatjar') && \
(cd ui; npm build) && \
rm -rf build && \
mkdir -p build/ui && \
cp server/target/scala-2.12/server-assembly-2.0.0-SNAPSHOT.jar build && \
cp -r ui/dist build/ui

