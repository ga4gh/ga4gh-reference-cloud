import fnmatch
import os
import time
import json
import hashlib


REGION = "us-east-1"
BUCKET_NAME = "1000genomes"
STARTING_PREFIX = "phase3/data"
GLOB_PATTERN = "*.mapped.*.bam*"
THROTTLE_TIME_SECS = 0.0


def onboard_1000_genomes(s3_client):
    token = None
    page_number = 1
    subject_folder_count = 0

    while True:
        print(f"\n--- Fetching Page {page_number} ---")
        
        # Get one page of folders (limiting to 5 folders per page for demonstration)
        folders, token = get_subfolders_page(s3_client, BUCKET_NAME, STARTING_PREFIX, continuation_token=token, max_keys=5)
        
        for subject_folder in folders:
            subject_folder_count += 1
            exome_directory = subject_folder + "exome_alignment/"
            yield process_subject_level_exome_directory(s3_client, exome_directory)
            
        # If NextContinuationToken is missing, we reached the end
        if not token:
            print("\nFinished listing all subdirectories.")
            break
            
        # Pause or wait for user input to mimic manual pagination
        print(f"\n{subject_folder_count} subject folders complete. next page...")
        page_number += 1


def get_subfolders_page(s3_client, bucket_name, directory_prefix, continuation_token=None, max_keys=500):
    """
    Fetches a single page of subdirectories.
    Returns a tuple: (list_of_subfolders, next_continuation_token)
    """
    if not directory_prefix.endswith('/'):
        directory_prefix += '/'

    kwargs = {
        'Bucket': bucket_name,
        'Prefix': directory_prefix,
        'Delimiter': '/',
        'MaxKeys': max_keys
    }

    if continuation_token:
        kwargs['ContinuationToken'] = continuation_token

    response = s3_client.list_objects_v2(**kwargs)

    subfolders = []
    if 'CommonPrefixes' in response:
        for sub_dir in response['CommonPrefixes']:
            subfolders.append(sub_dir['Prefix'])

    next_token = response.get('NextContinuationToken')

    return subfolders, next_token


def process_subject_level_exome_directory(s3_client, exome_directory):
    drs_object_set = {
        "manifest_obj": {
            "drs_object": {
                "id": None,
                "description": None,
                "created_time": None,
                "mime_type": "application/octet-stream",
                "name": None,
                "size": None,
                "updated_time": None,
                "version": "v1",
                "is_manifest": True,
                "manifest_content": ""
            },
            "aliases": [],
            "checksums": [],
            "aws_s3_access_objects": []
        },
        "file_keys": [],
        "file_objs": {}
    }

    kwargs = {
        "Bucket": BUCKET_NAME,
        "Prefix": exome_directory,
        "Delimiter": "/"
    }

    response = s3_client.list_objects_v2(**kwargs)
    tc = 0 # total file count
    mc = 0 # count of files that match the glob pattern
    if 'Contents' in response:
        for obj in response['Contents']:
            tc += 1
            file_name = os.path.basename(obj['Key'])
            if fnmatch.fnmatch(file_name, GLOB_PATTERN):
                mc += 1

                # reusable metadata components
                subject = file_name.split(".")[0]
                suffix = obj['Key'].split(".")[-1]
                file_key = suffix + "_file"

                # capture all metadata required for DRS
                drs_object_metadata = {
                    "drs_object": {
                        "id": file_name,
                        "description": f"{subject} whole-exome {suffix} file",
                        "created_time": obj['LastModified'].strftime("%Y-%m-%d %H:%M:%S"),
                        "mime_type": "application/octet-stream",
                        "name": file_name,
                        "size": obj['Size'],
                        "updated_time": obj['LastModified'].strftime("%Y-%m-%d %H:%M:%S"),
                        "version": "v1"
                    },
                    "aliases": [
                        f"{subject} whole-exome {suffix} file"
                    ],
                    "checksums": [
                        {
                            "checksum": obj['ETag'].replace('"', ''),
                            "type": "etag"
                        }
                    ],
                    "aws_s3_access_objects": [
                        {
                            "region": REGION,
                            "bucket": BUCKET_NAME,
                            "key": f"/{obj['Key']}"
                        }
                    ]
                }

                # assign file according to file type so it can be tracked in the manifest 
                drs_object_set["file_keys"].append(file_key)
                drs_object_set["file_objs"][file_key] = drs_object_metadata

                if suffix == "bam":
                    drs_object_set["manifest_obj"]["drs_object"]["id"] = f"{file_name}.manifest",
                    drs_object_set["manifest_obj"]["drs_object"]["description"] = f"{subject} whole-exome file manifest",
                    drs_object_set["manifest_obj"]["drs_object"]["created_time"] = obj['LastModified'].strftime("%Y-%m-%d %H:%M:%S")
                    drs_object_set["manifest_obj"]["drs_object"]["name"] = f"{file_name}.manifest",
                    drs_object_set["manifest_obj"]["drs_object"]["updated_time"] = obj['LastModified'].strftime("%Y-%m-%d %H:%M:%S")
                    drs_object_set["manifest_obj"]["aliases"].append(f"{subject} whole-exome file manifest")

    # finalize manifest metadata
    manifest_content_obj = {key: drs_object_set["file_objs"][key]["drs_object"]["id"] for key in drs_object_set["file_keys"]}
    manifest_content_str = json.dumps(manifest_content_obj)
    manifest_size = len(manifest_content_str.encode('utf-8'))
    manifest_md5 = hashlib.md5(manifest_content_str.encode('utf-8')).hexdigest()

    drs_object_set["manifest_obj"]["drs_object"]["manifest_content"] = manifest_content_obj
    drs_object_set["manifest_obj"]["drs_object"]["size"] = manifest_size
    drs_object_set["manifest_obj"]["checksums"].append({"checksum": manifest_md5, "type": "md5"})

    print(f" {exome_directory} total files: {tc} matched files: {mc}")
    time.sleep(THROTTLE_TIME_SECS) # throttle requests so client doesn't get blocked by S3

    return drs_object_set
