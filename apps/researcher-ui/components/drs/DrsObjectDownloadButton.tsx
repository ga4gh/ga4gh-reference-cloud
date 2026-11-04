'use client';

interface DrsObjectDownloadButtonProps {
  fileUrlString?: string
}

// refcloud-served manifest content requires a passport, which a plain browser link can't send,
// so those downloads go through the UI proxy (which attaches the passport from the access_token cookie)
const MANIFEST_CONTENT_PATH = /^\/ga4gh\/drs\/v1\/objects\/([^/]+)\/manifest-content$/;

const resolveDownload = (fileUrl: URL): { href: string, fileName: string } => {
  const manifestMatch = fileUrl.pathname.match(MANIFEST_CONTENT_PATH);
  if (manifestMatch && typeof window !== 'undefined' && fileUrl.host === window.location.host) {
    return {
      href: `/api${fileUrl.pathname}`,
      fileName: decodeURIComponent(manifestMatch[1]), // the manifest's DRS ID, e.g. <name>.manifest
    };
  }

  const pathName = fileUrl.pathname;
  return { href: fileUrl.toString(), fileName: pathName.substring(pathName.lastIndexOf('/') + 1) };
};

export default function DrsObjectDownloadButton({ fileUrlString }: DrsObjectDownloadButtonProps) {
  if (!fileUrlString) {
    return null;
  }

  try {
    const { href, fileName } = resolveDownload(new URL(fileUrlString));

    return (
      <a
        href={href}
        download={fileName}
        target="_blank"
        rel="noopener noreferrer"
        className="ga4gh-btn-dark aspect-auto"
      >
        <svg xmlns="http://w3.org" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5 mr-2">
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
        </svg>
        <span className="btn-text">Download</span>
      </a>
    )
  } catch (e) {
    console.error("Download failed: URL string is malformed.", fileUrlString);
    return null;
  }
}
