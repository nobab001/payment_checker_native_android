const { generateCustomRegex, regexToBrackets } = require('../backend/controllers/adminController'); // Wait, they might not be exported. Let's define them locally.

function generateCustomRegexLocal(smsText) {
  if (!smsText) return '';
  const patterns = smsText.split('|||');
  const compiledPatterns = patterns.map(patternText => {
      const tokens = patternText.split(/(\{[a-zA-Z0-9_]+\})/g);
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
  });
  return compiledPatterns.join('|||');
}

function regexToBracketsLocal(regexPattern) {
  if (!regexPattern) return '';
  const patterns = regexPattern.split('|||');
  const bracketPatterns = patterns.map(pattern => {
      let s = pattern;
      if (s.startsWith('^')) s = s.slice(1);
      if (s.endsWith('$')) s = s.slice(0, -1);

      s = s.split('(?<amount>[\\d,\\.]+)').join('{amount}');
      s = s.split('(?<sender>[\\d*xX]+)').join('{sender}');
      s = s.split('(?<trxid>[A-Za-z0-9]+)').join('{trxid}');
      s = s.split('(.*?)').join('{random}');
      s = s.split('(.*)').join('{random}');

      s = s.replace(/\\([-\/\\^$*+?.()|[\]{}])/g, '$1');
      return s;
  });
  return bracketPatterns.join('|||');
}

const input = `Money Received.
Amount: Tk {amount}
Sender: {sender}
Ref: {random}
TxnID: {trxid}
Balance: Tk {random}
{random}`;

console.log("INPUT:");
console.log(input);

const regex = generateCustomRegexLocal(input);
console.log("\nCOMPILED REGEX:");
console.log(regex);

const decoded = regexToBracketsLocal(regex);
console.log("\nDECODED BACK:");
console.log(decoded);

console.log("\nMATCH?", input === decoded);
process.exit(0);
