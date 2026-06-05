import type { NextPage } from 'next'
import AppLayout from 'components/layout/AppLayout';
import DatasetCard from 'components/datasets/DatasetCard';

const Datasets: NextPage = () => {

  const datasets = [
    {
      title: "1000 Genomes Project Phase 3",
      description: "Whole-genome sequencing data from 2,504 individuals across 26 global populations, serving as a comprehensive catalog of human genetic variation.",
      tags: ["Variant Calling", "Whole Genome"],
      apis: ["DRS", "Beacon"]
    },
    {
      title: "The Cancer Genome Atlas (TCGA) - Breast Invasive Carcinoma",
      description: "Multi-platform genomic profiling data, including mRNA expression, miRNA, copy number variations, and clinical data for breast cancer cohorts.",
      tags: ["Transcriptomics", "Cancer Genomics"],
      apis: ["DRS", "htsget", "Beacon"]
    },
    {
      title: "gnomAD (Genome Aggregation Database) v4.1",
      description: "Aggregated exome and genome sequencing data from over 800,000 diverse individuals, designed to assist researchers in filtering out common variants.",
      tags: ["Allele Frequency", "Reference Data"],
      apis: ["DRS", "htsget"]
    },
    {
      title: "ENCODE Registry of Candidate Cis-Regulatory Elements",
      description: "A vast collection of functional genomic data identifying promoters, enhancers, and transcription factor binding sites across the human genome.",
      tags: ["Epigenomics", "Regulatory Elements"],
      apis: ["DRS", "Beacon"]
    },
    {
      title: "GTEx (Genotype-Tissue Expression) v8",
      description: "RNA sequencing and genotyping data from dozens of human tissue types to study the relationship between genetic variation and gene expression.",
      tags: ["Gene Expression", "eQTL"],
      apis: ["DRS", "htsget"]
    },
    {
      title: "UK Biobank WES 450K Dataset",
      description: "Whole-exome sequencing data coupled with detailed health and lifestyle records for 450,000 participants.",
      tags: ["Whole Exome", "Phenotype Association"],
      apis: ["DRS", "htsget", "Beacon"]
    },
    {
      title: "Mouse Genome Informatics (MGI) Strain Database",
      description: "Reference genomic, phenotypic, and strain-specific mutation data for standard laboratory mouse models.",
      tags: ["Model Organism", "Genotype"],
      apis: ["DRS", "htsget"]
    },
    {
      title: "ClinVar Human Variation Database",
      description: "A freely accessible, public archive of reports of the relationships among human variations and phenotypes, with supporting evidence.",
      tags: ["Clinical Genetics", "Variant Interpretation"],
      apis: ["DRS", "Beacon"]
    },
    {
      title: "SARS-CoV-2 Genomic Surveillance Sequences",
      description: "A curated dataset of viral whole-genome sequences collected globally to monitor mutations and transmission dynamics over time.",
      tags: ["Viral Genomics", "Pathogen"],
      apis: ["DRS", "htsget"]
    },
    {
      title: "Single-Cell RNA-Seq of Human PBMC (10x Genomics)",
      description: "Transcriptomic profiling of 10,000 peripheral blood mononuclear cells (PBMCs), ideal for testing and benchmarking single-cell analysis pipelines.",
      tags: ["Single-cell RNA", "Transcriptomics"],
      apis: ["DRS", "htsget", "Beacon"]
    },
    {
      title: "HmtVar Human Mitochondrial Genome Database",
      description: "Comprehensive database focused on human mitochondrial DNA variations, including disease annotations and mutation frequencies.",
      tags: ["Mitochondrial DNA", "Disease"],
      apis: ["DRS", "Beacon"]
    },
    {
      title: "100K Pathogen Genomes Project",
      description: "High-quality draft genome sequences for 100,000 foodborne pathogens (e.g., Salmonella, E. coli) for comparative genomics and epidemiology.",
      tags: ["Microbial Genomics", "Epidemiology"],
      apis: ["DRS", "htsget"]
    }
  ]

  return (
    <>
      <AppLayout>
        <h1 className="mb-5 text-5xl font-bold">Browse Datasets</h1>
        <h2 className="mb-4 text-2xl">Explore and request access to the datasets available on the platform</h2>
        <div className="flex flex-wrap m-16 gap-8">
          <input type="search" placeholder="Search..." className="input input-bordered w-full max-w-xs" />
        </div>
        <div className="flex flex-wrap gap-8">
          {datasets.map((dataset, index) => (
            <DatasetCard
              key={index}
              title={dataset.title}
              description={dataset.description}
              tags={dataset.tags}
              apis={dataset.apis}
            />
          ))}
        </div>
      </AppLayout>
    </>
  )
}

export default Datasets