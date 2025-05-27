#!/usr/bin/env python3

import os
import sys
import json
import argparse
import shutil
import tempfile
import subprocess
import csv
from pathlib import Path
from tqdm import tqdm
import docker
import tarfile
import io

def check_jar_exists():
    return os.path.exists('structure-extractor.jar')

def create_source_archive():
    archive_stream = io.BytesIO()
    files_to_add = []
    
    if not os.path.exists('core'):
        print("ERROR: 'core' directory not found")
        sys.exit(1)
    
    for root, dirs, files in os.walk('core'):
        dirs[:] = [d for d in dirs if not d.startswith('.') and d != '__pycache__']
        for file in files:
            if not file.startswith('.') and not file.endswith('.pyc'):
                file_path = os.path.join(root, file)
                archive_name = os.path.relpath(file_path, 'core')
                files_to_add.append((file_path, archive_name))
    
    with tarfile.open(fileobj=archive_stream, mode='w') as tar:
        for file_path, archive_name in tqdm(files_to_add, desc="Archiving core files", unit="file"):
            tar.add(file_path, arcname=archive_name)
    
    archive_stream.seek(0)
    return archive_stream.getvalue()

def build_structure_extractor(java_image):
    print("Building structure extractor...")
    
    try:
        client = docker.from_env()
    except Exception as e:
        print(f"ERROR: Failed to connect to Docker: {e}")
        sys.exit(1)
    
    print(f"Pulling Java image: {java_image}")
    try:
        image = client.images.pull(java_image)
    except docker.errors.ImageNotFound:
        print(f"ERROR: Image '{java_image}' not found")
        sys.exit(1)
    except docker.errors.APIError as e:
        print(f"ERROR: Failed to pull image '{java_image}': {e}")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: Failed to pull Java image: {e}")
        sys.exit(1)
    
    print("Creating build container...")
    try:
        container = client.containers.run(
            image.id,
            command="sleep infinity",
            detach=True,
            working_dir="/build",
            name="structure-extractor-builder"
        )
    except docker.errors.APIError as e:
        if "Conflict" in str(e):
            print("Removing existing container...")
            try:
                old_container = client.containers.get("structure-extractor-builder")
                old_container.stop()
                old_container.remove()
                container = client.containers.run(
                    image.id,
                    command="sleep infinity",
                    detach=True,
                    working_dir="/build",
                    name="structure-extractor-builder"
                )
            except Exception as cleanup_e:
                print(f"ERROR: Failed to cleanup and recreate container: {cleanup_e}")
                sys.exit(1)
        else:
            print(f"ERROR: Failed to create container: {e}")
            sys.exit(1)
    except Exception as e:
        print(f"ERROR: Failed to create container: {e}")
        sys.exit(1)
    
    try:
        print("Creating source archive...")
        try:
            archive_data = create_source_archive()
        except Exception as e:
            print(f"ERROR: Failed to create source archive: {e}")
            sys.exit(1)
        
        print("Copying core source code to container...")
        try:
            container.put_archive("/build", archive_data)
        except Exception as e:
            print(f"ERROR: Failed to copy source code: {e}")
            sys.exit(1)
        
        print("Building JAR (attempt 1: mvnw)...")
        exit_code, output = container.exec_run(
            "sh -c './mvnw clean package assembly:single -DskipTests -q'"
        )
        
        if exit_code != 0:
            print("Building JAR (attempt 2: mvn)...")
            exit_code, output = container.exec_run(
                "sh -c 'mvn clean package assembly:single -DskipTests -q'"
            )
        
        if exit_code != 0:
            print("Installing Maven...")
            try:
                install_exit, install_output = container.exec_run(
                    "sh -c 'apt-get update -qq && apt-get install -y maven -qq'"
                )
                if install_exit != 0:
                    print(f"ERROR: Failed to install Maven: {install_output.decode()}")
                    sys.exit(1)
            except Exception as e:
                print(f"ERROR: Failed to install Maven: {e}")
                sys.exit(1)
                
            print("Building JAR (attempt 3: installed mvn)...")
            exit_code, output = container.exec_run(
                "sh -c 'mvn clean package assembly:single -DskipTests -q'"
            )
        
        if exit_code != 0:
            print(f"ERROR: Failed to build JAR after all attempts")
            if output:
                print(f"Build output: {output.decode()}")
            sys.exit(1)
        
        print("Extracting JAR from container...")
        jar_path = "/build/target/psi-1.0-SNAPSHOT-jar-with-dependencies.jar"
        
        try:
            stream, _ = container.get_archive(jar_path)
            tar_data = b"".join(chunk for chunk in tqdm(stream, desc="Downloading JAR", unit="chunk"))
        except Exception as e:
            print(f"ERROR: Failed to extract JAR from container: {e}")
            sys.exit(1)
        
        print("Saving JAR file...")
        try:
            with tarfile.open(fileobj=io.BytesIO(tar_data)) as tar:
                jar_member = tar.getmember("psi-1.0-SNAPSHOT-jar-with-dependencies.jar")
                jar_file = tar.extractfile(jar_member)
                
                with open("structure-extractor.jar", "wb") as f:
                    shutil.copyfileobj(jar_file, f)
        except Exception as e:
            print(f"ERROR: Failed to save JAR file: {e}")
            sys.exit(1)
        
    finally:
        print("Cleaning up build container...")
        try:
            container.stop()
            container.remove()
        except Exception as e:
            print(f"WARNING: Failed to cleanup container: {e}")
    
    jar_size = os.path.getsize('structure-extractor.jar')
    print(f"SUCCESS: Built structure-extractor.jar ({jar_size:,} bytes)")

def create_project_archive(input_dir, ref_dir):
    archive_stream = io.BytesIO()
    files_to_add = []
    
    required_files = ['structure-extractor.jar', 'files.txt']
    for req_file in required_files:
        if not os.path.exists(req_file):
            print(f"ERROR: Required file '{req_file}' not found")
            sys.exit(1)
        files_to_add.append((req_file, req_file))
    
    if os.path.exists(ref_dir):
        for root, dirs, files in os.walk(ref_dir):
            for file in files:
                if not file.startswith('.'):
                    file_path = os.path.join(root, file)
                    archive_name = os.path.relpath(file_path, '.')
                    files_to_add.append((file_path, archive_name))
    else:
        print(f"ERROR: Reference directory '{ref_dir}' not found")
        sys.exit(1)
    
    if os.path.exists(input_dir):
        for root, dirs, files in os.walk(input_dir):
            for file in files:
                if file.endswith('.jar'):
                    file_path = os.path.join(root, file)
                    archive_name = f"submissions/{os.path.relpath(file_path, input_dir)}"
                    files_to_add.append((file_path, archive_name))
    else:
        print(f"ERROR: Input directory '{input_dir}' not found")
        sys.exit(1)
    
    with tarfile.open(fileobj=archive_stream, mode='w') as tar:
        for file_path, archive_name in tqdm(files_to_add, desc="Archiving project files", unit="file"):
            tar.add(file_path, arcname=archive_name)
    
    archive_stream.seek(0)
    return archive_stream.getvalue()

def create_grading_script():
    grading_script = '''#!/usr/bin/env python3
import os
import sys
import json
import shutil
import tempfile
import csv
import subprocess
from tqdm import tqdm

def read_target_files():
    with open("files.txt", "r") as f:
        return [line.strip() for line in f if line.strip()]

def process_file_with_structure_jar(file_path):
    try:
        result = subprocess.run(["java", "-jar", "structure-extractor.jar", file_path], 
                              capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            error_msg = result.stderr.strip()
            if "ParseProblemException" in error_msg:
                print(f"Parse error in {file_path} - likely syntax error in student code")
                return "PARSE_ERROR"
            else:
                print(f"Java command failed for {file_path}: {error_msg}")
                return None
        if not os.path.exists("output.json"):
            print(f"No output.json generated for {file_path}")
            return None
        with open("output.json", "r") as f:
            data = json.load(f)
        os.remove("output.json")
        return data
    except subprocess.TimeoutExpired:
        print(f"Timeout processing {file_path}")
        return None
    except Exception as e:
        print(f"Exception processing {file_path}: {e}")
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
    parse_errors = []
    for file_name, file_path in java_files.items():
        try:
            file_data = process_file_with_structure_jar(file_path)
            if file_data == "PARSE_ERROR":
                parse_errors.append(file_name)
                continue
            elif file_data is None:
                return None
            result[file_data.get("name", "Unknown")] = file_data
        except Exception:
            return None
    if parse_errors and not result:
        return "PARSE_ERROR"
    return result if result else None

def generate_solution_json(ref_dir):
    print(f"Generating solution.json from {ref_dir}...")
    target_files = read_target_files()
    print(f"Looking for target files: {target_files}")
    results = []
    
    for root, dirs, files in os.walk(ref_dir):
        for name in files:
            if name in target_files:
                path = os.path.join(root, name)
                print(f"Processing solution file: {path}")
                try:
                    data = process_file_with_structure_jar(path)
                    if data and data != "PARSE_ERROR":
                        results.append(data)
                        print(f"Successfully processed solution file: {path}")
                    else:
                        print(f"Failed to process solution file: {path}")
                except Exception as e:
                    print(f"Error processing solution file {path}: {e}")
    
    if results:
        with open("solution.json", "w") as f:
            json.dump(results, f, indent=4)
        print(f"Generated solution.json with {len(results)} classes")
        return True
    else:
        print("ERROR: No valid solution files found")
        print(f"Searched in directory: {ref_dir}")
        print(f"Directory exists: {os.path.exists(ref_dir)}")
        if os.path.exists(ref_dir):
            print(f"Files in directory: {list(os.listdir(ref_dir))}")
        return False

def extract_jar(jar_path, extract_dir):
    try:
        result = subprocess.run(["unzip", "-q", jar_path, "-d", extract_dir], 
                              capture_output=True, timeout=30)
        return result.returncode == 0
    except subprocess.TimeoutExpired:
        return False
    except Exception:
        return False

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
                differences.append(f"{path} - Length mismatch: expected {ref_val}, got {stu_val}")
            else:
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

def process_submissions():
    input_dir = "submissions"
    output_dir = "results"
    json_output_dir = os.path.join(output_dir, "json")
    os.makedirs(json_output_dir, exist_ok=True)
    os.makedirs(output_dir, exist_ok=True)
    
    with open("solution.json", 'r') as f:
        ref_data = json.load(f)
    
    target_files = read_target_files()
    print(f"Target files to process: {target_files}")
    
    jar_files = []
    for root, dirs, files in os.walk(input_dir):
        for file in files:
            if file.endswith('.jar'):
                jar_files.append(os.path.join(root, file))
    
    if not jar_files:
        print(f"ERROR: No JAR files found in {input_dir}")
        return False
    
    print(f"Found {len(jar_files)} JAR files to process")
    results = []
    
    for jar_file in tqdm(jar_files, desc="Processing JAR files"):
        rel_path = os.path.relpath(jar_file, input_dir)
        student_id = os.path.splitext(rel_path)[0].replace(os.sep, '_')
        
        extract_dir = tempfile.mkdtemp()
        try:
            if extract_jar(jar_file, extract_dir):
                student_data = process_directory(extract_dir, target_files)
                if student_data == "PARSE_ERROR":
                    results.append((student_id, "ERROR", "Parse error - likely syntax error in code"))
                elif student_data:
                    with open(os.path.join(json_output_dir, f"{student_id}.json"), 'w') as f:
                        json.dump(student_data, f, indent=4)
                    differences = compare_uml_files(ref_data, student_data)
                    results.append((student_id, "FAIL" if differences else "PASS", "\\n".join(differences)))
                else:
                    results.append((student_id, "ERROR", "Failed to generate UML data"))
            else:
                results.append((student_id, "ERROR", "Failed to extract JAR file"))
        except Exception as e:
            results.append((student_id, "ERROR", f"Processing failed: {str(e)}"))
        finally:
            shutil.rmtree(extract_dir, ignore_errors=True)
    
    results.sort(key=lambda x: x[0])
    
    score_file = os.path.join(output_dir, "uml-score.csv")
    comments_file = os.path.join(output_dir, "uml-comments.csv")
    
    with open(score_file, 'w', newline='') as sf, open(comments_file, 'w', newline='') as cf:
        csv.writer(sf).writerow(["ID", "Status"])
        csv.writer(cf).writerow(["ID", "Status", "Comments"])
        for result in tqdm(results, desc="Writing results"):
            csv.writer(sf).writerow(result[:2])
            csv.writer(cf).writerow(result)
    
    passed = sum(1 for r in results if r[1] == "PASS")
    failed = sum(1 for r in results if r[1] == "FAIL")
    errors = sum(1 for r in results if r[1] == "ERROR")
    
    print(f"Processing complete!")
    print(f"Results: {passed} PASS, {failed} FAIL, {errors} ERROR")
    return True

def main():
    if len(sys.argv) != 2:
        print("Usage: python grader.py <ref_dir>")
        sys.exit(1)
    
    ref_dir = sys.argv[1]
    
    print(f"Starting grading process with reference directory: {ref_dir}")
    
    if not generate_solution_json(ref_dir):
        sys.exit(1)
    
    if not process_submissions():
        sys.exit(1)

if __name__ == "__main__":
    main()
'''
    return grading_script

def safe_extract_filter(member, path):
    if member.isfile() or member.isdir():
        return member
    return None

def run_grading_container(java_image, input_dir, ref_dir, output_dir):
    print("Creating grading container...")
    
    try:
        client = docker.from_env()
    except Exception as e:
        print(f"ERROR: Failed to connect to Docker: {e}")
        sys.exit(1)
    
    try:
        container = client.containers.run(
            java_image,
            command="sleep infinity",
            detach=True,
            working_dir="/grader",
            name="structure-grader"
        )
    except docker.errors.APIError as e:
        if "Conflict" in str(e):
            print("Removing existing grading container...")
            try:
                old_container = client.containers.get("structure-grader")
                old_container.stop()
                old_container.remove()
                container = client.containers.run(
                    java_image,
                    command="sleep infinity",
                    detach=True,
                    working_dir="/grader",
                    name="structure-grader"
                )
            except Exception as cleanup_e:
                print(f"ERROR: Failed to cleanup and recreate container: {cleanup_e}")
                sys.exit(1)
        else:
            print(f"ERROR: Failed to create container: {e}")
            sys.exit(1)
    except Exception as e:
        print(f"ERROR: Failed to create container: {e}")
        sys.exit(1)
    
    try:
        print("Installing Python and dependencies in container...")
        install_exit, install_output = container.exec_run(
            "sh -c 'apt-get update -qq && apt-get install -y python3 python3-pip unzip -qq'"
        )
        if install_exit != 0:
            print(f"ERROR: Failed to install Python: {install_output.decode()}")
            sys.exit(1)
        
        pip_exit, pip_output = container.exec_run("python3 -m pip install tqdm --quiet --break-system-packages")
        if pip_exit != 0:
            print(f"ERROR: Failed to install tqdm: {pip_output.decode()}")
            sys.exit(1)
        
        print("Creating project archive...")
        try:
            archive_data = create_project_archive(input_dir, ref_dir)
        except Exception as e:
            print(f"ERROR: Failed to create project archive: {e}")
            sys.exit(1)
        
        print("Copying project files to container...")
        try:
            container.put_archive("/grader", archive_data)
        except Exception as e:
            print(f"ERROR: Failed to copy project files: {e}")
            sys.exit(1)
        
        print("Creating grading script...")
        grading_script = create_grading_script()
        script_archive = io.BytesIO()
        with tarfile.open(fileobj=script_archive, mode='w') as tar:
            script_info = tarfile.TarInfo(name='grader.py')
            script_info.size = len(grading_script.encode())
            script_info.mode = 0o755
            tar.addfile(script_info, io.BytesIO(grading_script.encode()))
        script_archive.seek(0)
        container.put_archive("/grader", script_archive.getvalue())
        
        print("Running grading process...")
        
        exec_id = container.client.api.exec_create(
            container.id, 
            f"python3 grader.py {ref_dir}",
            stdout=True,
            stderr=True
        )
        
        output_stream = container.client.api.exec_start(exec_id['Id'], stream=True)
        
        for chunk in output_stream:
            if chunk:
                try:
                    decoded_chunk = chunk.decode('utf-8', errors='replace')
                    print(decoded_chunk, end='', flush=True)
                except UnicodeDecodeError:
                    print(chunk.decode('latin-1', errors='replace'), end='', flush=True)
        
        exec_result = container.client.api.exec_inspect(exec_id['Id'])
        exit_code = exec_result['ExitCode']
        
        if exit_code != 0:
            print(f"\nERROR: Grading process failed with exit code {exit_code}")
            sys.exit(1)
        
        print("Copying results back...")
        try:
            stream, _ = container.get_archive("/grader/results")
            tar_data = b"".join(chunk for chunk in tqdm(stream, desc="Downloading results", unit="chunk"))
            
            with tarfile.open(fileobj=io.BytesIO(tar_data)) as tar:
                tar.extractall(path=".", filter=safe_extract_filter)
                
            if os.path.exists("results"):
                if output_dir != "results":
                    if os.path.exists(output_dir):
                        shutil.rmtree(output_dir)
                    shutil.move("results", output_dir)
                print(f"Results copied to {output_dir}/")
            else:
                print("ERROR: Results directory not found")
                sys.exit(1)
                
        except Exception as e:
            print(f"ERROR: Failed to copy results: {e}")
            sys.exit(1)
        
    finally:
        print("Cleaning up grading container...")
        try:
            container.stop()
            container.remove()
        except Exception as e:
            print(f"WARNING: Failed to cleanup container: {e}")

def main():
    parser = argparse.ArgumentParser(description="UML Structure Grader")
    parser.add_argument("--input_dir", required=True, help="Directory containing student JAR submissions")
    parser.add_argument("--java", default="openjdk:21-jdk-slim", help="Java Docker image to use")
    parser.add_argument("--output_dir", default="umls", help="Output directory for results")
    parser.add_argument("--ref_dir", default="solution", help="Reference/solution directory")
    
    args = parser.parse_args()
    
    if not os.path.isdir(args.input_dir):
        print(f"ERROR: Input directory '{args.input_dir}' does not exist")
        sys.exit(1)
    
    if not os.path.isdir(args.ref_dir):
        print(f"ERROR: Reference directory '{args.ref_dir}' does not exist")
        sys.exit(1)
    
    if not os.path.exists("files.txt"):
        print("ERROR: files.txt not found in current directory")
        sys.exit(1)
    
    if not check_jar_exists():
        print("Structure extractor JAR not found, building...")
        build_structure_extractor(args.java)
    else:
        print("Using existing structure-extractor.jar")
    
    run_grading_container(args.java, args.input_dir, args.ref_dir, args.output_dir)
    print("Grading complete!")

if __name__ == "__main__":
    main()