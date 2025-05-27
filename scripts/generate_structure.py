import os
import sys
import json
import subprocess

def read_target_files():
    with open("files.txt", "r") as f:
        return [line.strip() for line in f if line.strip()]

def process_directory(dir_path, target_files):
    results = []
    for root, dirs, files in os.walk(dir_path):
        for name in files:
            if name in target_files:
                path = os.path.join(root, name)
                try:
                    subprocess.run(["java", "-jar", "structure-extractor.jar", path], check=True)
                    with open("output.json", "r") as f:
                        data = json.load(f)
                    results.append(data)
                    print(f"Processed file: {path}")
                    os.remove("output.json")
                except Exception as e:
                    print(f"Error processing file {path}: {e}")
    return results

def main():
    if len(sys.argv) != 3:
        print("Usage: python generate_struct.py <input_dir> <output_name.json>")
        sys.exit(1)
    input_dir = sys.argv[1]
    output_file = sys.argv[2]
    if not os.path.isdir(input_dir):
        print(f"Error: {input_dir} is not a directory")
        sys.exit(1)
    try:
        targets = read_target_files()
    except FileNotFoundError:
        print("Error: files.txt not found in current directory")
        sys.exit(1)
    print(f"Target files to process: {targets}")
    results = process_directory(input_dir, targets)
    if results:
        with open(output_file, "w") as f:
            json.dump(results, f, indent=4)
        print(f"Generated data and saved to {output_file}")
    else:
        print("No matching files found")

if __name__ == "__main__":
    main()