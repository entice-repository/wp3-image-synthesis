#!/bin/bash

set -ex

PDIR=env/entice-ib-backend

echo "Reseting '$PDIR'"

rm -rf "$PDIR"

virtualenv "$PDIR"
source "$PDIR"/bin/activate
pip install --upgrade pip
pip install -r requirements.txt 

set +ex
echo "It's dangerous to go alone. Take these:"
echo "source '$PDIR/bin/activate'"

