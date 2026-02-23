const admin = require("firebase-admin");
const path = require("path");
const fs = require("fs");

// Check for .json first, then .js
const serviceAccountKeyJson = path.join(__dirname, "serviceAccountKey.json");
const serviceAccountKeyJs = path.join(__dirname, "serviceAccountKey.js");

if (fs.existsSync(serviceAccountKeyJson)) {
    const serviceAccount = require(serviceAccountKeyJson);
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
    console.log("Firebase Admin initialized (JSON)");
} else if (fs.existsSync(serviceAccountKeyJs)) {
    // Fallback for JS format
    require(serviceAccountKeyJs);
    // serviceAccountKey.js usually does admin.initializeApp itself or exports credentials. 
    // Assuming the user's JS file does the init based on previous context.
    // If it just exports, we would need to require it differently. 
    // Given the user pasted code, it likely does admin.initializeApp.
    console.log("Firebase Admin initialized (JS)");
} else {
    console.warn("WARNING: serviceAccountKey not found in config/ directory. Push notifications will not work.");
}

module.exports = admin;
