import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';
import tesImg from '../../../static/img/tes-min.png'
import networksImg from '../../../static/img/networks.png'
import htsgetImg from '../../../static/img/htsget-min.png'

const FeatureList = [
  {
    title: 'Explore GA4GH APIs in Action',
    image: htsgetImg,
    description: (
      <>
        Interact with live, hands-on implementations of core standards to see exactly how they function in a real-world cloud environment.
      </>
    ),
  },
  {
    title: 'Interoperable GA4GH Ecosystem',
    image: tesImg,
    description: (
      <>
        Explore common genomics workflows in a pre-integrated stack where data discovery, access, and computation operate in one unified system.
      </>
    ),
  },
  {
    title: 'Your Open Source Starter Kit for Federated Genomics',
    image: networksImg,
    description: (
      <>
        Spin up your own instance of the reference cloud and begin sharing your genomic datasets with collaborators around the world.
      </>
    ),
  },
];

function Feature({image, title, description}: {image: string; title: string; description: React.ReactNode}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        {/* <Svg className={styles.featureSvg} alt={title} /> */}
        <img className={styles.featureImage} src={image} />
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