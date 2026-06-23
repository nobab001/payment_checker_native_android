const { query } = require('./db/connection');

async function migrate() {
    try {
        console.log("Renaming tables...");

        try {
            await query("RENAME TABLE system_settings TO sms_templates");
            console.log("Renamed system_settings to sms_templates");
        } catch (e) {
            console.log("Failed to rename system_settings: ", e.message);
        }

        try {
            await query("RENAME TABLE sms_settings TO sms_gateways");
            console.log("Renamed sms_settings to sms_gateways");
        } catch (e) {
            console.log("Failed to rename sms_settings: ", e.message);
        }

        try {
            await query("RENAME TABLE email_accounts TO smtp_gateways");
            console.log("Renamed email_accounts to smtp_gateways");
        } catch (e) {
            console.log("Failed to rename email_accounts: ", e.message);
        }

        console.log("Migration finished.");
        process.exit(0);
    } catch (e) {
        console.error("Migration failed:", e);
        process.exit(1);
    }
}

migrate();
