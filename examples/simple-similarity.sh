#!/bin/sh

echo &&
curl -s -XDELETE "http://localhost:9200/test"

echo &&
curl -s -XPUT "http://localhost:9200/test" -d '{
  "settings": {
   "index": {
     "number_of_shards": 1,
     "number_of_replicas": 0
   },
   "similarity": {
     "simpleSimilarity": {
       "type": "simple-similarity"
     }
   }
  }
}'

echo &&
curl -XPUT 'localhost:9200/test/type1/_mapping' -d '{
 "type1": {
   "properties": {
     "simpleName": {
       "type": "string",
       "similarity": "simpleSimilarity"
     },
     "name": {
       "type": "string"
     }
   }
 }
}'

echo && curl -s -XPUT "localhost:9200/test/type1/1" -d '{"name" : "foo bar baz", "simpleName" : "foo bar baz"}' && echo
echo && curl -s -XPUT "localhost:9200/test/type1/2" -d '{"name" : "foo foo foo", "simpleName" : "foo foo foo"}' && echo
echo && curl -s -XPUT "localhost:9200/test/type1/3" -d '{"name" : "bar baz", "simpleName" : "bar baz"}' && echo

echo && curl -s -XPOST "http://localhost:9200/test/_refresh" && echo

echo &&
echo 'expecting different score' &&
curl -s "localhost:9200/test/type1/_search?pretty=true" -d '{
 "query": {
   "match": {
     "name": "foo"
   }
 }
}'

echo &&
echo 'expecting the same score' &&
curl -s "localhost:9200/test/type1/_search?pretty=true" -d '{
  "explain": false,
  "query": {
    "match": {
      "simpleName": "foo"
    }
  }
}'

echo &&
echo 'expecting different score' &&
curl -s "localhost:9200/test/type1/_search?pretty=true" -d '{
 "query": {
   "match": {
     "name": "bar"
   }
 }
}'

echo &&
echo 'expecting the same score' &&
curl -s "localhost:9200/test/type1/_search?pretty=true" -d '{
  "explain": false,
  "query": {
    "match": {
      "simpleName": "bar"
    }
  }
}'
