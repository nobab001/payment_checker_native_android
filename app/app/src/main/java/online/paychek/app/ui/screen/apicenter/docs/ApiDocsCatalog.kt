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

    private fun laravel() = DocLanguage(
        id = "laravel", name = "Laravel",
        sections = listOf(
            DocSection("1. Installation", "Guzzle HTTP client ইনস্টল করুন (Laravel HTTP facade-এও ব্যবহার করা যায়)।", "composer require guzzlehttp/guzzle"),
            DocSection(
                "2. Sample Code", "Controller থেকে checkout-এ রিডাইরেক্ট।",
                """// routes/web.php
Route::post('/pay', [PaymentController::class, 'start']);

// PaymentController.php
public function start(Request ${'$'}request) {
    ${'$'}url = "$BASE/checkout.html?apiKey=".config('paychek.api_key')
          ."&amount=".${'$'}request->amount;
    return redirect()->away(${'$'}url);
}"""
            ),
            DocSection(
                "3. API Example", "Laravel HTTP client দিয়ে claim-check।",
                """use Illuminate\Support\Facades\Http;

${'$'}res = Http::post('$BASE/api/transactions/claim-check', [
    'apiKey' => config('paychek.api_key'),
    'trxId'  => ${'$'}trxId,
    'amount' => ${'$'}amount,
])->json();"""
            ),
            DocSection(
                "4. Callback Example", "Success রিডাইরেক্ট route।",
                """Route::get('/payment/success', function (Request ${'$'}r) {
    ${'$'}order->update(['status' => 'paid', 'trx_id' => ${'$'}r->query('trxId')]);
    return view('thankyou');
});"""
            ),
            DocSection(
                "5. Webhook Example", "Webhook controller (CSRF ছাড় দিন VerifyCsrfToken-এ)।",
                """Route::post('/paychek/webhook', function (Request ${'$'}r) {
    ${'$'}data = ${'$'}r->all(); // trxId, amount, provider, payment_type?, commission?
    // process...
    return response()->json(['ok' => true], 200);
});"""
            ),
            DocSection(
                "6. Security Example", "HMAC signature middleware।",
                """${'$'}raw = ${'$'}request->getContent();
${'$'}expected = hash_hmac('sha256', ${'$'}raw, config('paychek.secret'));
abort_unless(hash_equals(${'$'}expected, ${'$'}request->header('X-Paychek-Signature')), 401);"""
            ),
            DocSection(
                "7. Error Handling Example", "Http client exception হ্যান্ডলিং।",
                """${'$'}response = Http::retry(3, 200)->post(${'$'}url, ${'$'}payload);
if (${'$'}response->status() === 429) { /* backoff */ }
if (${'$'}response->failed()) { Log::error(${'$'}response->body()); }"""
            )
        )
    )

    private fun django() = DocLanguage(
        id = "django", name = "Django",
        sections = listOf(
            DocSection("1. Installation", "requests ইনস্টল করুন।", "pip install django requests"),
            DocSection(
                "2. Sample Code", "checkout-এ রিডাইরেক্ট।",
                """from django.shortcuts import redirect
def pay(request):
    amount = request.POST['amount']
    return redirect(f"$BASE/checkout.html?apiKey={settings.PAYCHEK_KEY}&amount={amount}")"""
            ),
            DocSection(
                "3. API Example", "claim-check।",
                """import requests
r = requests.post("$BASE/api/transactions/claim-check", json={
    "apiKey": settings.PAYCHEK_KEY, "trxId": trx_id, "amount": amount
})
data = r.json()"""
            ),
            DocSection(
                "4. Callback Example", "success view।",
                """def success(request):
    trx = request.GET.get('trxId')
    Order.objects.filter(trx_id=trx).update(status='paid')
    return render(request, 'thankyou.html')"""
            ),
            DocSection(
                "5. Webhook Example", "csrf_exempt webhook।",
                """@csrf_exempt
def webhook(request):
    payload = json.loads(request.body)
    # payload['trxId'], payload['amount'], payload['payment_type']
    return JsonResponse({'ok': True})"""
            ),
            DocSection(
                "6. Security Example", "HMAC যাচাই।",
                """import hmac, hashlib
expected = hmac.new(settings.PAYCHEK_SECRET.encode(), request.body, hashlib.sha256).hexdigest()
if not hmac.compare_digest(expected, request.headers.get('X-Paychek-Signature','')):
    return HttpResponse(status=401)"""
            ),
            DocSection("7. Error Handling Example", "timeout ও status হ্যান্ডলিং।", "try:\n    r = requests.post(url, json=payload, timeout=10)\nexcept requests.Timeout:\n    schedule_retry()")
        )
    )

    private fun java() = DocLanguage(
        id = "java", name = "Java",
        sections = listOf(
            DocSection("1. Installation", "OkHttp dependency যোগ করুন।", "// build.gradle\nimplementation 'com.squareup.okhttp3:okhttp:4.12.0'"),
            DocSection(
                "2. Sample Code", "checkout URL তৈরি।",
                """String url = "$BASE/checkout.html?apiKey=" + apiKey + "&amount=" + amount;
response.sendRedirect(url);"""
            ),
            DocSection(
                "3. API Example", "OkHttp দিয়ে claim-check।",
                """OkHttpClient client = new OkHttpClient();
String json = "{\"apiKey\":\"pk_xxxx\",\"trxId\":\"" + trxId + "\",\"amount\":" + amount + "}";
Request req = new Request.Builder()
    .url("$BASE/api/transactions/claim-check")
    .post(RequestBody.create(json, MediaType.parse("application/json")))
    .build();
try (Response res = client.newCall(req).execute()) {
    String body = res.body().string();
}"""
            ),
            DocSection("4. Callback Example", "Servlet success handler।", "String trxId = request.getParameter(\"trxId\");\n// mark order paid"),
            DocSection(
                "5. Webhook Example", "webhook servlet।",
                """@WebServlet("/paychek/webhook")
public class Hook extends HttpServlet {
  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String body = req.getReader().lines().collect(Collectors.joining());
    // parse trxId, amount, payment_type
    res.setStatus(200);
  }
}"""
            ),
            DocSection(
                "6. Security Example", "HMAC-SHA256 যাচাই।",
                """Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
String expected = Hex.encodeHexString(mac.doFinal(rawBody.getBytes()));
if (!MessageDigest.isEqual(expected.getBytes(), signature.getBytes())) throw new SecurityException();"""
            ),
            DocSection("7. Error Handling Example", "status code হ্যান্ডলিং।", "if (res.code() == 429) { Thread.sleep(500); retry(); }\nelse if (!res.isSuccessful()) { log.error(res.body().string()); }")
        )
    )

    private fun kotlin() = DocLanguage(
        id = "kotlin", name = "Kotlin",
        sections = listOf(
            DocSection("1. Installation", "Retrofit + Gson।", "implementation 'com.squareup.retrofit2:retrofit:2.11.0'\nimplementation 'com.squareup.retrofit2:converter-gson:2.11.0'"),
            DocSection(
                "2. Sample Code", "checkout URL।",
                """val url = "$BASE/checkout.html?apiKey=${'$'}apiKey&amount=${'$'}amount"
// open in browser / WebView"""
            ),
            DocSection(
                "3. API Example", "Retrofit interface + call।",
                """interface PaychekApi {
  @POST("api/transactions/claim-check")
  suspend fun claim(@Body body: ClaimRequest): ClaimResponse
}
data class ClaimRequest(val apiKey: String, val trxId: String, val amount: Double)"""
            ),
            DocSection("4. Callback Example", "deep link / redirect পড়া।", "val trxId = uri.getQueryParameter(\"trxId\")\nval status = uri.getQueryParameter(\"status\")"),
            DocSection(
                "5. Webhook Example", "Ktor webhook route (সার্ভার সাইড)।",
                """post("/paychek/webhook") {
    val payload = call.receive<Map<String, Any>>()
    // payload["trxId"], payload["amount"], payload["payment_type"]
    call.respond(HttpStatusCode.OK)
}"""
            ),
            DocSection(
                "6. Security Example", "HMAC যাচাই।",
                """val mac = Mac.getInstance("HmacSHA256")
mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
val expected = mac.doFinal(rawBody.toByteArray()).joinToString("") { "%02x".format(it) }
require(MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray()))"""
            ),
            DocSection("7. Error Handling Example", "coroutine try/catch।", "try { api.claim(req) }\ncatch (e: HttpException) { if (e.code() == 429) retry() }")
        )
    )

    private fun aspNet() = DocLanguage(
        id = "aspnet", name = "ASP.NET",
        sections = listOf(
            DocSection("1. Installation", "HttpClient বিল্ট-ইন; JSON-এর জন্য।", "dotnet add package Newtonsoft.Json"),
            DocSection(
                "2. Sample Code", "checkout redirect (MVC)।",
                """public IActionResult Pay(decimal amount) {
    var url = ${'$'}"$BASE/checkout.html?apiKey={_cfg["Paychek:Key"]}&amount={amount}";
    return Redirect(url);
}"""
            ),
            DocSection(
                "3. API Example", "HttpClient দিয়ে claim-check।",
                """using var http = new HttpClient();
var body = new { apiKey = "pk_xxxx", trxId, amount };
var res = await http.PostAsJsonAsync("$BASE/api/transactions/claim-check", body);
var data = await res.Content.ReadFromJsonAsync<ClaimResult>();"""
            ),
            DocSection("4. Callback Example", "success action।", "public IActionResult Success(string trxId, string status) {\n    // mark paid\n    return View();\n}"),
            DocSection(
                "5. Webhook Example", "webhook endpoint (minimal API)।",
                """app.MapPost("/paychek/webhook", async (HttpRequest req) => {
    var payload = await req.ReadFromJsonAsync<WebhookDto>();
    // payload.TrxId, payload.Amount, payload.PaymentType
    return Results.Ok();
});"""
            ),
            DocSection(
                "6. Security Example", "HMAC যাচাই।",
                """using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
var expected = Convert.ToHexString(hmac.ComputeHash(Encoding.UTF8.GetBytes(rawBody))).ToLower();
if (!CryptographicOperations.FixedTimeEquals(
        Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(signature)))
    return Results.Unauthorized();"""
            ),
            DocSection("7. Error Handling Example", "status হ্যান্ডলিং।", "if ((int)res.StatusCode == 429) await Task.Delay(500);\nres.EnsureSuccessStatusCode();")
        )
    )

    private fun go() = DocLanguage(
        id = "go", name = "Go",
        sections = listOf(
            DocSection("1. Installation", "স্ট্যান্ডার্ড net/http যথেষ্ট।", "go get github.com/go-resty/resty/v2 // ঐচ্ছিক"),
            DocSection(
                "2. Sample Code", "checkout redirect।",
                """url := fmt.Sprintf("$BASE/checkout.html?apiKey=%s&amount=%.2f", apiKey, amount)
http.Redirect(w, r, url, http.StatusFound)"""
            ),
            DocSection(
                "3. API Example", "net/http দিয়ে claim-check।",
                """body, _ := json.Marshal(map[string]any{"apiKey": apiKey, "trxId": trxId, "amount": amount})
resp, err := http.Post("$BASE/api/transactions/claim-check",
    "application/json", bytes.NewReader(body))
defer resp.Body.Close()"""
            ),
            DocSection("4. Callback Example", "success handler।", "func success(w http.ResponseWriter, r *http.Request) {\n    trxId := r.URL.Query().Get(\"trxId\")\n    _ = trxId // mark paid\n}"),
            DocSection(
                "5. Webhook Example", "webhook handler।",
                """func webhook(w http.ResponseWriter, r *http.Request) {
    var p map[string]any
    json.NewDecoder(r.Body).Decode(&p)
    // p["trxId"], p["amount"], p["payment_type"]
    w.WriteHeader(http.StatusOK)
}"""
            ),
            DocSection(
                "6. Security Example", "HMAC যাচাই।",
                """mac := hmac.New(sha256.New, []byte(secret))
mac.Write(rawBody)
expected := hex.EncodeToString(mac.Sum(nil))
if !hmac.Equal([]byte(expected), []byte(signature)) {
    http.Error(w, "unauthorized", 401); return
}"""
            ),
            DocSection("7. Error Handling Example", "status চেক ও retry।", "if resp.StatusCode == 429 {\n    time.Sleep(500 * time.Millisecond); /* retry */\n}")
        )
    )

    private fun react() = DocLanguage(
        id = "react", name = "React",
        sections = listOf(
            DocSection("1. Installation", "axios (বা fetch)।", "npm install axios"),
            DocSection(
                "2. Sample Code", "checkout-এ রিডাইরেক্ট (সার্ভার থেকে URL নিন)।",
                """async function pay(amount) {
  const { data } = await axios.post('/api/create-order', { amount });
  window.location.href = data.checkoutUrl; // "$BASE/checkout.html?apiKey=..."
}"""
            ),
            DocSection(
                "3. API Example", "⚠ claim-check সবসময় ব্যাকএন্ডে করুন — Secret ফ্রন্টএন্ডে রাখবেন না।",
                """// frontend শুধু নিজের ব্যাকএন্ডে কথা বলবে
const res = await axios.post('/api/verify', { trxId, amount });"""
            ),
            DocSection("4. Callback Example", "success পেজে query param পড়া।", "const params = new URLSearchParams(window.location.search);\nconst trxId = params.get('trxId');"),
            DocSection("5. Webhook Example", "Webhook সবসময় সার্ভার-সাইড (React SPA-তে webhook receive হয় না)। আপনার Node/Express ব্যাকএন্ডে receive করুন।"),
            DocSection("6. Security Example", "API Key public হতে পারে; Secret কখনো ব্রাউজারে নয়। সব verify/HMAC ব্যাকএন্ডে।"),
            DocSection("7. Error Handling Example", "UI error state।", "try { await pay(amount) }\ncatch (e) { setError(e.response?.data?.message ?? 'Payment failed') }")
        )
    )

    private fun vue() = DocLanguage(
        id = "vue", name = "Vue",
        sections = listOf(
            DocSection("1. Installation", "axios।", "npm install axios"),
            DocSection(
                "2. Sample Code", "checkout redirect।",
                """async function pay(amount) {
  const { data } = await axios.post('/api/create-order', { amount });
  window.location.href = data.checkoutUrl;
}"""
            ),
            DocSection("3. API Example", "⚠ verify সবসময় ব্যাকএন্ডে।", "const res = await axios.post('/api/verify', { trxId, amount });"),
            DocSection("4. Callback Example", "success route param।", "const trxId = new URLSearchParams(location.search).get('trxId')"),
            DocSection("5. Webhook Example", "Webhook সার্ভার-সাইডে। Vue SPA থেকে নয়।"),
            DocSection("6. Security Example", "Secret Key কখনো client bundle-এ রাখবেন না।"),
            DocSection("7. Error Handling Example", "ref error দেখান।", "try { await pay(a) } catch (e) { error.value = e.message }")
        )
    )

    private fun nextJs() = DocLanguage(
        id = "nextjs", name = "Next.js",
        sections = listOf(
            DocSection("1. Installation", "axios (server actions/route handlers-এ)।", "npm install axios"),
            DocSection(
                "2. Sample Code", "Route handler থেকে checkout URL।",
                """// app/api/create-order/route.ts
export async function POST(req: Request) {
  const { amount } = await req.json();
  const url = `$BASE/checkout.html?apiKey=${'$'}{process.env.PAYCHEK_KEY}&amount=${'$'}{amount}`;
  return Response.json({ checkoutUrl: url });
}"""
            ),
            DocSection(
                "3. API Example", "server-side claim-check (Secret env-এ)।",
                """// app/api/verify/route.ts
const r = await fetch('$BASE/api/transactions/claim-check', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ apiKey: process.env.PAYCHEK_KEY, trxId, amount })
});"""
            ),
            DocSection("4. Callback Example", "success page searchParams।", "export default function Success({ searchParams }) {\n  return <p>Paid: {searchParams.trxId}</p>;\n}"),
            DocSection(
                "5. Webhook Example", "webhook route handler।",
                """// app/api/paychek/webhook/route.ts
export async function POST(req: Request) {
  const raw = await req.text();
  const payload = JSON.parse(raw); // trxId, amount, payment_type
  return new Response('ok', { status: 200 });
}"""
            ),
            DocSection(
                "6. Security Example", "HMAC যাচাই (Node crypto)।",
                """import crypto from 'crypto';
const expected = crypto.createHmac('sha256', process.env.PAYCHEK_SECRET!)
  .update(raw).digest('hex');
const sig = req.headers.get('x-paychek-signature') || '';
if (!crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(sig)))
  return new Response('unauthorized', { status: 401 });"""
            ),
            DocSection("7. Error Handling Example", "route handler try/catch।", "try { /* verify */ }\ncatch (e) { return Response.json({ error: 'failed' }, { status: 500 }) }")
        )
    )

    private fun flutter() = DocLanguage(
        id = "flutter", name = "Flutter",
        sections = listOf(
            DocSection("1. Installation", "http + url_launcher (Backend example অংশে সার্ভার সাইড দেখুন)।", "dart pub add http url_launcher"),
            DocSection(
                "2. Sample Code", "checkout WebView/browser খুলুন।",
                """final url = Uri.parse('$BASE/checkout.html?apiKey=${'$'}apiKey&amount=${'$'}amount');
await launchUrl(url, mode: LaunchMode.externalApplication);"""
            ),
            DocSection(
                "3. API Example", "⚠ claim-check আপনার Backend-এ করুন। Flutter শুধু নিজের backend কল করবে।",
                """final res = await http.post(
  Uri.parse('https://your-backend.com/verify'),
  headers: {'Content-Type': 'application/json'},
  body: jsonEncode({'trxId': trxId, 'amount': amount}),
);"""
            ),
            DocSection("4. Callback Example", "deep link দিয়ে success ধরুন।", "// app_links / uni_links প্যাকেজ দিয়ে trxId, status পড়ুন"),
            DocSection(
                "5. Webhook Example (Backend)", "Node backend যেটি Flutter অ্যাপকে সার্ভ করে।",
                """// server.js (Flutter backend)
app.post('/paychek/webhook', (req, res) => {
  const { trxId, amount, payment_type } = req.body;
  notifyDevice(trxId); // FCM push to Flutter app
  res.sendStatus(200);
});"""
            ),
            DocSection("6. Security Example", "Secret শুধু Backend-এ; HMAC backend-এ যাচাই। Flutter অ্যাপে Secret embed করবেন না।"),
            DocSection("7. Error Handling Example", "status হ্যান্ডলিং।", "if (res.statusCode == 429) { await retryLater(); }\nelse if (res.statusCode != 200) { showError(res.body); }")
        )
    )
}
