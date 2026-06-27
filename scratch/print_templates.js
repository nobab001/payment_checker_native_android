const prisma = require('../backend/db/prisma');

async function main() {
  const templates = await prisma.sms_templates.findMany({
    where: { is_official: 1 }
  });
  console.log("TEMPLATES IN DB:");
  templates.forEach(t => {
    console.log(`ID: ${t.id}, Name: ${t.template_name}`);
    console.log(`  Raw Regex: ${t.regex_pattern}`);
    console.log(`  sender_number: ${t.sender_number}`);
  });
  process.exit(0);
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
