import os
import sys
import json
import shutil
import tempfile
import csv
import subprocess
from tqdm import tqdm

def extract_jar(jar_path, extract_dir):
    try:
        subprocess.run(["unzip", jar_path, "-d", extract_dir], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return True
    except:
        return False

def read_target_files():
    with open("files.txt", "r") as f:
        return [line.strip() for line in f if line.strip()]

def process_file_with_structure_jar(file_path):
    try:
        subprocess.run(["java", "-jar", "structure-extractor.jar", file_path], check=True, stderr=subprocess.PIPE)
        if not os.path.exists("output.json"):
            return None
        with open("output.json", "r") as f:
            data = json.load(f)
        os.remove("output.json")
        return data
    except:
        return None

def find_java_files(dir_path, target_files):
    found = {}
    for root, dirs, files in os.walk(dir_path):
        for file_name in files:
            if file_name in target_files:
                found[file_name] = os.path.join(root, file_name)
    return found

def process_directory(dir_path, target_files):
    java_files = find_java_files(dir_path, target_files)
    if not java_files:
        return None
    result = {}
    for file_name, file_path in java_files.items():
        try:
            file_data = process_file_with_structure_jar(file_path)
            if file_data is None:
                return None
            result[file_data.get("name", "Unknown")] = file_data
        except:
            return None
    return result

def compare_values(ref_val, stu_val, path):
    differences = []
    if type(ref_val) != type(stu_val):
        differences.append(f"{path} - Type mismatch: expected {type(ref_val).__name__}, got {type(stu_val).__name__}")
        return differences
    
    if isinstance(ref_val, dict):
        for key in ref_val:
            if key not in stu_val:
                differences.append(f"{path}.{key} - Missing key")
            else:
                differences.extend(compare_values(ref_val[key], stu_val[key], f"{path}.{key}"))
        for key in stu_val:
            if key not in ref_val:
                differences.append(f"{path}.{key} - Extra key")
    elif isinstance(ref_val, list):
        if path.endswith('.methods'):
            differences.extend(compare_methods(ref_val, stu_val, path))
        elif path.endswith('.fields'):
            differences.extend(compare_fields(ref_val, stu_val, path))
        elif path.endswith('.constructors'):
            differences.extend(compare_constructors(ref_val, stu_val, path))
        elif path.endswith('.extends') or path.endswith('.implements'):
            if sorted(ref_val) != sorted(stu_val):
                differences.append(f"{path} - Content mismatch: expected {sorted(ref_val)}, got {sorted(stu_val)}")
        else:
            if len(ref_val) != len(stu_val):
                differences.append(f"{path} - Length mismatch: expected {ref_val} ({len(ref_val)} items), got {stu_val} ({len(stu_val)} items)")
            for i, (ref_item, stu_item) in enumerate(zip(ref_val, stu_val)):
                differences.extend(compare_values(ref_item, stu_item, f"{path}[{i}]"))
    else:
        if ref_val != stu_val:
            differences.append(f"{path} - Value mismatch: expected {ref_val}, got {stu_val}")
    
    return differences

def compare_methods(ref_methods, stu_methods, path):
    differences = []
    ref_map = {(m.get('name', ''), tuple(m.get('params', []))): m for m in ref_methods}
    stu_map = {(m.get('name', ''), tuple(m.get('params', []))): m for m in stu_methods}
    
    for sig, ref_method in ref_map.items():
        if sig not in stu_map:
            differences.append(f"{path} - Missing method: {sig[0]} with params {list(sig[1])}")
        else:
            differences.extend(compare_values(ref_method, stu_map[sig], f"{path}.{sig[0]}({','.join(sig[1])})"))
    
    for sig in stu_map:
        if sig not in ref_map:
            differences.append(f"{path} - Extra method: {sig[0]} with params {list(sig[1])}")
    
    return differences

def compare_fields(ref_fields, stu_fields, path):
    differences = []
    ref_map = {f.get('name', ''): f for f in ref_fields}
    stu_map = {f.get('name', ''): f for f in stu_fields}
    
    for name, ref_field in ref_map.items():
        if name not in stu_map:
            differences.append(f"{path} - Missing field: {name}")
        else:
            differences.extend(compare_values(ref_field, stu_map[name], f"{path}.{name}"))
    
    for name in stu_map:
        if name not in ref_map:
            differences.append(f"{path} - Extra field: {name}")
    
    return differences

def compare_constructors(ref_ctors, stu_ctors, path):
    differences = []
    ref_map = {tuple(c.get('params', [])): c for c in ref_ctors}
    stu_map = {tuple(c.get('params', [])): c for c in stu_ctors}
    
    for params, ref_ctor in ref_map.items():
        if params not in stu_map:
            differences.append(f"{path} - Missing constructor with params {list(params)}")
        else:
            differences.extend(compare_values(ref_ctor, stu_map[params], f"{path}.constructor({','.join(params)})"))
    
    for params in stu_map:
        if params not in ref_map:
            differences.append(f"{path} - Extra constructor with params {list(params)}")
    
    return differences

def compare_uml_files(ref_data, student_data):
    if isinstance(ref_data, list):
        ref_dict = {item["name"]: item for item in ref_data if "name" in item}
        ref_data = ref_dict
    
    if isinstance(student_data, list):
        stu_dict = {item["name"]: item for item in student_data if "name" in item}
        student_data = stu_dict
    
    differences = []
    
    for class_name in ref_data:
        if class_name not in student_data:
            differences.append(f"Missing class: {class_name}")
        else:
            differences.extend(compare_values(ref_data[class_name], student_data[class_name], f"Class {class_name}"))
    
    for class_name in student_data:
        if class_name not in ref_data:
            differences.append(f"Extra class: {class_name}")
    
    return differences

def process_student_submissions(base_dir, solution_file):
    output_dir = "umls/json"
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs("umls", exist_ok=True)
    
    with open(solution_file, 'r') as f:
        ref_data = json.load(f)
    
    target_files = read_target_files()
    print(f"Target files to process: {target_files}")
    
    jar_files = []
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if file.endswith('.jar'):
                jar_files.append(os.path.join(root, file))
    
    results = []
    
    for jar_file in tqdm(jar_files, desc="Processing JAR files"):
        rel_path = os.path.relpath(jar_file, base_dir)
        student_id = os.path.splitext(rel_path)[0].replace(os.sep, '_')
        
        extract_dir = tempfile.mkdtemp()
        try:
            if extract_jar(jar_file, extract_dir):
                student_data = process_directory(extract_dir, target_files)
                if student_data:
                    with open(os.path.join(output_dir, f"{student_id}.json"), 'w') as f:
                        json.dump(student_data, f, indent=4)
                    differences = compare_uml_files(ref_data, student_data)
                    results.append((student_id, "FAIL" if differences else "PASS", "\n".join(differences)))
                else:
                    results.append((student_id, "ERROR", "Failed to generate UML data"))
            else:
                results.append((student_id, "ERROR", "Failed to extract JAR file"))
        finally:
            shutil.rmtree(extract_dir, ignore_errors=True)
    
    results.sort(key=lambda x: x[0])
    with open(os.path.join("umls", "uml-score.csv"), 'w', newline='') as sf, open(os.path.join("umls", "uml-comments.csv"), 'w', newline='') as cf:
        csv.writer(sf).writerow(["ID", "Status"])
        csv.writer(cf).writerow(["ID", "Status", "Comments"])
        for result in tqdm(results, desc="Writing results"):
            csv.writer(sf).writerow(result[:2])
            csv.writer(cf).writerow(result)
    
    print(f"All UML data generated and saved to {output_dir}")
    print(f"Score and comments files saved")

def main():
    if len(sys.argv) != 3:
        print("Usage: python grade_uml.py <submissions_dir> <solution_file>")
        sys.exit(1)
    
    if not os.path.isdir(sys.argv[1]):
        print(f"Error: {sys.argv[1]} is not a directory")
        sys.exit(1)
    
    if not os.path.isfile(sys.argv[2]):
        print(f"Error: {sys.argv[2]} is not a file")
        sys.exit(1)
    
    process_student_submissions(sys.argv[1], sys.argv[2])

if __name__ == "__main__":
    main()