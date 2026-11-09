#!/bin/bash

SRC_DIR="src"
OUTPUT_FILE="/home/shanto/Documents/bdf_raw.txt"

# Clear output file if it exists
> "$OUTPUT_FILE"

# Loop through all files in src
find "$SRC_DIR" -type f | while read -r file; do
    echo "===== FILE: $file =====" >> "$OUTPUT_FILE"
    cat "$file" >> "$OUTPUT_FILE"
    echo -e "\n\n" >> "$OUTPUT_FILE"
done

echo "All files from $SRC_DIR have been combined into $OUTPUT_FILE"
