#!/bin/bash

# Get the script's directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Versions directory path
VERSIONS_DIR="$SCRIPT_DIR/mods/versions"

# Create output directory if it doesn't exist
OUTPUT_DIR="$SCRIPT_DIR/builds"
mkdir -p "$OUTPUT_DIR"

# Iterate through each directory in versions/
for version_dir in "$VERSIONS_DIR"/*/ ; do
    if [ -d "$version_dir" ]; then
        # Get the version name from directory path
        version_name=$(basename "$version_dir")
        
        # Path to build/libs in this version directory
        build_libs_dir="$version_dir/build/libs"
        
        if [ -d "$build_libs_dir" ]; then
            echo "Moving builds from $version_name..."
            
            # Move all jar files except *-dev.jar and *-sources.jar
            for jar in "$build_libs_dir"/*.jar; do
                if [ -f "$jar" ]; then
                    # Skip dev and sources jars
                    if [[ "$jar" != *"-dev.jar"
                    && "$jar" != *"-sources.jar"
                    && "$jar" != *"-transformProductionFabric.jar"
                    && "$jar" != *"-sources.jar"
                    && "$jar" != *"-shadow.jar"
                    && "$jar" != *"-transformProductionNeoForge.jar"
                    && "$jar" != *"-thin.jar"
                    ]]; then
                        # Move the file to output directory
                        mv "$jar" "$OUTPUT_DIR/"
                        echo "  Moved $(basename "$jar")"
                    fi
                fi
            done
        else
            echo "No build/libs directory found in $version_name"
        fi
    fi
done

echo "Build files have been moved to $OUTPUT_DIR"
open builds