INSERT INTO dataset (id, name, description) VALUES
    ('ds1', '1000 Genomes Project Phase 3', 'Whole-genome sequencing data from 2,504 individuals across 26 global populations, serving as a comprehensive catalog of human genetic variation.'),
    ('ds2', 'The Cancer Genome Atlas (TCGA) - Breast Invasive Carcinoma', 'Multi-platform genomic profiling data, including mRNA expression, miRNA, copy number variations, and clinical data for breast cancer cohorts.'),
    ('ds3', 'gnomAD (Genome Aggregation Database) v4.1', 'Aggregated exome and genome sequencing data from over 800,000 diverse individuals, designed to assist researchers in filtering out common variants.'),
    ('ds4', 'ENCODE Registry of Candidate Cis-Regulatory Elements', 'A vast collection of functional genomic data identifying promoters, enhancers, and transcription factor binding sites across the human genome.'),
    ('ds5', 'GTEx (Genotype-Tissue Expression) v8', 'RNA sequencing and genotyping data from dozens of human tissue types to study the relationship between genetic variation and gene expression.'),
    ('ds6', 'UK Biobank WES 450K Dataset', 'Whole-exome sequencing data coupled with detailed health and lifestyle records for 450,000 participants.'),
    ('ds7', 'Mouse Genome Informatics (MGI) Strain Database', 'Reference genomic, phenotypic, and strain-specific mutation data for standard laboratory mouse models.'),
    ('ds8', 'ClinVar Human Variation Database', 'A freely accessible, public archive of reports of the relationships among human variations and phenotypes, with supporting evidence.'),
    ('ds9', 'SARS-CoV-2 Genomic Surveillance Sequences', 'A curated dataset of viral whole-genome sequences collected globally to monitor mutations and transmission dynamics over time.'),
    ('ds10', 'Single-Cell RNA-Seq of Human PBMC (10x Genomics)', 'Transcriptomic profiling of 10,000 peripheral blood mononuclear cells (PBMCs), ideal for testing and benchmarking single-cell analysis pipelines.'),
    ('ds11', 'HmtVar Human Mitochondrial Genome Database', 'Comprehensive database focused on human mitochondrial DNA variations, including disease annotations and mutation frequencies.'),
    ('ds12', '100K Pathogen Genomes Project', 'High-quality draft genome sequences for 100,000 foodborne pathogens (e.g., Salmonella, E. coli) for comparative genomics and epidemiology.');

INSERT INTO tag(id, tag) VALUES
    (1, 'Variant Calling'),
    (2, 'Whole Genome'),
    (3, 'Transcriptomics'),
    (4, 'Cancer Genomics'),
    (5, 'Allele Frequency'),
    (6, 'Reference Data'),
    (7, 'Epigenomics'),
    (8, 'Regulatory Elements'),
    (9, 'Gene Expression'),
    (10, 'eQTL'),
    (11, 'Whole Exome'),
    (12, 'Phenotype Association'),
    (13, 'Model Organism'),
    (14, 'Genotype'),
    (15, 'Clinical Genetics'),
    (16, 'Variant Interpretation'),
    (17, 'Viral Genomics'),
    (18, 'Pathogen'),
    (19, 'Single-cell RNA'),
    (20, 'Single-cell Transcriptomics'),
    (21, 'Mitochondrial DNA'),
    (22, 'Disease'),
    (23, 'Microbial Genomics'),
    (24, 'Epidemiology');

INSERT INTO dataset_tag (dataset_id, tag_id) VALUES
    ('ds1', 1),
    ('ds1', 2),
    ('ds2', 3),
    ('ds2', 4),
    ('ds3', 5),
    ('ds3', 6),
    ('ds4', 7),
    ('ds4', 8),
    ('ds5', 9),
    ('ds5', 10),
    ('ds6', 11),
    ('ds6', 12),
    ('ds7', 13),
    ('ds7', 14),
    ('ds8', 15),
    ('ds8', 16),
    ('ds9', 17),
    ('ds9', 18),
    ('ds10', 19),
    ('ds10', 20),
    ('ds11', 21),
    ('ds11', 22),
    ('ds12', 23),
    ('ds12', 24);

INSERT INTO passport_visa (id, name, description, dataset_id) VALUES
    ('visa.ds.1', 'Visa: 1000 Genomes Project Phase 3', 'Visa for dataset: 1000 Genomes Project Phase 3', 'ds1'),
    ('visa.ds.2', 'Visa: The Cancer Genome Atlas (TCGA) - Breast Invasive Carcinoma', 'Visa for dataset: The Cancer Genome Atlas (TCGA) - Breast Invasive Carcinoma', 'ds2'),
    ('visa.ds.3', 'Visa: gnomAD (Genome Aggregation Database) v4.1', 'Visa for dataset: gnomAD (Genome Aggregation Database) v4.1', 'ds3'),
    ('visa.ds.4', 'Visa: ENCODE Registry of Candidate Cis-Regulatory Elements', 'Visa for dataset: ENCODE Registry of Candidate Cis-Regulatory Elements', 'ds4'),
    ('visa.ds.5', 'Visa: GTEx (Genotype-Tissue Expression) v8', 'Visa for dataset: GTEx (Genotype-Tissue Expression) v8', 'ds5'),
    ('visa.ds.6', 'Visa: UK Biobank WES 450K Dataset', 'Visa for dataset: UK Biobank WES 450K Dataset', 'ds6'),
    ('visa.ds.7', 'Visa: Mouse Genome Informatics (MGI) Strain Database', 'Visa for dataset: Mouse Genome Informatics (MGI) Strain Database', 'ds7'),
    ('visa.ds.8', 'Visa: ClinVar Human Variation Database', 'Visa for dataset: ClinVar Human Variation Database', 'ds8'),
    ('visa.ds.9', 'Visa: SARS-CoV-2 Genomic Surveillance Sequences', 'Visa for dataset: SARS-CoV-2 Genomic Surveillance Sequences', 'ds9'),
    ('visa.ds.10', 'Visa: Single-Cell RNA-Seq of Human PBMC (10x Genomics)', 'Visa for dataset: Single-Cell RNA-Seq of Human PBMC (10x Genomics)', 'ds10'),
    ('visa.ds.11', 'Visa: HmtVar Human Mitochondrial Genome Database', 'Visa for dataset: HmtVar Human Mitochondrial Genome Database', 'ds11'),
    ('visa.ds.12', 'Visa: 100K Pathogen Genomes Project', 'Visa for dataset: 100K Pathogen Genomes Project', 'ds12');


INSERT INTO drs_object (id, description, created_time, mime_type, name, size, updated_time, version, dataset_id) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', 'HG00096 whole-exome bam file', '2015-05-13 03:30:44', 'application/octet-stream', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', 9196950908, '2015-05-13 03:30:44', 'v1', 'ds1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', 'HG00096 whole-exome bai file', '2015-05-13 03:12:41', 'application/octet-stream', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', 6842584, '2015-05-13 03:12:41', 'v1', 'ds1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 'HG00096 whole-exome bas file', '2015-05-13 03:29:43', 'application/octet-stream', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 827, '2015-05-13 03:29:43', 'v1', 'ds1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', 'HG00096 whole-exome cram file', '2015-05-13 03:13:58', 'application/octet-stream', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', 2304099249, '2015-05-13 03:13:58', 'v1', 'ds1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', 'HG00096 whole-exome crai file', '2015-05-13 03:27:40', 'application/octet-stream', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', 178859, '2015-05-13 03:27:40', 'v1', 'ds1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', 'HG00096 whole-exome csra file', '2015-05-13 03:14:58', 'application/octet-stream', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', 2282395745, '2015-05-13 03:14:58', 'v1', 'ds1');

INSERT INTO drs_object_alias (drs_object_id, alias) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', 'HG00096 whole-exome bam file'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', 'HG00096 whole-exome bai file'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 'HG00096 whole-exome bas file'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', 'HG00096 whole-exome cram file'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', 'HG00096 whole-exome crai file'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', 'HG00096 whole-exome csra file');

INSERT INTO drs_object_checksum(drs_object_id, checksum, type) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', '5d4ae7a46d470036d99429c363498965', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', '72d9fc6f08feb87b5e9666eb6bee98bd00b0d024', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', 'e2062842263d1ca42ce4368e61850b75a58f34cd9b6347c465ea95e3da31d943', 'sha256'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', 'a8a1f1ba420f7d75c7955b04b5972c54', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', '56e7a1d55713e74eccf220365bbeec69ced899ad', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', '89d6677f9e8d54fd3d771177ab352fb5d7435cf4ad1fb5dec33e5a508612f5c0', 'sha256'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 'e2ebae06af6ce9c92750339e9a85e5d9', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 'a3327258093db7eea317b41dff596a032e7e27cf', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 'fb028f0d383e9cc8e987c287a263191c9f910963dc4ad0c68094d569ba714033', 'sha256'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', '46d0f8f93809c608571d82c327bb8bfc', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', 'bbc73bcee7a1e837d5541db1857611b75288a53f', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', '5b8da495309e4b1a2fa229557a2f4ccb7be9347b75fac6c37bc845cb0dcf0784', 'sha256'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', '83ef6855b01965759b9c3c9c7e6586a8', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', 'b7d52204789cc5e098a6be807083165a8a34f4f6', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', '722260f9c8757ed603cfb1bf7441e076371028c05e3f89a81930c895254837ee', 'sha256'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', '35e12569cd5dacb4d158832bedfb8b1b', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', '8f1f9634ab8118717471420a5703fb10ee5383b6', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', 'd5b7a372c4c0187590b3a73b4fd79a4913ad1900305694cb2a33051e63684e89', 'sha256');

INSERT INTO aws_s3_access_object(drs_object_id, region, bucket, key) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam', 'us-east-1', '1000genomes', '/phase3/data/HG00096/exome_alignment/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai', 'us-east-1', '1000genomes', '/phase3/data/HG00096/exome_alignment/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas', 'us-east-1', '1000genomes', '/phase3/data/HG00096/exome_alignment/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram', 'us-east-1', '1000genomes', '/phase3/data/HG00096/exome_alignment/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai', 'us-east-1', '1000genomes', '/phase3/data/HG00096/exome_alignment/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra', 'us-east-1', '1000genomes', '/phase3/data/HG00096/exome_alignment/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra');

/* DRS Object Manifest */
INSERT INTO drs_object (id, description, created_time, mime_type, name, size, updated_time, version, dataset_id, is_manifest, manifest_content) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.MANIFEST', 'HG00096 whole-exome - compound object manifest', '2015-05-13 03:30:44', 'application/json', 'HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.MANIFEST', 416, '2015-05-13 03:30:44', 'v1', 'ds1', true, '{"bam_file":"HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam","bai_file":"HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai","bas_file":"HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bas","cram_file":"HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram","crai_file":"HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.cram.crai","csra_file":"HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.csra"}');

INSERT INTO drs_object_alias (drs_object_id, alias) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.MANIFEST', 'HG00096 whole-exome - compound object manifest');

INSERT INTO drs_object_checksum(drs_object_id, checksum, type) VALUES
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.MANIFEST', 'f257d781e1d4017d1a851b61acc7e93c', 'md5'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.MANIFEST', 'f9db5b1a3332972dbe499399543f210b88dbe55c', 'sha1'),
    ('HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.MANIFEST', 'e5a3e248d68a035d23e2ed336c93aaf78cd46aceca7cd814e58689896f97b33c', 'sha256');