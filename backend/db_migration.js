const { query } = require('./db/connection');

async function migrate() {
    try {
        console.log("Adding is_owner_device...");
        try {
            await query("ALTER TABLE registered_devices ADD COLUMN is_owner_device TINYINT(1) DEFAULT 0");
            console.log("Added is_owner_device");
        } catch (e) {
            console.log("Column is_owner_device might already exist: ", e.message);
        }

        console.log("Adding device_specific_pin...");
        try {
            await query("ALTER TABLE registered_devices ADD COLUMN device_specific_pin VARCHAR(255) DEFAULT NULL");
            console.log("Added device_specific_pin");
        } catch (e) {
            console.log("Column device_specific_pin might already exist: ", e.message);
        }

        console.log("Inserting Trial Package...");
        await query(`
            INSERT INTO subscription_plans (plan_name, price, max_sites, max_devices, duration_days) 
            VALUES ('Trial Package', 0.00, 3, 5, 3) 
            ON DUPLICATE KEY UPDATE max_sites=3, max_devices=5, duration_days=3
        `);
        console.log("Inserted Trial Package");

        console.log("Migration finished successfully.");
        process.exit(0);
    } catch (e) {
        console.error("Migration failed:", e);
        process.exit(1);
    }
}

migrate();
