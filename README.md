# Quick CRM — Android Studio + MilesWeb

This is the original Jetpack Compose CRM UI connected to the live backend at:

`https://crm.rscc.in/api/index.php`

## Before running the app

1. Upload the contents of `server-upload/` into the document root of `crm.rscc.in`.
2. Import `server-upload/api_migration.sql` into the existing `nnvrdjjh_piko_crm` database.
3. Keep the live database password only in `includes/config.php` on MilesWeb.
4. Test `https://crm.rscc.in/api/index.php?action=health`.
5. Open this project in Android Studio with JDK 17.

## Login

Use the same email and password stored in the existing `admin_users` table.

## Security

No MySQL password, Meta token, WhatsApp token, or Gemini key is stored in the Android app.

## Build

Use **Build → Generate Signed Bundle / APK** and create your own upload keystore. The unsafe bundled keystore from the AI Studio export was removed.
