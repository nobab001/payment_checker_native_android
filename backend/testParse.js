const { parseRawSms } = require('./utils/parseRawSms');

async function run() {
  const body1 = 'You have received Tk 10.00 from 01648074862. Fee Tk 0.00. Balance Tk 3,750.84. TrxID DFN3MI1YZX at 23/06/2026 22:36';
  const body2 = 'Cash In Tk 1,000.00 from 01821592626 successful. Fee Tk 0.00. Balance Tk 11,306.35. TrxID DFN8MGGJY6 at 23/06/2026 22:03. Download App: https://bKa.sh/8app';

  console.log('--- TESTING SMS 1 (Received) ---');
  console.log(await parseRawSms(body1, 'bKash'));

  console.log('\n--- TESTING SMS 2 (Cash In) ---');
  console.log(await parseRawSms(body2, 'bKash'));
}
run();
