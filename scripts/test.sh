#!/bin/bash

TESTDIR=/tmp/coursier-s3

mkdir -p $TESTDIR

s3-server -d $TESTDIR -h localhost -p 4568 -b test.coursier-s3.com &

sbt coursier-s3/test

SERVERPID=$(ps -f | grep "s3-server" | grep -v "grep" | awk '{print $2}')

kill -9 $SERVERPID

rm -rf $TESTDIR
