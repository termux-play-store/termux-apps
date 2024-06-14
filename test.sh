#!/bin/bash

value=apa.23
if ! [[ "$value" =~ ^[0-9]+(\.[0-9]+)?$ ]]
then
   echo bad
fi

