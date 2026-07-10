const KEY = 'paychek_checkout_lang';

export const LANG = Object.freeze({
  BN: 'bn',
  EN: 'en',
});

const DICT = Object.freeze({
  [LANG.BN]: {
    pay_amount: 'পরিশোধযোগ্য মোট পরিমাণ',
    vibe_prompt: 'আপনি যে নাম্বার থেকে টাকা পাঠাবেন সেই নাম্বারটি লিখুন',
    vibe_go: 'চেকআউটে যান',
    verify_payment: 'ভেরিফাই পেমেন্ট',
    verify_short: 'Verify',
    trx_label: 'Transaction ID',
    waiting_title: 'পেমেন্টের জন্য অপেক্ষা করা হচ্ছে',
    searching: 'পেমেন্ট খোঁজা হচ্ছে...',
    checking_sms: 'SMS চেক করা হচ্ছে...',
    verifying: 'ভেরিফাই করা হচ্ছে...',
    didnt_receive: 'পেমেন্ট পেলেন না? ম্যানুয়ালি ভেরিফাই করুন',
    copy_hint: '✅ নাম্বার কপি হয়েছে। এখন আপনার মোবাইল ব্যাংকিং অ্যাপ খুলুন।',
    progress_copied_title: '📋 নাম্বার কপি হয়েছে',
    progress_copied_sub: '➡️ এখন আপনার মোবাইল ব্যাংকিং অ্যাপ খুলুন',
    progress_received_title: '✅ Payment Received',
    progress_received_sub: 'Verifying...',
    progress_success_title: '🎉 Payment Successful',
    progress_success_sub: 'Redirecting...',
    progress_waiting: '● Waiting Payment',
    progress_detecting: '○ Detecting',
    progress_verifying: '○ Verifying',
    progress_completed: '○ Completed',
    checkout_ready: '✓ Checkout Ready',
    verify_btn: 'Verify Payment',
    verify_manual: 'Verify Manually',
    automatic: 'Automatic',
    manual: 'Manual',
    copied: 'নম্বর কপি হয়েছে',
    copy_failed: 'কপি ব্যর্থ হয়েছে',
    help_title: 'কীভাবে পেমেন্ট করবেন?',
    help_step1: '① নাম্বার কপি করুন',
    help_step2: '② আপনার মোবাইল ব্যাংকিং অ্যাপে টাকা পাঠান',
    help_step3: '③ পেমেন্ট সম্পন্ন হলে Verify করুন',
    help_step4: '④ Success হলে অপেক্ষা করুন',
    err_trx_not_found_title: 'Transaction ID খুঁজে পাওয়া যায়নি।',
    err_try_again: 'দয়া করে আবার চেষ্টা করুন।',
    success_title: 'Payment Successful',
  },
  [LANG.EN]: {
    pay_amount: 'Total payable amount',
    vibe_prompt: 'Which number will you send payment from?',
    vibe_go: 'Go to checkout',
    verify_payment: 'Verify Payment',
    verify_short: 'Verify',
    trx_label: 'Transaction ID',
    waiting_title: 'Waiting for payment',
    searching: 'Searching payment...',
    checking_sms: 'Checking SMS...',
    verifying: 'Verifying...',
    didnt_receive: "Didn't receive? Verify Manually",
    copy_hint: '✅ Number copied. Now open your mobile banking app.',
    progress_copied_title: '📋 Number copied',
    progress_copied_sub: '➡️ Open your mobile banking app now',
    progress_received_title: '✅ Payment Received',
    progress_received_sub: 'Verifying...',
    progress_success_title: '🎉 Payment Successful',
    progress_success_sub: 'Redirecting...',
    progress_waiting: '● Waiting Payment',
    progress_detecting: '○ Detecting',
    progress_verifying: '○ Verifying',
    progress_completed: '○ Completed',
    checkout_ready: '✓ Checkout Ready',
    verify_btn: 'Verify Payment',
    verify_manual: 'Verify Manually',
    automatic: 'Automatic',
    manual: 'Manual',
    copied: 'Number copied',
    copy_failed: 'Copy failed',
    help_title: 'How to pay?',
    help_step1: '① Copy the number',
    help_step2: '② Send money from your mobile banking app',
    help_step3: '③ Verify after payment',
    help_step4: '④ Wait on success',
    err_trx_not_found_title: 'Transaction ID not found.',
    err_try_again: 'Please try again.',
    success_title: 'Payment Successful',
  },
});

export function getLang() {
  const v = localStorage.getItem(KEY);
  return v === LANG.EN ? LANG.EN : LANG.BN;
}

export function setLang(lang) {
  const next = lang === LANG.EN ? LANG.EN : LANG.BN;
  localStorage.setItem(KEY, next);
  document.documentElement.setAttribute('lang', next);
  document.dispatchEvent(new CustomEvent('checkout:lang-change', { detail: { lang: next } }));
}

export function t(key) {
  const lang = getLang();
  return DICT[lang]?.[key] ?? DICT[LANG.BN][key] ?? key;
}

export function applyI18n(root = document) {
  const lang = getLang();
  document.documentElement.setAttribute('lang', lang);
  root.querySelectorAll('[data-i18n]').forEach((el) => {
    const k = el.getAttribute('data-i18n');
    if (!k) return;
    el.textContent = t(k);
  });
}

