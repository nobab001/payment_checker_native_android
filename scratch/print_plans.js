const prisma = require('../backend/db/prisma');

async function main() {
  const plans = await prisma.subscription_plans.findMany();
  console.log("SUBSCRIPTION PLANS IN DB:");
  console.log(plans);
  process.exit(0);
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
