/**
 * PayChek Developer Docs — section nav + framework snippets + copy buttons.
 */
(function () {
  const BASE = 'https://paychek.online';

  const frameworks = [
    {
      id: 'node',
      name: 'Node.js',
      code: `const crypto = require('crypto');
const axios = require('axios');

function sign(raw, secret) {
  return crypto.createHmac('sha256', secret).update(raw).digest('hex');
}

async function initPay({ amount, orderId, purpose }) {
  const body = {
    amount, orderId, purpose,
    successUrl: 'https://yoursite.com/ok',
    callbackUrl: 'https://yoursite.com/hook'
  };
  const raw = JSON.stringify(body);
  const { data } = await axios.post('${BASE}/api/v1/pay/init', raw, {
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': process.env.PAYCHEK_KEY,
      'X-Signature': sign(raw, process.env.PAYCHEK_SECRET)
    },
    transformRequest: [(d) => d]
  });
  return data; // checkoutUrl / redirectUrl
}`
    },
    {
      id: 'php',
      name: 'PHP',
      code: `$body = [
  'amount' => 500,
  'orderId' => 'ORD-1',
  'purpose' => 'payment',
  'successUrl' => 'https://yoursite.com/ok',
  'callbackUrl' => 'https://yoursite.com/hook'
];
$raw = json_encode($body);
$sig = hash_hmac('sha256', $raw, getenv('PAYCHEK_SECRET'));

$ch = curl_init('${BASE}/api/v1/pay/init');
curl_setopt_array($ch, [
  CURLOPT_POST => true,
  CURLOPT_POSTFIELDS => $raw,
  CURLOPT_HTTPHEADER => [
    'Content-Type: application/json',
    'X-Api-Key: ' . getenv('PAYCHEK_KEY'),
    'X-Signature: ' . $sig
  ],
  CURLOPT_RETURNTRANSFER => true
]);
$res = json_decode(curl_exec($ch), true);`
    },
    {
      id: 'laravel',
      name: 'Laravel',
      code: `$body = [
  'amount' => $amount,
  'orderId' => $orderId,
  'purpose' => $purpose, // add_balance | payment
  'successUrl' => url('/ok'),
  'callbackUrl' => url('/paychek/webhook'),
];
$raw = json_encode($body);
$sig = hash_hmac('sha256', $raw, config('services.paychek.secret'));

$res = Http::withHeaders([
  'Content-Type' => 'application/json',
  'X-Api-Key' => config('services.paychek.key'),
  'X-Signature' => $sig,
])->withBody($raw, 'application/json')
  ->post(config('services.paychek.base').'/api/v1/pay/init');`
    },
    {
      id: 'python',
      name: 'Python',
      code: `import os, json, hmac, hashlib, requests

body = {
  "amount": 500,
  "orderId": "ORD-1",
  "purpose": "payment",
  "successUrl": "https://yoursite.com/ok",
  "callbackUrl": "https://yoursite.com/hook",
}
raw = json.dumps(body, separators=(",", ":"))
sig = hmac.new(os.environ["PAYCHEK_SECRET"].encode(), raw.encode(), hashlib.sha256).hexdigest()
r = requests.post(
  "${BASE}/api/v1/pay/init",
  data=raw,
  headers={
    "Content-Type": "application/json",
    "X-Api-Key": os.environ["PAYCHEK_KEY"],
    "X-Signature": sig,
  },
)
print(r.json())`
    },
    {
      id: 'nextjs',
      name: 'Next.js',
      code: `// app/api/paychek/init/route.ts
import crypto from 'crypto';

export async function POST(req: Request) {
  const { amount, orderId, purpose } = await req.json();
  const body = { amount, orderId, purpose,
    successUrl: process.env.NEXT_PUBLIC_SITE + '/ok',
    callbackUrl: process.env.NEXT_PUBLIC_SITE + '/api/paychek/webhook' };
  const raw = JSON.stringify(body);
  const sig = crypto.createHmac('sha256', process.env.PAYCHEK_SECRET!).update(raw).digest('hex');
  const r = await fetch('${BASE}/api/v1/pay/init', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': process.env.PAYCHEK_KEY!,
      'X-Signature': sig
    },
    body: raw
  });
  return Response.json(await r.json());
}`
    },
    {
      id: 'react',
      name: 'React',
      code: `// Browser: never put Secret here.
// Call YOUR backend; backend does HMAC + pay/init.

async function startPay(amount, purpose, orderId) {
  const { data } = await axios.post('/api/paychek/init', { amount, purpose, orderId });
  window.location.href = data.checkoutUrl;
}
// addBalance → purpose: 'add_balance'
// buyNow → purpose: 'payment'`
    },
    {
      id: 'go',
      name: 'Go',
      code: `body := []byte(\`{"amount":500,"orderId":"ORD-1","purpose":"payment","successUrl":"https://yoursite.com/ok","callbackUrl":"https://yoursite.com/hook"}\`)
mac := hmac.New(sha256.New, []byte(secret))
mac.Write(body)
sig := hex.EncodeToString(mac.Sum(nil))
req, _ := http.NewRequest("POST", "${BASE}/api/v1/pay/init", bytes.NewReader(body))
req.Header.Set("Content-Type", "application/json")
req.Header.Set("X-Api-Key", apiKey)
req.Header.Set("X-Signature", sig)
resp, err := http.DefaultClient.Do(req)`
    },
    {
      id: 'flutter',
      name: 'Flutter',
      code: `// Secret stays on your server.
final res = await http.post(
  Uri.parse('https://yoursite.com/api/paychek/init'),
  headers: {'Content-Type': 'application/json'},
  body: jsonEncode({
    'amount': amount,
    'purpose': purpose, // add_balance | payment
    'orderId': orderId,
  }),
);
final url = jsonDecode(res.body)['checkoutUrl'] as String;
await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);`
    }
  ];

  function showSection(id) {
    document.querySelectorAll('.docs-section').forEach((el) => {
      el.classList.toggle('active', el.getAttribute('data-section') === id);
    });
    document.querySelectorAll('.docs-nav-group a').forEach((a) => {
      a.classList.toggle('active', a.getAttribute('data-section') === id);
    });
    const target = document.getElementById('sec-' + id);
    if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function renderFrameworks() {
    const chips = document.getElementById('langChips');
    const panels = document.getElementById('langPanels');
    if (!chips || !panels) return;
    chips.innerHTML = '';
    panels.innerHTML = '';
    frameworks.forEach((fw, i) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = fw.name;
      btn.className = i === 0 ? 'active' : '';
      btn.addEventListener('click', () => {
        chips.querySelectorAll('button').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        panels.querySelectorAll('.lang-panel').forEach((p) => p.classList.remove('active'));
        document.getElementById('lang-' + fw.id)?.classList.add('active');
      });
      chips.appendChild(btn);

      const panel = document.createElement('div');
      panel.className = 'lang-panel' + (i === 0 ? ' active' : '');
      panel.id = 'lang-' + fw.id;
      panel.innerHTML =
        '<div class="code-block"><div class="code-bar"><span>' +
        fw.name.toUpperCase() +
        '</span><button type="button" class="copy" data-copy>Copy</button></div><pre></pre></div>';
      panel.querySelector('pre').textContent = fw.code;
      panels.appendChild(panel);
    });
  }

  function wireCopy() {
    document.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-copy]');
      if (!btn) return;
      const block = btn.closest('.code-block');
      const pre = block?.querySelector('pre');
      if (!pre) return;
      navigator.clipboard.writeText(pre.textContent || '').then(() => {
        const old = btn.textContent;
        btn.textContent = 'Copied';
        setTimeout(() => { btn.textContent = old || 'Copy'; }, 1200);
      });
    });
  }

  function wireNav() {
    document.querySelectorAll('.docs-nav-group a').forEach((a) => {
      a.addEventListener('click', (e) => {
        e.preventDefault();
        const id = a.getAttribute('data-section');
        if (id) {
          history.replaceState(null, '', '#' + id);
          showSection(id);
        }
      });
    });
    const hash = (location.hash || '#overview').replace('#', '');
    showSection(hash || 'overview');
  }

  window.PaychekDocs = {
    init() {
      renderFrameworks();
      wireCopy();
      wireNav();
    }
  };
})();
