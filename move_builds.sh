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

        case "$version_name" in
            1.21.3-fabric|1.21.3-neoforge)
                echo "Skipping development-only builds from $version_name..."
                continue
                ;;
        esac
        
        # Path to build/libs in this version directory
        build_libs_dir="$version_dir/build/libs"
        
        if [ -d "$build_libs_dir" ]; then
            echo "Moving builds from $version_name..."
            
            # Move release jar files, excluding development and source artifacts
            for jar in "$build_libs_dir"/*.jar; do
                if [ -f "$jar" ]; then
                    # Skip dev and sources jars
                    if [[ "$jar" != *"-dev.jar"
                    && "$jar" != *"-dev-jarjar.jar"
                    && "$jar" != *"-sources.jar"
                    && "$jar" != *"-transformProductionFabric.jar"
                    && "$jar" != *"-sources.jar"
                    && "$jar" != *"-shadow.jar"
                    && "$jar" != *"-transformProductionNeoForge.jar"
                    && "$jar" != *"-thin.jar"
                    && "$jar" != *"folia-live-test"*
                    && "$jar" != *"proxy-lifecycle-test"*
                    ]]; then
                        # Move the file to output directory
                        mv "$jar" "$OUTPUT_DIR/"
                        echo "  Moved $(basename "$jar")"
                    else
                        echo "  Skipped development-only $(basename "$jar")"
                    fi
                fi
            done
        else
            echo "No build/libs directory found in $version_name"
        fi
    fi
done

# Iterate through each directory in paper versions/
PAPER_VERSIONS_DIR="$SCRIPT_DIR/paper/versions"
for version_dir in "$PAPER_VERSIONS_DIR"/*/ ; do
    if [ -d "$version_dir" ]; then
        version_name=$(basename "$version_dir")
        build_libs_dir="$version_dir/build/libs"

        if [ -d "$build_libs_dir" ]; then
            echo "Moving Paper builds from $version_name..."

            for jar in "$build_libs_dir"/*.jar; do
                if [ -f "$jar" ]; then
                    if [[ "$jar" != *"-dev.jar"
                    && "$jar" != *"-sources.jar"
                    && "$jar" != *"-shadow.jar"
                    && "$jar" != *"folia-live-test"*
                    ]]; then
                        mv "$jar" "$OUTPUT_DIR/"
                        echo "  Moved $(basename "$jar")"
                    else
                        echo "  Skipped development-only $(basename "$jar")"
                    fi
                fi
            done
        else
            echo "No build/libs directory found in paper $version_name"
        fi
    fi
done

echo "Build files have been moved to $OUTPUT_DIR"
