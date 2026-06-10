const cron = require('node-cron');
const { pool, query } = require('../db/connection');

// Global flag to prevent overlapping processes
let isBillingProcessing = false;
const BATCH_SIZE = 500;

/**
 * Distributed Billing Batch Processing:
 * Deducts 1 credit from up to 500 active non-free users who haven't paid yet today.
 */
async function runDistributedBilling() {
  if (isBillingProcessing) {
    console.log("[Billing Scheduler] Billing batch is already running. Skipping overlap.");
    return;
  }

  isBillingProcessing = true;
  console.log("[Billing Scheduler] Distributed Billing Batch Processing Triggered...");

  try {
    // 1. Fetch up to 500 users who are not FREE_LEVEL and don't have a deduction ledger entry for today
    const users = await query(
      `SELECT id, wallet_credits FROM users 
       WHERE account_level != 'FREE_LEVEL' 
       AND blocked = 0 AND profile_complete = 1
       AND id NOT IN (
           SELECT user_id FROM credit_deduction_ledger 
           WHERE deducted_date = CURRENT_DATE()
       )
       LIMIT ?`,
      [BATCH_SIZE]
    );

    if (!users || users.length === 0) {
      console.log("[Billing Scheduler] Today's billing is fully processed, or no eligible users remaining.");
      isBillingProcessing = false;
      return;
    }

    console.log(`[Billing Scheduler] Processing credit deductions for ${users.length} users in this batch...`);

    // 2. Loop through the batch and process deductions inside ACID transactions
    for (let user of users) {
      const connection = await pool.getConnection();
      try {
        await connection.beginTransaction();

        // Deduct 1 credit from wallet
        await connection.query(
          "UPDATE users SET wallet_credits = wallet_credits - 1 WHERE id = ?",
          [user.id]
        );

        // Record entry in deduction ledger
        await connection.query(
          "INSERT INTO credit_deduction_ledger (user_id, deducted_date) VALUES (?, CURRENT_DATE())",
          [user.id]
        );

        // If balance drops to 0 or below, automatically downgrade user to FREE_LEVEL
        if (user.wallet_credits - 1 <= 0) {
          await connection.query(
            "UPDATE users SET account_level = 'FREE_LEVEL', wallet_credits = 0 WHERE id = ?",
            [user.id]
          );
          console.log(`[Billing Scheduler] User ID ${user.id} balance is <= 0. Downgraded to FREE_LEVEL.`);
        }

        await connection.commit();
      } catch (err) {
        await connection.rollback();
        console.error(`[Billing Scheduler] Billing transaction failed and rolled back for user ID ${user.id}:`, err);
      } finally {
        connection.release();
      }
    }

    console.log(`[Billing Scheduler] Successfully completed batch billing for ${users.length} users.`);
  } catch (globalError) {
    console.error("[Billing Scheduler] Global billing engine run error:", globalError);
  } finally {
    isBillingProcessing = false;
  }
}

// Cron 1: Run every 1 minute to process users batch-by-batch
cron.schedule('*/1 * * * *', async () => {
  await runDistributedBilling();
});

// Cron 2: Self-Healing Guard check every 2 hours
cron.schedule('0 */2 * * *', async () => {
  console.log("[Billing Scheduler] Self-Healing Guard: Initiating cross-check...");
  try {
    const remaining = await query(
      `SELECT COUNT(*) as total FROM users 
       WHERE account_level != 'FREE_LEVEL' 
       AND blocked = 0 AND profile_complete = 1
       AND id NOT IN (
           SELECT user_id FROM credit_deduction_ledger 
           WHERE deducted_date = CURRENT_DATE()
       )`
    );

    const totalRemaining = remaining[0] ? remaining[0].total : 0;
    if (totalRemaining > 0) {
      console.log(`[Billing Scheduler] ⚠️ ALERT! Self-healing triggered: found ${totalRemaining} users with missing billing records for today. Starting processing...`);
      await runDistributedBilling();
    } else {
      console.log("[Billing Scheduler] Self-Healing Guard: All billing records are settled perfectly today.");
    }
  } catch (error) {
    console.error("[Billing Scheduler] Self-healing guard check error:", error);
  }
});

module.exports = { runDistributedBilling };
