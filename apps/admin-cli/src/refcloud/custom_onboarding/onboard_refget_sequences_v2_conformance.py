import base64
import datetime
import fnmatch
import os
import time
import json
import hashlib

REGION = "us-east-1"
BUCKET_NAME = "ga4gh-demo-data"
STARTING_PREFIX = "refget/v2.0.0/conformance/"
GLOB_PATTERN = "*.faa"

REFGET_METADATA = {
    "test_seq_1": {
        "sequence": {
            "is_circular": True,
        },
        "aliases": [
            {
                "naming_authority": "INSDC",
                "alias": "CH003448.1"
            },
            {
                "naming_authority": "UCSC",
                "alias": "chr1"
            }
        ]
    },
    "test_seq_2": {
        "sequence": {
            "is_circular": False,
        },
    },
    "test_seq_3": {
        "sequence": {
            "is_circular": False,
        },
    },
}

def onboard_refget_sequences_v2_conformance(s3_client):
    paginator = s3_client.get_paginator('list_objects_v2')
    page_iterator = paginator.paginate(Bucket=BUCKET_NAME, Prefix=STARTING_PREFIX)
    
    all_objs = [obj for page in page_iterator if 'Contents' in page for obj in page['Contents']]

    # output object
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

    for obj in all_objs:
        if fnmatch.fnmatch(obj['Key'], GLOB_PATTERN):
            file_name = os.path.basename(obj['Key'])
            test_seq_number = file_name.split(".")[0].split("-")[-1]
            file_key = "test_seq_" + test_seq_number

            # generate checksums
            response = s3_client.get_object(Bucket=BUCKET_NAME, Key=obj['Key'])
            s3_file_stream = response['Body']
            md5_hasher = hashlib.md5()
            sha512_hasher = hashlib.sha512()
            for chunk in iter(lambda: s3_file_stream.read(65536), b''):
                md5_hasher.update(chunk)
                sha512_hasher.update(chunk)

            md5_final = md5_hasher.hexdigest()

            sha512_hexdigest = sha512_hasher.hexdigest()
            trunc512_final = sha512_hexdigest[:48]

            sha512_rawbytes = bytes.fromhex(sha512_hexdigest)
            sha512t24u_final = base64.urlsafe_b64encode(sha512_rawbytes[:24]).decode('utf-8').rstrip('=')

            drs_object_metadata = {
                "drs_object": {
                    "id": file_name,
                    "description": f"Reference sequence for Refget Sequences v2 Conformance",
                    "created_time": obj['LastModified'].strftime("%Y-%m-%d %H:%M:%S"),
                    "mime_type": "application/octet-stream",
                    "name": file_name,
                    "size": obj['Size'],
                    "updated_time": obj['LastModified'].strftime("%Y-%m-%d %H:%M:%S"),
                    "version": "v1"
                },
                "aliases": [],
                "checksums": [
                    {
                        "checksum": md5_final,
                        "type": "md5"
                    },
                    {
                        "checksum": trunc512_final,
                        "type": "trunc512"
                    },
                    {
                        "checksum": sha512t24u_final,
                        "type": "sha512t24u"
                    }

                ],
                "aws_s3_access_objects": [
                    {
                        "region": REGION,
                        "bucket": BUCKET_NAME,
                        "key": f"/{obj['Key']}"
                    }
                ],
                "refget_sequence": REFGET_METADATA[file_key]["sequence"],
                "refget_sequence_aliases": REFGET_METADATA[file_key].get("aliases", [])
            }

            drs_object_set["file_keys"].append(file_key)
            drs_object_set["file_objs"][file_key] = drs_object_metadata

    # finalize manifest metadata
    manifest_content_obj = {key: drs_object_set["file_objs"][key]["drs_object"]["id"] for key in drs_object_set["file_keys"]}
    manifest_content_str = json.dumps(manifest_content_obj)
    manifest_size = len(manifest_content_str.encode('utf-8'))
    manifest_md5 = hashlib.md5(manifest_content_str.encode('utf-8')).hexdigest()

    drs_object_set["manifest_obj"]["drs_object"]["id"] = f"GA4GH-RefgetSequences-V2-ConformanceTestSequences.manifest"
    drs_object_set["manifest_obj"]["drs_object"]["description"] = f"File manifest for Refget Sequences v2 Conformance Test Sequences"
    drs_object_set["manifest_obj"]["drs_object"]["created_time"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    drs_object_set["manifest_obj"]["drs_object"]["name"] = f"GA4GH-RefgetSequences-V2-ConformanceTestSequences.manifest"
    drs_object_set["manifest_obj"]["drs_object"]["updated_time"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    drs_object_set["manifest_obj"]["aliases"].append(f"File manifest for Refget Sequences v2 Conformance Test Sequences")

    drs_object_set["manifest_obj"]["drs_object"]["manifest_content"] = manifest_content_obj
    drs_object_set["manifest_obj"]["drs_object"]["size"] = manifest_size
    drs_object_set["manifest_obj"]["checksums"].append({"checksum": manifest_md5, "type": "md5"})

    return [drs_object_set]
