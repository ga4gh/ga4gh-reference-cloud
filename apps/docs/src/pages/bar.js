import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import styles from './index.module.css';
import HomepageFeatures from '../components/HomepageFeatures';

const Bar = () => {
    const {siteConfig} =  useDocusaurusContext();
    return (
        <div>
            <h1 className="hero__title">This is the BAR page</h1>
            <h2 className="hero_subtitle">Please do not visit the Foo page</h2>
        </div>
    )
}

export default Bar;