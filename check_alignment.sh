#!/bin/bash

# 16KB Alignment Checker for Android Libraries
# This script checks if .so files are properly aligned for 16KB page size

echo "=== 16KB Page Alignment Checker ==="
echo ""

NDK_PATH="/Users/yuuto/Library/Android/sdk/ndk/29.0.14206865"
READELF="$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"

if [ ! -f "$READELF" ]; then
    echo "Error: readelf not found at $READELF"
    echo "Please update NDK_PATH in this script"
    exit 1
fi

check_alignment() {
    local so_file="$1"
    echo "Checking: $(basename $so_file)"
    
    # Get LOAD segment alignment
    $READELF -l "$so_file" 2>/dev/null | grep "LOAD" | while read line; do
        # Extract alignment value (last column)
        align=$(echo "$line" | awk '{print $NF}')
        
        # Convert hex to decimal if needed
        if [[ $align == 0x* ]]; then
            align=$((align))
        fi
        
        if [ "$align" -lt 16384 ]; then
            echo "  ❌ FAIL: Alignment = $align (< 16384)"
            echo "     Segment: $line"
        else
            echo "  ✅ PASS: Alignment = $align"
        fi
    done
    echo ""
}

echo "📦 Checking built libraries in app/build..."
find /Users/yuuto/learn_lab/RecEmotion/app/build -name "*.so" -type f 2>/dev/null | while read so_file; do
    check_alignment "$so_file"
done

echo "📦 Checking jniLibs..."
find /Users/yuuto/learn_lab/RecEmotion/app/src/main/jniLibs -name "*.so" -type f 2>/dev/null | while read so_file; do
    check_alignment "$so_file"
done

echo "=== Check Complete ==="
