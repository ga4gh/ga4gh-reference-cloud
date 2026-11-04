import React from 'react';

export default function CenteredImage({ src, alt, maxWidth = '400px' }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', margin: '20px 0' }}>
      <img src={src} alt={alt || 'Image'} style={{ maxWidth: maxWidth, height: 'auto' }} />
    </div>
  );
}
