const dbPattern = "^You have received Tk (?<amount>[\\d,\\.]+) from (?<sender>[\\d*xX]+)\\. Fee Tk 0\\.00\\. Balance Tk (.*)\\. TrxID (?<trxid>[A-Za-z0-9]+) at (.*)$";

console.log("Original Pattern:", dbPattern);

function testRegexToBrackets(regexPattern) {
  if (!regexPattern) return '';
  const patterns = regexPattern.split('|||');
  const bracketPatterns = patterns.map(pattern => {
      let s = pattern;
      if (s.startsWith('^')) s = s.slice(1);
      if (s.endsWith('$')) s = s.slice(0, -1);

      console.log("Before replacements:", s);
      s = s.split('(?<amount>[\\d,\\.]+)').join('{amount}');
      console.log("After amount split/join:", s);
      s = s.split('(?<sender>[\\d*xX]+)').join('{sender}');
      console.log("After sender split/join:", s);
      s = s.split('(?<trxid>[A-Za-z0-9]+)').join('{trxid}');
      console.log("After trxid split/join:", s);
      s = s.split('(.*?)').join('{random}');
      s = s.split('(.*)').join('{random}');

      s = s.replace(/\\([-\/\\^$*+?.()|[\]{}])/g, '$1');
      return s;
  });
  return bracketPatterns.join('|||');
}

const result = testRegexToBrackets(dbPattern);
console.log("Result:", result);
process.exit(0);
