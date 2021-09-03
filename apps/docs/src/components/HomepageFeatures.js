import React from 'react';
import clsx from 'clsx';
import styles from './HomepageFeatures.module.css';
import tesImg from '../../static/img/tes-min.png'
import networksImg from '../../static/img/networks.png'
import htsgetImg from '../../static/img/htsget-min.png'

const FeatureList = [
  {
    title: 'Deploy Anywhere',
    image: htsgetImg,
    description: (
      <>
        Starter Kit services are unopinionated about where they can be run.
        Deploy a service network on your local laptop, a high performance compute
        cluster, or a public cloud provider.
      </>
    ),
  },
  {
    title: 'Modular Design',
    image: tesImg,
    description: (
      <>
        Each microservice in the Starter Kit suite implements a single GA4GH
        specification. Deploy only the required services for your data sharing
        needs.
      </>
    ),
  },
  {
    title: 'Explore Federated Genomics',
    image: networksImg,
    description: (
      <>
        Quickly set up networks of GA4GH services to explore how GA4GH standards
        work together to facilitate federated genomic data sharing. 
      </>
    ),
  },
];

function Feature({image, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        {/* <Svg className={styles.featureSvg} alt={title} /> */}
        <img src={image} />
      </div>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
