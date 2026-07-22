package online.paychek.app.ui.screen.apicenter.docs

/**
 * Integration docs — purpose-aware (Add Balance / Payment / Both) + per-framework steps.
 * Merchants open this from Website Settings (primary) or API tab.
 */

data class DocSection(
    val title: String,
    val description: String = "",
    val code: String = "",
    /** Optional step number shown in the rail (1, 2, 3…). */
    val step: Int? = null
)

data class DocLanguage(
    val id: String,
    val name: String,
    val sections: List<DocSection>
)

object ApiDocsCatalog {

    private const val BASE = "https://paychek.online"

    val overviewSections: List<DocSection> = listOf(
        DocSection(
            title = "লাইফসাইকেল (৫ ধাপ)",
            description = "১) সার্ভার থেকে HMAC init ২) কাস্টমারকে checkoutUrl ৩) পেমেন্ট + Trx ৪) Webhook/IPN ৫) প্রয়োজনে GET /api/v1/pay/:token/status। Success পেজকে সত্যের উৎস ধরবেন না।",
            step = 1
        ),
        DocSection(
            title = "কোথায় কী পাবেন",
            description = "প্রতিটি ওয়েবসাইটের Merchant ID, API Key ও Secret Key ওয়েবসাইট সেটিংস → মার্চেন্ট পরিচিতিতে। Secret শুধু তৈরির সময় একবার দেখা যায় — নিরাপদে সংরক্ষণ করুন।",
            step = 2
        ),
        DocSection(
            title = "Purpose আগে ঠিক করুন",
            description = "ওয়েবসাইট সেটিংসের Flag আইকন থেকে Add Balance, Pay, অথবা Both সিলেক্ট করে লক করুন। Both হলে প্রতিটি পেমেন্ট init-এ purpose পাঠাতে হবে।",
            step = 3
        ),
        DocSection(
            title = "সার্ভার থেকে init",
            description = "POST $BASE/api/v1/pay/init — amount, orderId, purpose, successUrl/cancelUrl/callbackUrl। বডি HMAC-SHA256 → X-Signature + X-Api-Key। রেসপন্স: sessionToken, checkoutUrl, expiresAt।",
            step = 4
        ),
        DocSection(
            title = "Status query",
            description = "GET $BASE/api/v1/pay/{sessionToken}/status + X-Api-Key — webhook মিস হলে বা success পেজ লোডে অর্ডার কনফার্ম। SSLCommerz validation / bKash query-এর মতো।",
            step = 5
        ),
        DocSection(
            title = "Webhook / Callback",
            description = "X-Paychek-Signature দিয়ে raw body ভেরিফাই করুন। Idempotent: একই trxId একবারই প্রসেস। Add Balance-এ walletCredit; Payment-এ expectedPayable, transactions[], overPaid।",
            step = 6
        ),
        DocSection(
            title = "Security ও Go-live",
            description = "Secret শুধু সার্ভারে; HTTPS webhook; ক্লায়েন্ট রিডাইরেক্ট trust নয়; টেস্ট /docs ও /test দিয়ে; ডিভাইস অনলাইন রাখুন। সম্পূর্ণ গাইড: paychek.online/docs",
            step = 7
        ),
        DocSection(
            title = "Payment Type / Commission কলব্যাক",
            description = "ডিফল্টে লক। নিজের সিস্টেমে হিসাব করতে পারলে লক রাখুন। শুধু কলব্যাকের মান দিয়ে ওয়ালেট ক্রেডিট করলে অ্যাডমিন দিয়ে আনলক করুন — নইলে টগল কাজ করবে না।",
            step = 8
        )
    )

    val addBalanceGuide: List<DocSection> = listOf(
        DocSection(
            step = 1,
            title = "কাস্টমার কী পাঠাবে",
            description = "চেকআউট অ্যামাউন্ট যত, কাস্টমার তত টাকাই পাঠাবে। কমিশন/চার্জ শুধু তথ্য — ওয়ালেট ক্রেডিট মার্চেন্ট নিজে করবে।"
        ),
        DocSection(
            step = 2,
            title = "Init উদাহরণ",
            code = """{
  "amount": 500,
  "orderId": "BAL-1001",
  "purpose": "add_balance",
  "successUrl": "https://yoursite.com/ok",
  "callbackUrl": "https://yoursite.com/hook"
}"""
        ),
        DocSection(
            step = 3,
            title = "সফল Callback (সংক্ষেপ)",
            code = """{
  "purpose": "add_balance",
  "checkoutAmount": 500,
  "receivedAmount": 500,
  "walletCredit": 502,
  "provider": "bkash",
  "status": "SUCCESS"
}"""
        ),
        DocSection(
            step = 4,
            title = "মার্চেন্ট করণীয়",
            description = "customerWallet += walletCredit। PayChek ওয়ালেট ক্রেডিট করে না।"
        )
    )

    val paymentGuide: List<DocSection> = listOf(
        DocSection(
            step = 1,
            title = "কাস্টমার কী পাঠাবে",
            description = "অর্ডার ৳500 হলে চার্জ/কমিশন অনুযায়ী expectedPayable (যেমন ৫০২) পাঠাতে হবে। পয়সা ০.৫০-এর নিচে হলে নিচের টাকা, ০.৫০ বা তার উপরে হলে উপরের টাকা।"
        ),
        DocSection(
            step = 2,
            title = "Init উদাহরণ",
            code = """{
  "amount": 500,
  "orderId": "ORD-9001",
  "purpose": "payment",
  "successUrl": "https://yoursite.com/ok",
  "callbackUrl": "https://yoursite.com/hook"
}"""
        ),
        DocSection(
            step = 3,
            title = "কম দিলে Settlement",
            description = "প্রথম Trx কম হলে checkout বলবে বাকি টাকা পাঠিয়ে আবার Trx দিন (সর্বোচ্চ ৫টি)। মোট ≥ expectedPayable হলে SUCCESS।"
        ),
        DocSection(
            step = 4,
            title = "বেশি দিলে",
            description = "SUCCESS + overPaid। গেটওয়ে রিফান্ড করে না — মার্চেন্ট সিদ্ধান্ত নেবে।"
        ),
        DocSection(
            step = 5,
            title = "সফল Callback (সংক্ষেপ)",
            code = """{
  "purpose": "payment",
  "orderAmount": 500,
  "expectedPayable": 502,
  "receivedAmount": 502,
  "transactions": [
    { "trxId": "A1", "amount": 500 },
    { "trxId": "A2", "amount": 2 }
  ],
  "status": "SUCCESS"
}"""
        )
    )

    val bothGuide: List<DocSection> = listOf(
        DocSection(
            step = 1,
            title = "দুই বাটন, দুই purpose",
            description = "এড ব্যালেন্স বাটন → purpose=add_balance। Buy/Pay/Offer বাটনগুলো → purpose=payment। খালি বা ভুল purpose → Hard Error।"
        ),
        DocSection(
            step = 2,
            title = "Add Balance বাটন",
            code = """await initPay({ amount: bal, purpose: "add_balance", orderId });"""
        ),
        DocSection(
            step = 3,
            title = "Buy / Pay বাটন",
            code = """await initPay({ amount: price, purpose: "payment", orderId });"""
        )
    )

    val languages: List<DocLanguage> = listOf(
        nodeJs(),
        php(),
        laravel(),
        python(),
        django(),
        java(),
        kotlin(),
        aspNet(),
        go(),
        react(),
        vue(),
        nextJs(),
        flutter()
    )

    private fun nodeJs() = DocLanguage(
        id = "node", name = "Node.js",
        sections = listOf(
            DocSection(step = 1, title = "ইনস্টল", description = "Express + axios।", code = "npm install express axios"),
            DocSection(
                step = 2, title = "HMAC সাইন হেল্পার",
                code = """const crypto = require('crypto');
const SECRET = process.env.PAYCHEK_SECRET; // api_secret

function signBody(bodyObj) {
  const raw = JSON.stringify(bodyObj);
  const sig = crypto.createHmac('sha256', SECRET).update(raw).digest('hex');
  return { raw, sig };
}"""
            ),
            DocSection(
                step = 3, title = "পেমেন্ট init (Add Balance বা Payment)",
                description = "Both সাইটে purpose বাধ্যতামূলক। Fixed মোডে সার্ভার purpose সেট করে দেয়।",
                code = """const axios = require('axios');
const API_KEY = process.env.PAYCHEK_KEY;

async function initPay({ amount, orderId, purpose }) {
  const body = {
    amount,
    orderId,
    purpose, // "add_balance" | "payment" — Both মোডে আবশ্যক
    successUrl: 'https://yoursite.com/payment/success',
    cancelUrl: 'https://yoursite.com/payment/cancel',
    callbackUrl: 'https://yoursite.com/paychek/webhook'
  };
  const { raw, sig } = signBody(body);
  const { data } = await axios.post('$BASE/api/v1/pay/init', raw, {
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': API_KEY,
      'X-Signature': sig
    },
    transformRequest: [(d) => d]
  });
  // data.checkoutUrl বা data.redirectUrl — কাস্টমারকে পাঠান
  return data;
}"""
            ),
            DocSection(
                step = 4, title = "Success রিডাইরেক্ট",
                code = """app.get('/payment/success', (req, res) => {
  const { trxId, amount, status, session } = req.query;
  // UI দেখান; আসল স্টেট webhook দিয়ে আপডেট করুন
  res.send(`Paid ${'$'}{status}: ${'$'}{trxId} (৳${'$'}{amount})`);
});"""
            ),
            DocSection(
                step = 5, title = "Webhook রিসিভ",
                description = "purpose অনুযায়ী walletCredit বা transactions হ্যান্ডল করুন।",
                code = """app.post('/paychek/webhook', express.raw({ type: '*/*' }), (req, res) => {
  const raw = req.body.toString('utf8');
  const sig = req.headers['x-paychek-signature'];
  const expected = crypto.createHmac('sha256', SECRET).update(raw).digest('hex');
  if (sig !== expected) return res.sendStatus(401);

  const p = JSON.parse(raw);
  if (p.purpose === 'add_balance') {
    // creditWallet(userId, p.walletCredit)
  } else if (p.purpose === 'payment') {
    // completeOrder(p.orderAmount, p.transactions, p.overPaid)
  }
  res.sendStatus(200);
});"""
            ),
            DocSection(
                step = 6, title = "ত্রুটি হ্যান্ডলিং",
                code = """// 400 PURPOSE_REQUIRED / PURPOSE_INVALID → Both মোডে purpose ঠিক করুন
// 401 INVALID_SIGNATURE → raw body ও secret মিলিয়ে দেখুন
// 429 → কিছুক্ষণ পর retry"""
            )
        )
    )

    private fun php() = DocLanguage(
        id = "php", name = "PHP",
        sections = listOf(
            DocSection(step = 1, title = "প্রস্তুতি", description = "php-curl চালু থাকতে হবে।", code = "sudo apt-get install php-curl"),
            DocSection(
                step = 2, title = "সাইন + init",
                code = """<?php
${'$'}secret = getenv('PAYCHEK_SECRET');
${'$'}apiKey = getenv('PAYCHEK_KEY');
${'$'}body = [
  'amount' => 500,
  'orderId' => 'ORD-1',
  'purpose' => 'payment', // or add_balance
  'successUrl' => 'https://yoursite.com/ok',
  'callbackUrl' => 'https://yoursite.com/hook'
];
${'$'}raw = json_encode(${'$'}body);
${'$'}sig = hash_hmac('sha256', ${'$'}raw, ${'$'}secret);

${'$'}ch = curl_init('$BASE/api/v1/pay/init');
curl_setopt_array(${'$'}ch, [
  CURLOPT_POST => true,
  CURLOPT_POSTFIELDS => ${'$'}raw,
  CURLOPT_HTTPHEADER => [
    'Content-Type: application/json',
    'X-Api-Key: ' . ${'$'}apiKey,
    'X-Signature: ' . ${'$'}sig
  ],
  CURLOPT_RETURNTRANSFER => true
]);
${'$'}res = curl_exec(${'$'}ch);
${'$'}data = json_decode(${'$'}res, true);
// redirect customer to ${'$'}data['checkoutUrl'] or redirectUrl"""
            ),
            DocSection(
                step = 3, title = "Webhook",
                code = """<?php
${'$'}raw = file_get_contents('php://input');
${'$'}sig = ${'$'}_SERVER['HTTP_X_PAYCHEK_SIGNATURE'] ?? '';
${'$'}expected = hash_hmac('sha256', ${'$'}raw, getenv('PAYCHEK_SECRET'));
if (!hash_equals(${'$'}expected, ${'$'}sig)) { http_response_code(401); exit; }
${'$'}p = json_decode(${'$'}raw, true);
// handle ${'$'}p['purpose'], walletCredit / transactions
http_response_code(200);"""
            ),
            DocSection(step = 4, title = "নোট", description = "Secret কখনো ফ্রন্টএন্ডে রাখবেন না। Both মোডে প্রতিটি বাটনে আলাদা purpose পাঠান।")
        )
    )

    private fun laravel() = DocLanguage(
        id = "laravel", name = "Laravel",
        sections = listOf(
            DocSection(step = 1, title = "Config", code = """# .env
PAYCHEK_KEY=pk_xxxx
PAYCHEK_SECRET=sk_xxxx
PAYCHEK_BASE=$BASE"""),
            DocSection(
                step = 2, title = "Init সার্ভিস",
                code = """use Illuminate\\Support\\Facades\\Http;

public function init(float ${'$'}amount, string ${'$'}orderId, string ${'$'}purpose)
{
    ${'$'}body = [
        'amount' => ${'$'}amount,
        'orderId' => ${'$'}orderId,
        'purpose' => ${'$'}purpose,
        'successUrl' => url('/payment/success'),
        'callbackUrl' => url('/paychek/webhook'),
    ];
    ${'$'}raw = json_encode(${'$'}body);
    ${'$'}sig = hash_hmac('sha256', ${'$'}raw, config('services.paychek.secret'));

    ${'$'}res = Http::withHeaders([
        'Content-Type' => 'application/json',
        'X-Api-Key' => config('services.paychek.key'),
        'X-Signature' => ${'$'}sig,
    ])->withBody(${'$'}raw, 'application/json')
      ->post(config('services.paychek.base').'/api/v1/pay/init');

    return ${'$'}res->json();
}"""
            ),
            DocSection(
                step = 3, title = "Route: Add Balance vs Pay",
                code = """Route::post('/wallet/topup', fn () => redirect(
  app(Paychek::class)->init(request('amount'), 'BAL-'.time(), 'add_balance')['checkoutUrl']
));
Route::post('/checkout', fn () => redirect(
  app(Paychek::class)->init(request('amount'), 'ORD-'.time(), 'payment')['checkoutUrl']
));"""
            ),
            DocSection(
                step = 4, title = "Webhook Controller",
                code = """public function webhook(Request ${'$'}request)
{
    ${'$'}raw = ${'$'}request->getContent();
    ${'$'}sig = ${'$'}request->header('X-Paychek-Signature');
    ${'$'}ok = hash_equals(hash_hmac('sha256', ${'$'}raw, config('services.paychek.secret')), ${'$'}sig ?? '');
    abort_unless(${'$'}ok, 401);
    ${'$'}p = json_decode(${'$'}raw, true);
    // purpose → walletCredit or complete order
    return response('ok', 200);
}"""
            )
        )
    )

    private fun python() = DocLanguage(
        id = "python", name = "Python",
        sections = listOf(
            DocSection(step = 1, title = "ইনস্টল", code = "pip install requests"),
            DocSection(
                step = 2, title = "init_pay",
                code = """import os, json, hmac, hashlib, requests

BASE = "$BASE"
KEY = os.environ["PAYCHEK_KEY"]
SECRET = os.environ["PAYCHEK_SECRET"]

def init_pay(amount, order_id, purpose):
    body = {
        "amount": amount,
        "orderId": order_id,
        "purpose": purpose,  # add_balance | payment
        "successUrl": "https://yoursite.com/ok",
        "callbackUrl": "https://yoursite.com/hook",
    }
    raw = json.dumps(body, separators=(",", ":"))
    sig = hmac.new(SECRET.encode(), raw.encode(), hashlib.sha256).hexdigest()
    r = requests.post(
        f"{BASE}/api/v1/pay/init",
        data=raw,
        headers={
            "Content-Type": "application/json",
            "X-Api-Key": KEY,
            "X-Signature": sig,
        },
    )
    r.raise_for_status()
    return r.json()"""
            ),
            DocSection(
                step = 3, title = "Webhook (Flask)",
                code = """from flask import Flask, request
app = Flask(__name__)

@app.post("/paychek/webhook")
def webhook():
    raw = request.get_data()
    sig = request.headers.get("X-Paychek-Signature", "")
    expected = hmac.new(SECRET.encode(), raw, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, sig):
        return ("", 401)
    p = request.get_json(force=True)
    # p["purpose"], walletCredit / transactions
    return ("", 200)"""
            )
        )
    )

    private fun django() = DocLanguage(
        id = "django", name = "Django",
        sections = listOf(
            DocSection(step = 1, title = "settings", code = """PAYCHEK_KEY = env("PAYCHEK_KEY")
PAYCHEK_SECRET = env("PAYCHEK_SECRET")
PAYCHEK_BASE = "$BASE""""),
            DocSection(
                step = 2, title = "views.init",
                code = """import json, hmac, hashlib, requests
from django.conf import settings
from django.http import JsonResponse, HttpResponse
from django.views.decorators.csrf import csrf_exempt

def start_payment(request):
    purpose = request.POST.get("purpose", "payment")
    body = {
        "amount": float(request.POST["amount"]),
        "orderId": request.POST["order_id"],
        "purpose": purpose,
        "successUrl": request.build_absolute_uri("/pay/ok/"),
        "callbackUrl": request.build_absolute_uri("/paychek/webhook/"),
    }
    raw = json.dumps(body, separators=(",", ":"))
    sig = hmac.new(settings.PAYCHEK_SECRET.encode(), raw.encode(), hashlib.sha256).hexdigest()
    r = requests.post(
        f"{settings.PAYCHEK_BASE}/api/v1/pay/init",
        data=raw,
        headers={"Content-Type": "application/json", "X-Api-Key": settings.PAYCHEK_KEY, "X-Signature": sig},
    )
    return JsonResponse(r.json())"""
            ),
            DocSection(
                step = 3, title = "webhook",
                code = """@csrf_exempt
def paychek_webhook(request):
    raw = request.body
    sig = request.headers.get("X-Paychek-Signature", "")
    expected = hmac.new(settings.PAYCHEK_SECRET.encode(), raw, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, sig):
        return HttpResponse(status=401)
    # parse json, credit wallet or complete order
    return HttpResponse(status=200)"""
            )
        )
    )

    private fun java() = DocLanguage(
        id = "java", name = "Java",
        sections = listOf(
            DocSection(step = 1, title = "ডিপেন্ডেন্সি", description = "OkHttp + Jackson (বা আপনার HTTP ক্লায়েন্ট)।"),
            DocSection(
                step = 2, title = "HMAC + init",
                code = """String raw = objectMapper.writeValueAsString(Map.of(
  "amount", 500,
  "orderId", "ORD-1",
  "purpose", "payment",
  "successUrl", "https://yoursite.com/ok",
  "callbackUrl", "https://yoursite.com/hook"
));
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
String sig = HexFormat.of().formatHex(mac.doFinal(raw.getBytes(UTF_8)));

Request req = new Request.Builder()
  .url("$BASE/api/v1/pay/init")
  .addHeader("Content-Type", "application/json")
  .addHeader("X-Api-Key", apiKey)
  .addHeader("X-Signature", sig)
  .post(RequestBody.create(raw, MediaType.get("application/json")))
  .build();"""
            ),
            DocSection(step = 3, title = "Webhook", description = "X-Paychek-Signature দিয়ে raw body ভেরিফাই করে purpose অনুযায়ী অর্ডার/ওয়ালেট আপডেট করুন।")
        )
    )

    private fun kotlin() = DocLanguage(
        id = "kotlin", name = "Kotlin",
        sections = listOf(
            DocSection(step = 1, title = "ইনস্টল", code = "// Gradle: implementation(\"com.squareup.okhttp3:okhttp:4.12.0\")"),
            DocSection(
                step = 2, title = "initPay",
                code = """fun sign(raw: String, secret: String): String {
  val mac = Mac.getInstance("HmacSHA256")
  mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
  return mac.doFinal(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}

// Build JSON body with amount, orderId, purpose ("add_balance"|"payment"),
// successUrl, callbackUrl — then:
// POST $BASE/api/v1/pay/init
// Headers: Content-Type, X-Api-Key, X-Signature=sign(raw, secret)"""
            ),
            DocSection(step = 3, title = "নোট", description = "অ্যান্ড্রয়েড অ্যাপে Secret রাখবেন না — সব init আপনার ব্যাকএন্ডে।")
        )
    )

    private fun aspNet() = DocLanguage(
        id = "aspnet", name = "ASP.NET",
        sections = listOf(
            DocSection(step = 1, title = "Config", code = """"Paychek": {
  "Key": "pk_xxxx",
  "Secret": "sk_xxxx",
  "Base": "$BASE"
}"""),
            DocSection(
                step = 2, title = "Init",
                code = """var body = new {
  amount = 500m,
  orderId = "ORD-1",
  purpose = "payment",
  successUrl = "https://yoursite.com/ok",
  callbackUrl = "https://yoursite.com/hook"
};
var raw = JsonSerializer.Serialize(body);
using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(_cfg["Paychek:Secret"]));
var sig = Convert.ToHexString(hmac.ComputeHash(Encoding.UTF8.GetBytes(raw))).ToLowerInvariant();

using var req = new HttpRequestMessage(HttpMethod.Post, $"{_cfg["Paychek:Base"]}/api/v1/pay/init");
req.Content = new StringContent(raw, Encoding.UTF8, "application/json");
req.Headers.Add("X-Api-Key", _cfg["Paychek:Key"]);
req.Headers.Add("X-Signature", sig);
var res = await _http.SendAsync(req);"""
            ),
            DocSection(step = 3, title = "Webhook", description = "EnableBuffering / raw body দিয়ে HMAC যাচাই করে purpose হ্যান্ডল করুন।")
        )
    )

    private fun go() = DocLanguage(
        id = "go", name = "Go",
        sections = listOf(
            DocSection(
                step = 1, title = "init",
                code = """body := []byte(`{"amount":500,"orderId":"ORD-1","purpose":"payment","successUrl":"https://yoursite.com/ok","callbackUrl":"https://yoursite.com/hook"}`)
mac := hmac.New(sha256.New, []byte(secret))
mac.Write(body)
sig := hex.EncodeToString(mac.Sum(nil))

req, _ := http.NewRequest("POST", "$BASE/api/v1/pay/init", bytes.NewReader(body))
req.Header.Set("Content-Type", "application/json")
req.Header.Set("X-Api-Key", apiKey)
req.Header.Set("X-Signature", sig)
resp, err := http.DefaultClient.Do(req)"""
            ),
            DocSection(
                step = 2, title = "webhook",
                code = """func webhook(w http.ResponseWriter, r *http.Request) {
  raw, _ := io.ReadAll(r.Body)
  sig := r.Header.Get("X-Paychek-Signature")
  mac := hmac.New(sha256.New, []byte(secret))
  mac.Write(raw)
  if !hmac.Equal([]byte(hex.EncodeToString(mac.Sum(nil))), []byte(sig)) {
    http.Error(w, "unauthorized", 401); return
  }
  w.WriteHeader(200)
}"""
            )
        )
    )

    private fun react() = DocLanguage(
        id = "react", name = "React",
        sections = listOf(
            DocSection(step = 1, title = "নিয়ম", description = "ব্রাউজারে Secret/HMAC নয়। React শুধু আপনার ব্যাকএন্ডে amount + purpose পাঠাবে।"),
            DocSection(
                step = 2, title = "Add Balance বাটন",
                code = """async function addBalance(amount) {
  const { data } = await axios.post('/api/paychek/init', {
    amount,
    purpose: 'add_balance'
  });
  window.location.href = data.checkoutUrl;
}"""
            ),
            DocSection(
                step = 3, title = "Buy / Pay বাটন",
                code = """async function buyNow(amount, orderId) {
  const { data } = await axios.post('/api/paychek/init', {
    amount,
    orderId,
    purpose: 'payment'
  });
  window.location.href = data.checkoutUrl;
}"""
            ),
            DocSection(step = 4, title = "Webhook", description = "Webhook আপনার Node/Laravel ইত্যাদি ব্যাকএন্ডে — React SPA থেকে নয়।")
        )
    )

    private fun vue() = DocLanguage(
        id = "vue", name = "Vue",
        sections = listOf(
            DocSection(step = 1, title = "নিয়ম", description = "Secret ক্লায়েন্ট বান্ডলে রাখবেন না। Init ব্যাকএন্ডে।"),
            DocSection(
                step = 2, title = "বাটন হ্যান্ডলার",
                code = """async function startPay(amount, purpose) {
  const { data } = await axios.post('/api/paychek/init', { amount, purpose })
  window.location.href = data.checkoutUrl
}
// @click="startPay(500, 'add_balance')"
// @click="startPay(price, 'payment')" """
            ),
            DocSection(step = 3, title = "Success পেজ", code = "const trxId = new URLSearchParams(location.search).get('trxId')")
        )
    )

    private fun nextJs() = DocLanguage(
        id = "nextjs", name = "Next.js",
        sections = listOf(
            DocSection(step = 1, title = "Env", code = "PAYCHEK_KEY=pk_xxxx\nPAYCHEK_SECRET=sk_xxxx"),
            DocSection(
                step = 2, title = "Route: init",
                code = """// app/api/paychek/init/route.ts
import crypto from 'crypto';

export async function POST(req: Request) {
  const { amount, orderId, purpose } = await req.json();
  const body = {
    amount, orderId, purpose,
    successUrl: process.env.NEXT_PUBLIC_SITE + '/pay/ok',
    callbackUrl: process.env.NEXT_PUBLIC_SITE + '/api/paychek/webhook'
  };
  const raw = JSON.stringify(body);
  const sig = crypto.createHmac('sha256', process.env.PAYCHEK_SECRET!).update(raw).digest('hex');
  const r = await fetch('$BASE/api/v1/pay/init', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': process.env.PAYCHEK_KEY!,
      'X-Signature': sig
    },
    body: raw
  });
  return Response.json(await r.json());
}"""
            ),
            DocSection(
                step = 3, title = "Webhook route",
                code = """// app/api/paychek/webhook/route.ts
export async function POST(req: Request) {
  const raw = await req.text();
  const sig = req.headers.get('x-paychek-signature') || '';
  const expected = crypto.createHmac('sha256', process.env.PAYCHEK_SECRET!).update(raw).digest('hex');
  if (sig !== expected) return new Response('no', { status: 401 });
  const p = JSON.parse(raw);
  // purpose → walletCredit | transactions
  return new Response('ok', { status: 200 });
}"""
            )
        )
    )

    private fun flutter() = DocLanguage(
        id = "flutter", name = "Flutter",
        sections = listOf(
            DocSection(step = 1, title = "নিয়ম", description = "মোবাইল অ্যাপে api_secret রাখবেন না। আপনার সার্ভার থেকে checkoutUrl নিন, তারপর url_launcher / WebView খুলুন।"),
            DocSection(
                step = 2, title = "ক্লায়েন্ট ফ্লো",
                code = """final res = await http.post(
  Uri.parse('https://yoursite.com/api/paychek/init'),
  body: jsonEncode({
    'amount': amount,
    'purpose': purpose, // add_balance | payment
    'orderId': orderId,
  }),
  headers: {'Content-Type': 'application/json'},
);
final url = jsonDecode(res.body)['checkoutUrl'] as String;
await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);"""
            ),
            DocSection(step = 3, title = "Webhook", description = "সব verify/webhook আপনার ব্যাকএন্ডে — Flutter অ্যাপে নয়।")
        )
    )
}
