import React from 'react';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import CodeBlock from '@theme/CodeBlock';

export default function HttpsCurlCodeBlock({ curlCommand, httpsRequest }) {
  return (
    <Tabs>
      <TabItem value="curl" label="cURL" default>
        <CodeBlock language="bash" className="margin-vert--md">
          {curlCommand}
        </CodeBlock>
      </TabItem>
      <TabItem value="https" label="HTTPS">
        <CodeBlock language="http" className="margin-vert--md">
          {httpsRequest}
        </CodeBlock>
      </TabItem>
    </Tabs>
  );
}
