const prisma = require('../backend/db/prisma');

async function testUpdate() {
  const templateId = 1;
  const original = await prisma.sms_templates.findUnique({
    where: { id: templateId }
  });
  console.log("Original template name:", original.template_name);

  // Perform mock save
  const data = {
    template_name: original.template_name + " Updated",
    sender_id: original.sender_id,
    sender_number: original.sender_number,
    matching_keyword: original.matching_keyword,
    regex_pattern: original.regex_pattern,
    is_active: original.is_active,
    is_parseable: original.is_parseable,
    is_official: 1,
    updated_at: new Date()
  };

  const updateResult = await prisma.sms_templates.updateMany({
    where: { id: parseInt(templateId), is_official: 1 },
    data
  });
  console.log("Update result count:", updateResult.count);

  const updated = await prisma.sms_templates.findUnique({
    where: { id: templateId }
  });
  console.log("Updated template name:", updated.template_name);

  // Restore
  await prisma.sms_templates.update({
    where: { id: templateId },
    data: { template_name: original.template_name }
  });
  console.log("Restored original template name.");
}

testUpdate().then(() => process.exit(0)).catch(err => {
  console.error(err);
  process.exit(1);
});
