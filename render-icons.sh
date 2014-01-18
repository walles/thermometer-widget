#!/bin/bash

# This script uses Blender to render logo.blend into different
# png files.  Get Blender here:
# http://www.blender.org/download/get-blender/

# Exit on error
set -e

MYDIR=$(cd $(dirname "$0") ; pwd)

# This works on OSX
INKSCAPE=$(mdfind 'kMDItemFSName = Inkscape.app && kMDItemKind = Program')/Contents/Resources/bin/inkscape
if [ ! -x $INKSCAPE ] ; then
    echo ERROR: Inkscape not found, please install Inkscape
    exit 1
fi

# This works on OSX
BLENDER=$(mdfind 'kMDItemFSName = blender.app && kMDItemKind = Program')/Contents/MacOS/blender
if [ ! -x $BLENDER ] ; then
    echo ERROR: Blender not found, please install Blender
    exit 1
fi

# Required on Johan's machine...
unset PYTHONPATH

cd $MYDIR/graphics

echo $(date): Rendering icons...

$INKSCAPE --export-png=thermometer-face.png --export-area-drawing thermometer-face.svg


mkdir -p $MYDIR/src/main/res/drawable-mdpi
$BLENDER -b logo.blend --scene mdpi -o /tmp/mdpi -F PNG -x 1 -f 1
mv /tmp/mdpi*.png $MYDIR/src/main/res/drawable-mdpi/icon.png

mkdir -p $MYDIR/src/main/res/drawable-hdpi
$BLENDER -b logo.blend --scene hdpi -o /tmp/hdpi -F PNG -x 1 -f 1
mv /tmp/hdpi*.png $MYDIR/src/main/res/drawable-hdpi/icon.png

mkdir -p $MYDIR/src/main/res/drawable-xhdpi
$BLENDER -b logo.blend --scene xhdpi -o /tmp/xhdpi -F PNG -x 1 -f 1
mv /tmp/xhdpi*.png $MYDIR/src/main/res/drawable-xhdpi/icon.png

mkdir -p $MYDIR/src/main/res/drawable-xxhdpi
$BLENDER -b logo.blend --scene xxhdpi -o /tmp/xxhdpi -F PNG -x 1 -f 1
mv /tmp/xxhdpi*.png $MYDIR/src/main/res/drawable-xxhdpi/icon.png

echo $(date): Rendering Market graphics...
$BLENDER -b logo.blend --scene market -o /tmp/market -F PNG -x 1 -f 1
mv /tmp/market*.png $MYDIR/graphics/market-icon.png

echo $(date): Done!
