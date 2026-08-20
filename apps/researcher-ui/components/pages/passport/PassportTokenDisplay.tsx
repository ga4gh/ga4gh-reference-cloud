import React, {useState, useEffect } from 'react';
import PassportCopyButton from './PassportCopyButton';
import PassportIsCopiedButton from './PassportIsCopiedButton';

const base64Decode = (base64Url: string) => {
  // Replace Base64Url characters with standard Base64 characters
  let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  
  // Pad the string with '=' if necessary
  while (base64.length % 4) {
    base64 += '=';
  }

  // Decode Base64 to raw string, handling multi-byte UTF-8 characters safely
  const jsonStr = decodeURIComponent(
    atob(base64)
      .split('')
      .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
      .join('')
  );

  const jsonObj = JSON.parse(jsonStr)
  const jsonStrPretty = JSON.stringify(jsonObj, null, 2)
  return jsonStrPretty;
};

const PassportTokenDisplay = () => {
  const [token, setToken] = useState("");
  const [encodedHeader, setEncodedHeader] = useState(null);
  const [encodedPayload, setEncodedPayload] = useState(null);
  const [encodedSignature, setEncodedSignature] = useState(null);
  const [decodedHeader, setDecodedHeader] = useState<string | null>(null);
  const [decodedPayload, setDecodedPayload] = useState<string | null>(null);
  const [isCopied, setIsCopied] = useState(false);

  useEffect(() => {
    fetch('/api/oauth/my-passport-token', {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' }
      })
      .then((res) => res.json())
      .then((data) => {
        setToken(data.token)
        const tokenParts = data.token.split(".")
        setEncodedHeader(tokenParts[0])
        setEncodedPayload(tokenParts[1])
        setEncodedSignature(tokenParts[2])
        setDecodedHeader(base64Decode(tokenParts[0]))
        setDecodedPayload(base64Decode(tokenParts[1]))
      })
      .catch((err) => console.error("Error:", err));
  }, [])

  const copyEncodedJwtToClipboard = async () => {
    try {
      await navigator.clipboard.writeText(token)
      setIsCopied(true)
      setTimeout(() => setIsCopied(false), 3000)
    } catch (err) {
      console.error('Failed to copy text: ', err)
    }
  }

  return (
    <>
      <div className="card bg-base-100 box-shadow-card">
        <div className="card-body">
          <h2 className="card-title">
            Encoded JWT (copy token to use in GA4GH APIs)
            {isCopied ? <PassportIsCopiedButton /> : <PassportCopyButton copyEncodedJwtToClipboard={copyEncodedJwtToClipboard} />}
          </h2>
          <p className="font-mono bg-base-200 p-4 rounded-box break-all">
            <code><span className="text-primary">{encodedHeader}</span>.<span className="text-secondary">{encodedPayload}</span>.<span className="text-accent">{encodedSignature}</span></code>
          </p>
        </div>
      </div>
      <div className="flex w-full flex-col">
        <div className="divider" />
      </div>
      <div className="card bg-base-100 box-shadow-card">
        <div className="card-body">
          <h2 className="card-title">Decoded JWT Header</h2>
          <div className="font-mono bg-base-200 p-4 rounded-box break-all">
            <pre className="text-primary">
              {decodedHeader}
            </pre>
          </div>
        </div>
      </div>
      <div className="flex w-full flex-col">
        <div className="divider" />
      </div>
      <div className="card bg-base-100 box-shadow-card">
        <div className="card-body">
          <h2 className="card-title">Decoded JWT Payload</h2>
          <div className="font-mono bg-base-200 p-4 rounded-box break-all">
            <pre className="text-secondary">
              {decodedPayload}
            </pre>
          </div>
        </div>
      </div>
    </>
  )
}

export default PassportTokenDisplay;
