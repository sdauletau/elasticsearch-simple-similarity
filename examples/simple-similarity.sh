#!/bin/sh

echo &&
curl --header "Content-Type:application/json" -s -XDELETE "http://localhost:9200/test"

echo &&
curl --header "Content-Type:application/json" -s -XPUT "http://localhost:9200/test" -d '{
  "settings": {
   "index": {
     "number_of_shards": 1,
     "number_of_replicas": 0,
     "similarity": {
       "default": {
         "type": "simple-similarity"
       }
     }
   }
  }
}'

echo &&
curl --header "Content-Type:application/json" -XPUT 'localhost:9200/test/type1/_mapping' -d '{
 "type1": {
   "properties": {
     "simpleName": {
       "type": "text"
     },
     "name": {
       "type": "text",
       "similarity": "classic"
     }
   }
 }
}' && echo

echo && curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test/type1/1" -d '{"name" : "foo bar baz", "simpleName" : "foo bar baz"}'
echo && curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test/type1/2" -d '{"name" : "foo foo foo", "simpleName" : "foo foo foo"}'
echo && curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test/type1/3" -d '{"name" : "bar baz", "simpleName" : "bar baz"}'
echo && curl --header "Content-Type:application/json" -s -XPOST "http://localhost:9200/test/_refresh" && echo

echo &&
echo 'expecting different score' &&
curl --header "Content-Type:application/json" -s "localhost:9200/test/type1/_search?pretty=true" -d '{
 "query": {
   "match": {
     "name": "foo"
   }
 }
}'

echo &&
echo 'expecting the same score, 1.0' &&
curl --header "Content-Type:application/json" -s "localhost:9200/test/type1/_search?pretty=true" -d '{
  "explain": false,
  "query": {
    "match": {
      "simpleName": "foo"
    }
  }
}'

echo &&
echo 'expecting different score' &&
curl --header "Content-Type:application/json" -s "localhost:9200/test/type1/_search?pretty=true" -d '{
 "query": {
   "multi_match": {
     "boost": 3.0,
     "query": "bar baz",
     "fields": [
       "name"
     ]
   }
 }
}'

echo &&
echo 'expecting the same score, 12.0' &&
curl --header "Content-Type:application/json" -s "localhost:9200/test/type1/_search?pretty=true" -d '{
  "explain": false,
  "query": {
    "multi_match": {
      "boost": 3.0,
      "query": "bar baz",
      "fields": [
        "simpleName^2.0"
      ]
    }
  }
}'
