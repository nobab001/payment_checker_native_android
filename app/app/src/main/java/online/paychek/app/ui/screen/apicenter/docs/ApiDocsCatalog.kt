package online.paychek.app.ui.screen.apicenter.docs

/**
 * ApiDocsCatalog — data-driven integration documentation.
 *
 * All merchant integrations talk to the same PayCheck REST API, so each language
 * documents the same flow: create a checkout / verify a transaction (claim-check)
 * and receive a webhook. Add or complete languages here without touching UI code.
 */

data class DocSection(
    val title: String,
    val description: String = "",
    val code: String = ""
)

data class DocLanguage(
    val id: String,
    val name: String,
    val sections: List<DocSection>
)

object ApiDocsCatalog {

    // Shared endpoint reference used across examples.
    private const val BASE = "https://paychek.online"

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

    // ── Node.js (Express) — fully documented reference implementation ─────────
    private fun nodeJs() = DocLanguage(
        id = "node", name = "Node.js",
        sections = listOf(
            DocSection("1. Installation", "Express সার্ভারে HTTP client হিসেবে axios ব্যবহার করুন।", "npm install express axios"),
            DocSection(
                "2. Sample Code", "একটি অর্ডার শুরু করার জন্য গ্রাহককে PayCheck checkout-এ পাঠান।",
                """const express = require('express');
const app = express();
app.use(express.json());

const MERCHANT_ID = 'MID_XXXX';
const API_KEY = 'pk_xxxx';

app.post('/pay', (req, res) => {
  const { amount, orderId } = req.body;
  const url = `$BASE/checkout.html?apiKey=${'$'}{API_KEY}&amount=${'$'}{amount}&order=${'$'}{orderId}`;
  res.json({ checkoutUrl: url });
});

app.listen(4000);"""
            ),
            DocSection(
                "3. API Example", "সার্ভার-টু-সার্ভার ট্রানজেকশন ভেরিফাই (claim-check)।",
                """const axios = require('axios');

async function verify(trxId, amount) {
  const { data } = await axios.post('$BASE/api/transactions/claim-check', {
    apiKey: 'pk_xxxx',
    trxId,
    amount
  });
  return data; // { success, data: { trxId, amount, provider, ... } }
}"""
            ),
            DocSection(
                "4. Callback Example", "Success/Cancel রিডাইরেক্ট হ্যান্ডলিং।",
                """app.get('/payment/success', (req, res) => {
  const { trxId, amount, status } = req.query;
  // TODO: mark order paid
  res.send(`Payment ${'$'}{status}: ${'$'}{trxId} (${'$'}{amount})`);
});"""
            ),
            DocSection(
                "5. Webhook Example", "PayCheck আপনার webhook_url-এ POST করবে।",
                """app.post('/paychek/webhook', (req, res) => {
  const { trxId, amount, provider, status } = req.body;
  // payment_type ও commission ফিল্ড আসবে যদি Admin অনুমতি থাকে
  console.log('Webhook:', trxId, amount, provider, status);
  res.sendStatus(200);
});"""
            ),
            DocSection(
                "6. Security Example", "HMAC signature যাচাই (X-Paychek-Signature হেডার)।",
                """const crypto = require('crypto');
function verifySignature(rawBody, signature, secret) {
  const expected = crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature));
}"""
            ),
            DocSection(
                "7. Error Handling Example", "রেসপন্স কোড অনুযায়ী নিরাপদ হ্যান্ডলিং।",
                """try {
  const r = await verify(trxId, amount);
  if (!r.success) handleNotFound(r.error);
} catch (e) {
  if (e.response?.status === 429) retryLater();
  else logError(e.message);
}"""
            )
        )
    )

    private fun php() = DocLanguage(
        id = "php", name = "PHP",
        sections = listOf(
            DocSection("1. Installation", "cURL এক্সটেনশন সাধারণত PHP-তে বিল্ট-ইন থাকে।", "sudo apt-get install php-curl"),
            DocSection(
                "2. Sample Code", "গ্রাহককে checkout-এ রিডাইরেক্ট করুন।",
                """<?php
${'$'}apiKey = 'pk_xxxx';
${'$'}amount = 500;
${'$'}url = "$BASE/checkout.html?apiKey={${'$'}apiKey}&amount={${'$'}amount}";
header("Location: {${'$'}url}");"""
            ),
            DocSection(
                "3. API Example", "claim-check দিয়ে ট্রানজেকশন ভেরিফাই।",
                """<?php
${'$'}ch = curl_init('$BASE/api/transactions/claim-check');
curl_setopt(${'$'}ch, CURLOPT_POST, true);
curl_setopt(${'$'}ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt(${'$'}ch, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
curl_setopt(${'$'}ch, CURLOPT_POSTFIELDS, json_encode([
  'apiKey' => 'pk_xxxx', 'trxId' => ${'$'}trxId, 'amount' => ${'$'}amount
]));
${'$'}res = json_decode(curl_exec(${'$'}ch), true);"""
            ),
            DocSection("4. Callback Example", "success URL-এ query param পড়ুন।", "<?php\n\$trxId = \$_GET['trxId'];\n\$status = \$_GET['status'];"),
            DocSection(
                "5. Webhook Example", "webhook body পড়া।",
                """<?php
${'$'}body = json_decode(file_get_contents('php://input'), true);
// ${'$'}body['trxId'], ${'$'}body['amount'], ${'$'}body['payment_type']
http_response_code(200);"""
            ),
            DocSection(
                "6. Security Example", "HMAC signature যাচাই।",
                """<?php
${'$'}raw = file_get_contents('php://input');
${'$'}sig = ${'$'}_SERVER['HTTP_X_PAYCHEK_SIGNATURE'] ?? '';
${'$'}expected = hash_hmac('sha256', ${'$'}raw, ${'$'}secret);
if (!hash_equals(${'$'}expected, ${'$'}sig)) { http_response_code(401); exit; }"""
            ),
            DocSection("7. Error Handling Example", "HTTP status অনুযায়ী হ্যান্ডল করুন।", "<?php\n\$code = curl_getinfo(\$ch, CURLINFO_HTTP_CODE);\nif (\$code === 429) { /* retry */ }")
        )
    )

    private fun python() = DocLanguage(
        id = "python", name = "Python",
        sections = listOf(
            DocSection("1. Installation", "requests লাইব্রেরি ব্যবহার করুন।", "pip install requests"),
            DocSection(
                "2. Sample Code", "checkout URL তৈরি।",
                "api_key = 'pk_xxxx'\namount = 500\nurl = f'$BASE/checkout.html?apiKey={api_key}&amount={amount}'"
            ),
            DocSection(
                "3. API Example", "claim-check ভেরিফাই।",
                """import requests
r = requests.post('$BASE/api/transactions/claim-check', json={
    'apiKey': 'pk_xxxx', 'trxId': trx_id, 'amount': amount
})
data = r.json()"""
            ),
            DocSection("4. Callback Example", "Flask success handler।", "@app.route('/success')\ndef success():\n    return request.args.get('trxId')"),
            DocSection(
                "5. Webhook Example", "webhook receiver।",
                """@app.route('/webhook', methods=['POST'])
def webhook():
    payload = request.get_json()
    # payload['trxId'], payload['amount'], payload['payment_type']
    return '', 200"""
            ),
            DocSection(
                "6. Security Example", "HMAC যাচাই।",
                """import hmac, hashlib
expected = hmac.new(secret.encode(), raw_body, hashlib.sha256).hexdigest()
if not hmac.compare_digest(expected, signature):
    abort(401)"""
            ),
            DocSection("7. Error Handling Example", "status code চেক।", "if r.status_code == 429:\n    retry_later()\nelif not r.ok:\n    log(r.text)")
        )
    )

    // ── Remaining languages: structured skeletons completed in Phase 3 ────────
    private fun genericSkeleton(id: String, name: String, installNote: String) = DocLanguage(
        id = id, name = name,
        sections = listOf(
            DocSection("1. Installation", installNote),
            DocSection("2. Sample Code", "গ্রাহককে $BASE/checkout.html?apiKey=pk_xxxx&amount=... এ রিডাইরেক্ট করুন।"),
            DocSection("3. API Example", "POST $BASE/api/transactions/claim-check — body: { apiKey, trxId, amount }"),
            DocSection("4. Callback Example", "Success URL query params: trxId, amount, status।"),
            DocSection("5. Webhook Example", "PayCheck আপনার webhook_url-এ JSON POST করবে: { trxId, amount, provider, status, payment_type?, commission? }"),
            DocSection("6. Security Example", "X-Paychek-Signature হেডারের HMAC-SHA256 আপনার Secret Key দিয়ে যাচাই করুন।"),
            DocSection("7. Error Handling Example", "HTTP 404 = not found, 429 = rate limited (retry), 401 = signature mismatch।")
        )
    )

    private fun laravel() = genericSkeleton("laravel", "Laravel", "composer require guzzlehttp/guzzle")
    private fun django() = genericSkeleton("django", "Django", "pip install django requests")
    private fun java() = genericSkeleton("java", "Java", "implementation 'com.squareup.okhttp3:okhttp:4.12.0'")
    private fun kotlin() = genericSkeleton("kotlin", "Kotlin", "implementation 'com.squareup.retrofit2:retrofit:2.11.0'")
    private fun aspNet() = genericSkeleton("aspnet", "ASP.NET", "dotnet add package Newtonsoft.Json")
    private fun go() = genericSkeleton("go", "Go", "go get github.com/go-resty/resty/v2")
    private fun react() = genericSkeleton("react", "React", "npm install axios")
    private fun vue() = genericSkeleton("vue", "Vue", "npm install axios")
    private fun nextJs() = genericSkeleton("nextjs", "Next.js", "npm install axios")
    private fun flutter() = genericSkeleton("flutter", "Flutter", "dart pub add http")
}
