#!/usr/bin/env python3

import docker
import tarfile
import io
import os
import shutil
import sys
import argparse
from pathlib import Path
from tqdm import tqdm

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

def main():
    parser = argparse.ArgumentParser(description="Build Java structure extractor using Docker")
    parser.add_argument("--java", default="openjdk:21-jdk-slim", help="Java Docker image to use (default: openjdk:21-jdk-slim)")
    args = parser.parse_args()
    
    try:
        client = docker.from_env()
    except Exception as e:
        print(f"ERROR: Failed to connect to Docker: {e}")
        sys.exit(1)
    
    print(f"Pulling Java image: {args.java}")
    try:
        image = client.images.pull(args.java)
    except docker.errors.ImageNotFound:
        print(f"ERROR: Image '{args.java}' not found")
        sys.exit(1)
    except docker.errors.APIError as e:
        print(f"ERROR: Failed to pull image '{args.java}': {e}")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: Failed to pull Java image: {e}")
        sys.exit(1)
    
    print("Creating container...")
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
            print("ERROR: Container 'structure-extractor-builder' already exists")
            print("Remove it with: docker rm -f structure-extractor-builder")
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
        print("Cleaning up container 'structure-extractor-builder'...")
        try:
            container.stop()
            container.remove()
        except Exception as e:
            print(f"WARNING: Failed to cleanup container: {e}")
    
    jar_size = os.path.getsize('structure-extractor.jar')
    print(f"SUCCESS: Built structure-extractor.jar ({jar_size:,} bytes)")

if __name__ == "__main__":
    main()