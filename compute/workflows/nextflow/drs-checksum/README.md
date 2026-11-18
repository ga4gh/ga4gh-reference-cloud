# drs-checksum

The refcloud's bundled demo workflow (Nextflow). It takes one file and writes `checksum-report.json` with the file's name, size, MD5 and SHA-256:

```json
{
  "name": "HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai",
  "size": 6842584,
  "md5": "a8a1f1ba420f7d75c7955b04b5972c54",
  "sha256": "89d6677f9e8d54fd3d771177ab352fb5d7435cf4ad1fb5dec33e5a508612f5c0"
}
```

| | |
|-|-|
| Parameters | `input`: the file to checksum. Through WES, a `drs://` URI. `outdir` is set by the refcloud. |
| Output | `checksum-report.json`, published to `outdir` |
| Container | `ubuntu:24.04` |
| WES `workflow_url` | `refcloud:bundled/drs-checksum` (`workflow_type` `NFL`, `workflow_type_version` `DSL2`) |

An admin offers it on a compute platform with the admin CLI (`apps/admin-cli`):

```bash
uv run refcloud compute-platform allowlist add -p <platform-id> --bundled drs-checksum \
  --name "DRS checksum" --description "Report a DRS object's name, size, MD5 and SHA-256"
```

## Demo input

The 1000 Genomes BAM index for sample HG00096, onboarded by `./scripts/refcloud-setup.sh`. It's small (6.8 MB):

```
drs://refcloud.ga4gh.localhost/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai
```

The 1000 Genomes dataset is visa-gated, so the researcher needs **two** approved visas: the dataset's (`ga4gh_visa:aws_opendata_registry_1000_genomes`) and the compute platform's (`ga4gh_visa:compute_platform:<platform-id>`).

The report's `md5` matches the checksum DRS reports for the object (`type` `etag`): for this file, S3's ETag is its MD5.

## Run it through WES

With a researcher's passport token in `$TOKEN`:

```bash
WES=https://refcloud.ga4gh.localhost/ga4gh/wes/<platform-id>/v1

# submit; returns {"run_id": "..."}
curl -s -H "Authorization: Bearer $TOKEN" \
  -F workflow_url=refcloud:bundled/drs-checksum \
  -F workflow_type=NFL \
  -F workflow_type_version=DSL2 \
  -F 'workflow_params={"input": "drs://refcloud.ga4gh.localhost/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai"}' \
  $WES/runs

# QUEUED while the input downloads, then RUNNING and COMPLETE
curl -s -H "Authorization: Bearer $TOKEN" $WES/runs/<run_id>/status

# the run log; outputs.files[].url is each output's download link
curl -s -H "Authorization: Bearer $TOKEN" $WES/runs/<run_id>
curl -s -H "Authorization: Bearer $TOKEN" -o checksum-report.json $WES/runs/<run_id>/outputs/checksum-report.json
```

## Run it on its own

Outside the refcloud, with Nextflow and Docker installed:

```bash
nextflow run compute/workflows/nextflow/drs-checksum --input <file> --outdir results -with-docker
```
