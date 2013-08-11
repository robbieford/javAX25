#!/bin/sh

DIR=`dirname $0`

java -cp $DIR/bin -Dinput="$APRS_INPUT" sivantoledo.ax25test.Test
