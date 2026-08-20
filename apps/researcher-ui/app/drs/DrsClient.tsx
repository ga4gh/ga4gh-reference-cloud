'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useEffect } from 'react';
import AppLayout from '@/components/layout/AppLayout';
import DrsManifestTable from '@/components/pages/drs/DrsManifestTable';
import { Dataset } from '../datasets/DatasetsClient'; 
import { useEnv } from '@/components/context/EnvContext';

export default function DrsClient() {
  const env = useEnv();
  const router = useRouter();
  
  const searchParams = useSearchParams();
  const code = searchParams?.get('code') || undefined;
  const state = searchParams?.get('state') || undefined;

  const [isProcessingExchange, setIsProcessingExchange] = useState(false);
  const [flowComplete, setFlowComplete] = useState(false);
  const [approvedDatasetMap, setApprovedDatasetMap] = useState<Record<string, Dataset>>({});
  const [selectedDatasetId, setSelectedDatasetId] = useState<string>("");

  // PIPELINE 1: Complete OAuth flow to get "Passport" JWT
  useEffect(() => {
    if (flowComplete) {
      console.log("Token already present. Escaping loop to DRS Page.");
      router.push('/drs');
      return;
    }

    // PIPELINE A: WE HAVE AN OAUTH CODE FROM HYDRA -> RUN EXCHANGE ONLY
    if (!flowComplete && code && typeof code === 'string') {
      if (isProcessingExchange) return; // Prevent double-triggering exchange
      setIsProcessingExchange(true);

      console.log("Found Hydra Code. Swapping for JWT via Server API...");

      fetch('/api/oauth/token-exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code,
          redirect_uri: `${env.UI_BASE_URL}/drs`
        })
      })
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          setFlowComplete(true);
          router.push('/drs');
        } else {
          console.error("Token swap failed:", data.details);
          setIsProcessingExchange(false);
        }
      })
      .catch((err) => {
        console.error("Network crash during swap:", err);
        setIsProcessingExchange(false);
      });

      return; // CRITICAL: Stop execution here. Do NOT run initial redirect logic.
    }

    // PIPELINE B: NO CODE & NO TOKEN YET -> INITIATE THE LOGIN REDIRECT ONLY ONCE
    if (!flowComplete && !code) {
      console.log("No token or code found. Transitioning window control to Ory Hydra...");

      const hydraAuthUrl = new URL(`${env.HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL}/oauth2/auth`);
      hydraAuthUrl.searchParams.append('client_id', `${env.HYDRA_RESEARCHER_CLIENT_ID}`);
      hydraAuthUrl.searchParams.append('response_type', 'code');
      hydraAuthUrl.searchParams.append('scope', 'openid offline_access profile');
      hydraAuthUrl.searchParams.append('redirect_uri', `${env.UI_BASE_URL}/drs`);
      hydraAuthUrl.searchParams.append('state', state || crypto.randomUUID());

      window.location.href = hydraAuthUrl.toString();
    }
  }, [code, state, flowComplete, isProcessingExchange, env, router]);

  // PIPELINE 2: Fetch datasets from API using user's Ory Session Token
  useEffect(() => {
    const fetchDatasets = async () => {
      try {
        const response = await fetch('/api/datasets');

        if (!response.ok) {
          throw new Error('datasets API response was not ok');
        }

        const result = await response.json();
        // Safe check to make sure nested visa and assertion items exist before checking status
        const resultFiltered = result.filter((dataset: Dataset) => dataset.visa?.assertion?.currentStatus === "Approved");
        const newApprovedDatasetMap = Object.fromEntries(
          resultFiltered.map((dataset: Dataset) => [dataset.id, dataset])
        );
        setApprovedDatasetMap(newApprovedDatasetMap);
      } catch (error) {
        console.error('Failed to fetch datasets', error);
      }
    };

    fetchDatasets();
  }, []);

  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">Data Repository Service (DRS)</h1>
      <h2 className="mb-4 text-2xl">View & Inspect DRS Objects</h2>
      {flowComplete ? (
        <>
          <select 
            className="ga4gh-select w-full max-w-xs" 
            defaultValue="Select Dataset" 
            onChange={e => setSelectedDatasetId(e.target.value)}
          >
            <option disabled value="Select Dataset">Select Dataset</option>
            {Object.keys(approvedDatasetMap).map((datasetId) => (
              <option key={datasetId} value={datasetId}>
                {approvedDatasetMap[datasetId].name}
              </option>
            ))}
          </select>
          {selectedDatasetId !== "" ? (
            <DrsManifestTable selectedDatasetId={selectedDatasetId} />
          ) : null}
        </>
      ) : (
        <h2 className="mb-4 text-xl">preparing passport token...</h2>
      )}
    </AppLayout>
  );
}
