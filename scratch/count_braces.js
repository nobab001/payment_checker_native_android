const fs = require('fs');
const content = fs.readFileSync('app/app/src/main/java/online/paychek/app/ui/screen/dashboard/DashboardScreen.kt', 'utf8');

let braceCount = 0;
let lines = content.split('\n');
let insideLazyColumn = false;
let foundOpenBrace = false;

for (let i = 0; i < lines.length; i++) {
    let line = lines[i];
    let cleanLine = line.replace(/\/\/.*$/, '').replace(/"(\\.|[^"\\])*"/g, '');
    
    if (cleanLine.includes('LazyColumn(')) {
        insideLazyColumn = true;
        foundOpenBrace = false;
        braceCount = 0;
    }
    
    if (insideLazyColumn) {
        if (!foundOpenBrace) {
            if (cleanLine.includes('{')) {
                foundOpenBrace = true;
                let idx = cleanLine.indexOf('{');
                let remaining = cleanLine.substring(idx);
                for (let char of remaining) {
                    if (char === '{') braceCount++;
                    if (char === '}') braceCount--;
                }
            }
        } else {
            for (let char of cleanLine) {
                if (char === '{') braceCount++;
                if (char === '}') braceCount--;
            }
            
            if (i >= 710 && i <= 740) {
                console.log(`Line ${i + 1} [bc=${braceCount}]: ${line.trim()}`);
            }
            
            if (braceCount === 0) {
                insideLazyColumn = false;
            }
        }
    }
}
