// drs-checksum: the refcloud's bundled demo workflow (claude/specs/active/0008-add-compute-wes-tes.md, Task 9).
// takes one file and writes checksum-report.json with its name, size, MD5 and SHA-256. through WES, the input is a
// drs:// URI in workflow_params.input, which the refcloud downloads and replaces with the local file before the run

params.input = null
params.outdir = 'results'

process CHECKSUM {
    // task images need bash (nextflow's task wrapper); ubuntu also has GNU stat, md5sum and sha256sum
    container 'ubuntu:24.04'
    publishDir params.outdir, mode: 'copy'

    input:
    // the file is staged under a fixed name, so the script never has to quote an arbitrary file name; the original
    // name goes into the report as an already-escaped JSON string
    tuple val(name), path('input')

    output:
    path 'checksum-report.json'

    script:
    """
    cat > name.json <<'REFCLOUD_NAME'
    ${groovy.json.JsonOutput.toJson(name)}
    REFCLOUD_NAME
    size=\$(stat -L -c %s input)
    md5=\$(md5sum input | cut -d ' ' -f 1)
    sha256=\$(sha256sum input | cut -d ' ' -f 1)
    printf '{\\n  "name": %s,\\n  "size": %s,\\n  "md5": "%s",\\n  "sha256": "%s"\\n}\\n' \\
        "\$(cat name.json)" "\$size" "\$md5" "\$sha256" > checksum-report.json
    """
}

workflow {
    if (!params.input) {
        error("drs-checksum needs one input file: --input <path> (through WES, a drs:// URI in workflow_params.input)")
    }
    channel.fromPath(params.input, checkIfExists: true)
        .map { file -> tuple(file.name, file) }
        | CHECKSUM
}
