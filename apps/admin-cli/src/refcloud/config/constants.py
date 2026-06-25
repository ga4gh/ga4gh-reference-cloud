from refcloud.custom_onboarding.onboard_1000_genomes import onboard_1000_genomes

SUPPORTED_DATASETS = {
    "aws.opendata.registry.1000-genomes": {
        "dataset": {
            "name": "1000 Genomes",
            "description": "The 1000 Genomes Project is an international collaboration which has established the most detailed catalogue of human genetic variation, including SNPs, structural variants, and their haplotype context. The final phase of the project sequenced more than 2500 individuals from 26 different populations around the world and produced an integrated set of phased haplotypes with more than 80 million variants for these individuals."
        },
        "visa": {
            "id": "ga4gh-visa:aws.opendata.registry.1000-genomes",
            "name": "GA4GH Visa: 1000 Genomes",
            "description": "GA4GH Visa required to access data from the 1000 Genomes dataset",
        },
        "data_source": {
            "type": "s3",
            "s3info": {
                "custom_onboarding_function": onboard_1000_genomes
            }
        }
    }
}
