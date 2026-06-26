
const prisma = require('./db/prisma');

function generateCustomRegex(smsText) {
  if (!smsText) return '';
  const tokens = smsText.split(/(\{[a-zA-Z0-9_]+\})/g);
  const result = tokens.map(token => {
      if (token.startsWith('{') && token.endsWith('}')) {
          const tag = token.slice(1, -1);
          if (tag === 'amount') return '(?<amount>[\\d,\\.]+)';
          if (tag === 'sender') return '(?<sender>[\\d*xX]+)';
          if (tag === 'trxid') return '(?<trxid>[A-Za-z0-9]+)';
          if (tag === 'random') return '(.*)';
          return '(.*?)';
      } else {
          return token.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
      }
  }).join('');
  return `^${result}$`;
}

async function updateTemplates() {
    try {
        await prisma.sms_templates.deleteMany({});
        console.log('Deleted existing templates.');

        const templates = [
            {
                template_name: 'bKash Receive Money',
                sender_id: 'bKash',
                sender_number: 'bKash',
                regex_pattern: generateCustomRegex('You have received Tk {amount} from {sender}. Fee Tk 0.00. Balance Tk {random}. TrxID {trxid} at {random}'),
                is_official: 1,
                is_active: 1,
                matching_keyword: ''
            },
            {
                template_name: 'bKash Cash In',
                sender_id: 'bKash',
                sender_number: 'bKash',
                regex_pattern: generateCustomRegex('Cash In Tk {amount} from {sender} successful. Fee Tk 0.00. Balance Tk {random}. TrxID {trxid} at {random}. Download App: https://bKa.sh/8app'),
                is_official: 1,
                is_active: 1,
                matching_keyword: ''
            }
        ];

        for (const t of templates) {
            await prisma.sms_templates.create({ data: t });
        }
        console.log('Inserted new templates.');
    } catch (err) {
        console.error(err);
    } finally {
        await prisma.$disconnect();
    }
}
updateTemplates();

