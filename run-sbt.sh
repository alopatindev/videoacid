#!/bin/bash

function cd_project_dir() {
    cd $(dirname "$0")
    echo "current directory is $(pwd)"
}

set -e
cd_project_dir

ln -sf ../../scripts/pre-commit .git/hooks/pre-commit

#scripts/build_assets.sh

echo "starting sbt..."
sbt
