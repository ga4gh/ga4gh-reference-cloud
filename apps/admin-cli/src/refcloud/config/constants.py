from refcloud.custom_onboarding.onboard_1000_genomes import onboard_1000_genomes
from refcloud.custom_onboarding.onboard_refget_sequences_v2_conformance import onboard_refget_sequences_v2_conformance

SUPPORTED_DATASETS = {
    "aws.opendata.registry.1000-genomes": {
        "dataset": {
            "id": "aws.opendata.registry.1000-genomes",
            "name": "1000 Genomes",
            "description": "The 1000 Genomes Project is an international collaboration which has established the most detailed catalogue of human genetic variation, including SNPs, structural variants, and their haplotype context. The final phase of the project sequenced more than 2500 individuals from 26 different populations around the world and produced an integrated set of phased haplotypes with more than 80 million variants for these individuals."
        },
        "visa": {
            "id": "ga4gh-visa:aws.opendata.registry.1000-genomes",
            "name": "GA4GH Visa: 1000 Genomes",
            "description": "GA4GH Visa required to access data from the 1000 Genomes dataset",
        },
        "tags": [
            "Variant Calling",
            "Whole Genome"
        ],
        "data_source": {
            "type": "s3",
            "s3info": {
                "custom_onboarding_function": onboard_1000_genomes
            }
        }
    },
    "ga4gh.refget.sequences.v2.conformance": {
        "dataset": {
            "id": "ga4gh.refget.sequences.v2.conformance",
            "name": "GA4GH Refget Sequences v2 Conformance Test Dataset",
            "description": "Test sequences for GA4GH Refget Sequences v2 Conformance"
        },
        "visa": None, # conformance data that will not require a researcher passport, hence no visa is required to access it
        "tags": [
            "Reference Sequences",
            "Conformance"
        ],
        "data_source": {
            "type": "s3",
            "s3info": {
                "custom_onboarding_function": onboard_refget_sequences_v2_conformance
            }
        }
    }
}
