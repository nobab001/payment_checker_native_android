const fs = require('fs');
let text = fs.readFileSync('app.js', 'utf8');
text = text.replace(/`/g, '`');
text = text.replace(/\$/g, '$');
fs.writeFileSync('app.js', text);
