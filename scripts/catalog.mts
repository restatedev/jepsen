import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { isIP } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const broker = 'JepsenCatalogPrototype-BrokerE1355FD6-JuxTNIAmMVlH';
const audience = 'restate-jepsen-catalog:307946634685:us-east-1';
interface Lease {
  state: string;
  error?: string;
  outputs?: Record<string, string>;
}

async function invoke(action: string, parameters: Record<string, string> = {}): Promise<Lease> {
  const url = new URL(process.env.ACTIONS_ID_TOKEN_REQUEST_URL!);
  url.searchParams.set('audience', audience);
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN!}` },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error(`GitHub identity request failed: ${response.status}`);
  const { value: identityToken } = await response.json() as { value: string };
  if (typeof identityToken !== 'string') throw new Error('Missing GitHub identity token');
  console.log(`::add-mask::${identityToken}`);
  const temporary = mkdtempSync(join(tmpdir(), 'jepsen-catalog-'));
  try {
    const output = join(temporary, 'response.json');
    const payload = join(temporary, 'request.json');
    writeFileSync(payload, JSON.stringify({ action, identityToken, ...parameters }), { mode: 0o600 });
    const metadata = JSON.parse(execFileSync('aws', [
      'lambda', 'invoke', '--region', 'us-east-1', '--function-name', broker,
      '--cli-binary-format', 'raw-in-base64-out', '--payload', `file://${payload}`, output,
    ], {
      encoding: 'utf8', timeout: 60000, stdio: ['pipe', 'pipe', 'pipe'],
      env: { ...process.env, AWS_PAGER: '', AWS_MAX_ATTEMPTS: '3' },
    }));
    if (metadata.FunctionError || metadata.StatusCode !== 200) {
      throw new Error(`Broker rejected ${action}; inspect broker logs for this GitHub run`);
    }
    return JSON.parse(readFileSync(output, 'utf8')) as Lease;
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
}

async function waitFor(target: string, minutes: number): Promise<Lease> {
  const deadline = Date.now() + minutes * 60000;
  while (Date.now() < deadline) {
    const lease = await invoke('status');
    console.log(`Cluster state: ${lease.state}`);
    if (lease.state === target || (target === 'TERMINATED' && lease.state === 'ABSENT')) return lease;
    if (lease.error && target === "READY") throw new Error(`Cluster operation failed: ${lease.error}`);
    if (target === 'READY' && ['ABSENT', 'RELEASING', 'TERMINATED'].includes(lease.state)) {
      throw new Error('Cluster became unavailable before the test');
    }
    await delay(15000);
  }
  throw new Error(`Timed out waiting for ${target}; the broker retains its two-hour expiry cleanup`);
}

const action = process.argv[2];
if (process.env.GITHUB_REPOSITORY !== 'restatedev/jepsen' || process.env.GITHUB_REF !== 'refs/heads/main') {
  throw new Error('Run this client only from the Jepsen main workflow');
}
if (action === 'acquire') {
  mkdirSync('aws', { recursive: true });
  const key = 'aws/private-key.pem';
  if (!existsSync(key)) execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-C', '', '-f', key]);
  const publicKey = readFileSync(`${key}.pub`, 'utf8').trim();
  const response = await fetch('https://checkip.amazonaws.com', { signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error('Cannot determine runner IP');
  const ip = (await response.text()).trim();
  if (isIP(ip) !== 4) throw new Error('Expected an IPv4 runner address');
  await invoke('acquire', { publicKey, cidr: `${ip}/32` });
  const lease = await waitFor('READY', 20);
  const outputs = lease.outputs;
  if (!outputs || !outputs.BucketName || !outputs.DynamoDbMetadataTableName) {
    throw new Error('Missing cluster storage outputs');
  }
  const nodes = [1, 2, 3].map(n => outputs[`Node${n}`]);
  if (nodes.some(node => !node || !/^[a-z0-9.-]+$/.test(node))) throw new Error('Invalid worker endpoints');
  writeFileSync('aws/nodes.txt', `${nodes.join('\n')}\n`);
  writeFileSync('aws/cdk-outputs.json', JSON.stringify({ catalog: outputs }));
  console.log('Three workers ready');
} else if (action === 'release') {
  try {
    await invoke('release');
    await waitFor('TERMINATED', 20);
    console.log('Cluster deletion confirmed');
  } finally {
    rmSync('aws/private-key.pem', { force: true });
    rmSync('aws/private-key.pem.pub', { force: true });
  }
} else {
  throw new Error('Expected acquire or release');
}
