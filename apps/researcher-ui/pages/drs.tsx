import type { NextPage } from 'next'
import { useRouter } from 'next/router';
import { useState, useEffect } from 'react';
import AppLayout from 'components/layout/AppLayout';
import DrsManifestTable from 'components/drs/DrsManifestTable';
import { Dataset, PassportVisaAssertionStatus } from './datasets';
import { useEnv } from '@/context/EnvContext'

const DRS: NextPage = () => {
  const env = useEnv();
  const router = useRouter();
  const { code, state } = router.query;
  const [isProcessingExchange, setIsProcessingExchange] = useState(false);
  const [flowComplete, setFlowComplete] = useState(false);
  const [approvedDatasetMap, setApprovedDatasetMap] = useState<Record<string, Dataset>>({});
  const [selectedDatasetId, setSelectedDatasetId] = useState<string>("");

  // complete OAuth flow to get "Passport" JWT
  useEffect(() => {
    // Wait for Next.js router to fully parse query parameters on mount
    if (!router.isReady) return;

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
          // Token is saved securely as an HttpOnly cookie via the server handler
          setFlowComplete(true);
          router.push('/drs');
        } else {
          console.error("Token swap failed:", data.details);
        }
      })
      .catch((err) => console.error("Network crash during swap:", err));

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
      hydraAuthUrl.searchParams.append('state', (state as string) || crypto.randomUUID());

      // Transfer window execution entirely out of Next.js state engine
      window.location.href = hydraAuthUrl.toString();
    }
  }, [router.isReady, code, state]);

  // fetch datasets from API using user's Ory Session Token
  useEffect(() => {
      // fetch datasets from API
      const fetchDatasets = async () => {
        try {
          const response = await fetch('/api/datasets');
  
          if (!response.ok) {
            throw new Error('datasets API response was not ok');
          }
  
          const result = await response.json()
          const resultFiltered = result.filter((dataset:Dataset) => dataset.visa.assertion.currentStatus === "Approved" )
          const newApprovedDatasetMap = Object.fromEntries(
            resultFiltered.map((dataset: Dataset) => [dataset.id, dataset]))
          setApprovedDatasetMap(newApprovedDatasetMap);
  
        } catch (error) {
          console.error('Failed to fetch datasets', error);
        }
      }
  
      fetchDatasets();
    }, [])

  return (
    <>
      <AppLayout>
        <h1 className="mb-5 text-5xl font-bold">Data Repository Service (DRS)</h1>
        <h2 className="mb-4 text-2xl">View & Inspect DRS Objects</h2>
        {flowComplete ? (
          <>
            <select className="select select-bordered w-full max-w-xs" onChange={e => setSelectedDatasetId(e.target.value)}>
              <option disabled selected>Select Dataset</option>
              {(Object.keys(approvedDatasetMap) as Array<keyof Dataset>).map((datasetId) => (
                <option key={datasetId} value={datasetId}>{approvedDatasetMap[datasetId].name}</option>
              ))}
            </select>
            {selectedDatasetId !== "" ? (
              <DrsManifestTable
                selectedDatasetId={selectedDatasetId}
              />
            ) : (
              null
            )}
          </>
        ) : (
          <h2 className="mb-4 text-2xl">Preparing passport token</h2>
        )}
      </AppLayout>
    </>
  )
}

export default DRS;
