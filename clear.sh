#!/bin/bash

find . -type d -name target -print0 | xargs -0 rm -rfv
rm -rfv bin #lib
