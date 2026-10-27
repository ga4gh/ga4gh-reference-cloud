'use client';

interface DrsObjectDownloadButtonProps {
  fileUrlString?: string
}

export default function DrsObjectDownloadButton({ fileUrlString }: DrsObjectDownloadButtonProps) {
  if (!fileUrlString) {
    return null;
  }

  try {
    const fileUrl = new URL(fileUrlString);
    const pathName = fileUrl.pathname;
    const fileName = pathName.substring(pathName.lastIndexOf('/') + 1);

    return (
      <a
        href={fileUrlString}
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
