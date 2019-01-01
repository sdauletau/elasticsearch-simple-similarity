<!--
  Title: Elasticsearch simple similarity (aka "ignore tf-idf") plugin
  Description: Elasticsearch plugin that ignores tf-idf.
  Author: sdauletau
  -->
  
# Elasticsearch "ignore tf-idf" plugin

## Build

./gradlew clean assemble

Note, that versions 6.5.x require Java 11.

## Install

./scripts/install-plugin.sh

Re-start elasticsearch

## Examples

./examples/simple-similarity.sh
