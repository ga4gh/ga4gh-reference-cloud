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
    ('1', 'Variant Calling'),
    ('2', 'Whole Genome'),
    ('3', 'Transcriptomics'),
    ('4', 'Cancer Genomics'),
    ('5', 'Allele Frequency'),
    ('6', 'Reference Data'),
    ('7', 'Epigenomics'),
    ('8', 'Regulatory Elements'),
    ('9', 'Gene Expression'),
    ('10', 'eQTL'),
    ('11', 'Whole Exome'),
    ('12', 'Phenotype Association'),
    ('13', 'Model Organism'),
    ('14', 'Genotype'),
    ('15', 'Clinical Genetics'),
    ('16', 'Variant Interpretation'),
    ('17', 'Viral Genomics'),
    ('18', 'Pathogen'),
    ('19', 'Single-cell RNA'),
    ('20', 'Single-cell Transcriptomics'),
    ('21', 'Mitochondrial DNA'),
    ('22', 'Disease'),
    ('23', 'Microbial Genomics'),
    ('24', 'Epidemiology');

INSERT INTO dataset_tag (dataset_id, tag_id) VALUES
    ('ds1', '1'),
    ('ds1', '2'),
    ('ds2', '3'),
    ('ds2', '4'),
    ('ds3', '5'),
    ('ds3', '6'),
    ('ds4', '7'),
    ('ds4', '8'),
    ('ds5', '9'),
    ('ds5', '10'),
    ('ds6', '11'),
    ('ds6', '12'),
    ('ds7', '13'),
    ('ds7', '14'),
    ('ds8', '15'),
    ('ds8', '16'),
    ('ds9', '17'),
    ('ds9', '18'),
    ('ds10', '19'),
    ('ds10', '20'),
    ('ds11', '21'),
    ('ds11', '22'),
    ('ds12', '23'),
    ('ds12', '24');

INSERT INTO drs_object (id, description, created_time, mime_type, name, size, updated_time, version, dataset_id) VALUES
    ('drs.id.0', '1000 Genomes Phase3 WGS alignment BAM: HG00096 chr11', '2015-05-13 03:10:08', 'application/x-bam', 'HG00096.chrom11.ILLUMINA.bwa.GBR.low_coverage.20120522.bam', 692760649, '2015-05-13 03:10:08', 'v1', 'ds1');

INSERT INTO drs_object_alias (drs_object_id, alias) VALUES
    ('drs.id.0', 'HG00096 chr11 BAM'),
    ('drs.id.0', 'HG00096 chr11 BAM file');

INSERT INTO drs_object_checksum(drs_object_id, checksum, type) VALUES
    ('drs.id.0', 'e2425c6f57b2aa4ddb08f472d98221d0', 'md5'),
    ('drs.id.0', '9dddead4e1b13471784e536824ffed3c6137126a', 'sha1'),
    ('drs.id.0', '718f74b48fd739c9305bbf6c3d4b29ef3c9d62fcb1c16eaae61dbfd0c5db60d5', 'sha256');

INSERT INTO aws_s3_access_object(drs_object_id, region, bucket, key) VALUES
    ('drs.id.0', 'us-east-1', '1000genomes', '/phase3/data/HG00096/alignment/HG00096.chrom11.ILLUMINA.bwa.GBR.low_coverage.20120522.bam');