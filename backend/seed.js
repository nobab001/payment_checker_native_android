
const prisma = require('./db/prisma');
const { generateCustomRegex } = require('./controllers/adminController');

async function seed() {
    await prisma.checkout_view_templates.deleteMany({
      where: { sms_template_id: { in: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] } }
    });
    await prisma.sms_templates.deleteMany({
      where: { id: { in: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] } }
    });

    const defaultSmsTemplates = [
      { 
        id: 1, 
        name: 'bKash Personal', 
        sender: 'bKash', 
        kw: '',
        regex: generateCustomRegex('You have received Tk {amount} from {sender}. Fee Tk 0.00. Balance Tk {random}. TrxID {trxid} at {random}|||Cash In Tk {amount} from {sender} successful. Fee Tk 0.00. Balance Tk {random}. TrxID {trxid} at {random}. Download App: https://bKa.sh/8app')
      }
    ];

    for (const t of defaultSmsTemplates) {
      await prisma.sms_templates.upsert({
        where: { id: t.id },
        update: { template_name: t.name, sender_id: t.sender, matching_keyword: t.kw, regex_pattern: t.regex },
        create: { id: t.id, template_name: t.name, sender_id: t.sender, matching_keyword: t.kw, regex_pattern: t.regex, is_official: 1, is_active: 1 }
      });
    }
    
    const data = await prisma.sms_templates.findMany();
    console.log(JSON.stringify(data, null, 2));
    await prisma.$disconnect();
}
seed();

