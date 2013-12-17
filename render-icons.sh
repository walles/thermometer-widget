#!/bin/bash

# This script uses Blender to render logo.blend into different
# png files.  Get Blender here:
# http://www.blender.org/download/get-blender/

# Exit on error
set -e

# This works for OSX
BLENDER=$(mdfind 'kMDItemFSName = blender.app && kMDItemKind = Program')/Contents/MacOS/blender

echo $(date): Rendering icon...
$BLENDER -b logo.blend --scene mdpi -o /tmp/mdpi -F PNG -x 1 -f 1
mv /tmp/mdpi*.png src/main/res/drawable/icon.png

echo $(date): Rendering Market graphics...
$BLENDER -b logo.blend --scene market -o /tmp/market -F PNG -x 1 -f 1
mv /tmp/market*.png market-icon.png

echo $(date): Done!
