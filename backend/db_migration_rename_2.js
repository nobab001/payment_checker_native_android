const { query } = require('./db/connection');

async function migrate() {
    try {
        console.log("Renaming tables...");

        try {
            await query("RENAME TABLE system_settings TO otp_sms_templates");
            console.log("Renamed system_settings to otp_sms_templates");
        } catch (e) {
            console.log("Failed to rename system_settings: ", e.message);
        }

        console.log("Migration finished.");
        process.exit(0);
    } catch (e) {
        console.error("Migration failed:", e);
        process.exit(1);
    }
}

migrate();
