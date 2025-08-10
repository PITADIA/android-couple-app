const functions = require("firebase-functions");
const admin = require("firebase-admin");
const appleReceiptVerify = require("node-apple-receipt-verify");
const jwt = require("jsonwebtoken");

admin.initializeApp();

// 🛡️ SÉCURITÉ GLOBALE - Configuration centralisée
const SECURITY_CONFIG = {
  // Rate Limiting
  rateLimitingEnabled:
    functions.config().security?.rate_limiting_enabled !== "false",
  strictMode: functions.config().security?.strict_mode === "true",
  logOnly: functions.config().security?.log_only === "true",

  // Logs sécurisés
  verboseLogging: functions.config().logs?.verbose === "true", // False par défaut en prod
  logUserIds: functions.config().logs?.log_user_ids === "true", // False par défaut
  logSensitiveData: functions.config().logs?.log_sensitive === "true", // False par défaut

  // Environment
  isProduction:
    functions.config().project?.env === "production" ||
    process.env.NODE_ENV === "production",
};

/**
 * 🔒 Logger sécurisé - Évite les fuites en production
 */
function secureLog(level, message, data = null, options = {}) {
  const {
    forceLog = false, // Forcer le log même en production
    sensitive = false, // Données sensibles
    includeUserId = false, // Inclure l'ID utilisateur
  } = options;

  // En production, réduire drastiquement les logs
  if (SECURITY_CONFIG.isProduction && !forceLog) {
    // Ne logger que les erreurs critiques en production
    if (level !== "error" && level !== "warn") {
      return;
    }

    // Filtrer les données sensibles
    if (sensitive && !SECURITY_CONFIG.logSensitiveData) {
      data = "[DONNÉES SENSIBLES MASQUÉES]";
    }

    // Masquer les IDs utilisateurs
    if (includeUserId && !SECURITY_CONFIG.logUserIds) {
      message = message.replace(/[a-zA-Z0-9]{20,}/g, "[USER_ID_MASKED]");
    }
  }

  // Logger selon le niveau
  const timestamp = new Date().toISOString();
  const logMessage = `[${timestamp}] ${message}`;

  switch (level) {
    case "error":
      console.error(logMessage, data || "");
      break;
    case "warn":
      console.warn(logMessage, data || "");
      break;
    case "info":
      if (SECURITY_CONFIG.verboseLogging || !SECURITY_CONFIG.isProduction) {
        console.info(logMessage, data || "");
      }
      break;
    case "debug":
      if (SECURITY_CONFIG.verboseLogging) {
        console.log(logMessage, data || "");
      }
      break;
    default:
      console.log(logMessage, data || "");
  }
}

// Raccourcis pour faciliter l'utilisation
const logger = {
  error: (msg, data, opts) => secureLog("error", msg, data, opts),
  warn: (msg, data, opts) => secureLog("warn", msg, data, opts),
  info: (msg, data, opts) => secureLog("info", msg, data, opts),
  debug: (msg, data, opts) => secureLog("debug", msg, data, opts),

  // Logs spécialisés
  security: (msg, data) =>
    secureLog("warn", `🔒 SECURITY: ${msg}`, data, { forceLog: true }),
  audit: (msg, data) =>
    secureLog("info", `📋 AUDIT: ${msg}`, data, { forceLog: true }),
};

// Limites par fonction (appels/fenêtre en minutes)
const RATE_LIMITS = {
  validateAppleReceipt: { calls: 5, window: 1 },
  connectToPartner: { calls: 3, window: 5 },
  generatePartnerCode: { calls: 2, window: 1 },
  submitDailyQuestionResponse: { calls: 20, window: 1 },
  validatePartnerCode: { calls: 10, window: 1 },
  disconnectPartners: { calls: 2, window: 10 },
};

/**
 * 🔒 Rate Limiting Middleware - Sécurisé et Non-Disruptif
 */
async function checkRateLimit(userId, functionName, context = {}) {
  // Si rate limiting désactivé, on laisse passer (mode maintenance)
  if (!SECURITY_CONFIG.rateLimitingEnabled) {
    logger.debug(`RateLimit désactivé pour ${functionName}`);
    return;
  }

  const config = RATE_LIMITS[functionName];
  if (!config) {
    console.log(`⚠️ RateLimit: Pas de config pour ${functionName}`);
    return; // Pas de limite définie = pas de restriction
  }

  const now = new Date();
  const windowStart = new Date(now.getTime() - config.window * 60000);
  const windowKey = `${Math.floor(now.getTime() / (config.window * 60000))}`;

  try {
    // Utiliser MemoryStore temporaire pour éviter les coûts Firestore
    const rateLimitDoc = admin
      .firestore()
      .collection("rate_limits")
      .doc(`${userId}_${functionName}_${windowKey}`);

    // 🔥 CORRECTION: Transaction atomique pour éviter condition de course
    let currentCalls = 0;
    await admin.firestore().runTransaction(async (transaction) => {
      const doc = await transaction.get(rateLimitDoc);
      currentCalls = doc.exists ? doc.data().count || 0 : 0;

      // Vérifier AVANT l'incrément
      if (currentCalls >= config.calls) {
        // La limite sera gérée après la transaction
        return;
      }

      // Incrémenter atomiquement
      transaction.set(
        rateLimitDoc,
        {
          count: currentCalls + 1,
          lastCall: now,
          userId: userId,
          function: functionName,
          window: config.window,
        },
        { merge: true }
      );
    });

    // Vérifier la limite
    if (currentCalls >= config.calls) {
      const message = `Rate limit dépassé pour ${functionName}: ${currentCalls}/${config.calls} en ${config.window}min`;

      // Mode log uniquement (pour test en prod sans casser)
      if (SECURITY_CONFIG.logOnly) {
        logger.security(`Rate limit dépassé (LOG ONLY) - ${functionName}`, {
          calls: currentCalls,
          limit: config.calls,
          window: config.window,
        });

        // Logger l'événement pour surveillance (sans user ID en production)
        await admin
          .firestore()
          .collection("security_events")
          .add({
            type: "rate_limit_exceeded",
            userId: SECURITY_CONFIG.logUserIds ? userId : "[MASKED]",
            function: functionName,
            count: currentCalls,
            limit: config.calls,
            window: config.window,
            timestamp: now,
            action: "logged_only",
            userAgent: context.rawRequest?.headers?.["user-agent"] || "unknown",
          });

        return; // On laisse passer mais on log
      }

      // Mode strict : rejeter la requête
      console.error(`❌ RateLimit: ${message}`);

      // Logger l'événement
      await admin.firestore().collection("security_events").add({
        type: "rate_limit_exceeded",
        userId: userId,
        function: functionName,
        count: currentCalls,
        limit: config.calls,
        window: config.window,
        timestamp: now,
        action: "blocked",
      });

      throw new functions.https.HttpsError(
        "resource-exhausted",
        `Trop de requêtes. Veuillez attendre ${config.window} minute(s).`
      );
    }

    console.log(
      `✅ RateLimit: ${functionName} - ${currentCalls + 1}/${config.calls}`
    );
  } catch (error) {
    // En cas d'erreur du rate limiting, on laisse passer pour ne pas casser l'app
    if (!SECURITY_CONFIG.strictMode) {
      console.warn(
        `⚠️ RateLimit: Erreur non bloquante pour ${functionName}:`,
        error.message
      );
      return;
    }
    throw error;
  }
}

/**
 * 🧹 Nettoyage automatique des anciens rate limits
 */
async function cleanupOldRateLimits() {
  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);

  try {
    const oldLimits = await admin
      .firestore()
      .collection("rate_limits")
      .where("lastCall", "<", oneDayAgo)
      .limit(100)
      .get();

    const batch = admin.firestore().batch();
    oldLimits.docs.forEach((doc) => batch.delete(doc.ref));

    if (oldLimits.size > 0) {
      await batch.commit();
      console.log(
        `🧹 Nettoyage: ${oldLimits.size} anciens rate limits supprimés`
      );
    }
  } catch (error) {
    console.warn("⚠️ Erreur nettoyage rate limits:", error.message);
  }
}

// Configuration App Store Connect API (avec valeurs par défaut)
const APP_STORE_CONNECT_CONFIG = {
  keyId: functions.config().apple?.key_id || "",
  issuerId: functions.config().apple?.issuer_id || "",
  bundleId: "com.lyes.love2love",
  privateKey: functions.config().apple?.private_key || "",
  environment: "production", // Configuration pour production
};

// Produits d'abonnement supportés
const SUBSCRIPTION_PRODUCTS = {
  WEEKLY: "com.lyes.love2love.subscription.weekly",
  MONTHLY: "com.lyes.love2love.subscription.monthly",
};

/**
 * Valider un reçu d'achat Apple
 */
exports.validateAppleReceipt = functions.https.onCall(async (data, context) => {
  try {
    // 🛡️ RATE LIMITING - Protection contre les abus
    if (context.auth) {
      await checkRateLimit(context.auth.uid, "validateAppleReceipt", context);
    }

    console.log("🔥 validateAppleReceipt: Début de la validation");

    const { receiptData, productId } = data;

    if (!receiptData) {
      console.log("🔥 validateAppleReceipt: Données de reçu manquantes");
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Données de reçu manquantes"
      );
    }

    // Vérifier que le produit est supporté
    const supportedProducts = Object.values(SUBSCRIPTION_PRODUCTS);
    if (!supportedProducts.includes(productId)) {
      console.log("🔥 validateAppleReceipt: Produit non supporté:", productId);
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Produit d'abonnement non supporté"
      );
    }

    console.log(
      "🔥 validateAppleReceipt: Validation du reçu pour le produit:",
      productId
    );

    // Vérifier la configuration
    const sharedSecret = functions.config().apple?.shared_secret || "";
    const environment = APP_STORE_CONNECT_CONFIG.environment;

    console.log("🔥 validateAppleReceipt: Configuration:");
    console.log("🔥 validateAppleReceipt: - Environment:", environment);
    console.log(
      "🔥 validateAppleReceipt: - Shared Secret configuré:",
      sharedSecret ? "OUI" : "NON"
    );
    console.log(
      "🔥 validateAppleReceipt: - Shared Secret longueur:",
      sharedSecret.length
    );

    if (!sharedSecret) {
      console.log("🔥 validateAppleReceipt: ERREUR - Shared Secret manquant!");
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Configuration Apple manquante - Shared Secret requis"
      );
    }

    // Configuration pour la validation
    console.log(
      "🔥 validateAppleReceipt: Configuration de node-apple-receipt-verify"
    );
    appleReceiptVerify.config({
      secret: sharedSecret,
      environment: ["sandbox", "production"], // CORRECTION: Essayer les deux environnements
      verbose: true,
      ignoreExpired: false,
      extended: true,
    });

    // Valider le reçu avec Apple
    console.log("🔥 validateAppleReceipt: Envoi de la requête à Apple...");
    const result = await appleReceiptVerify.validate({
      receipt: receiptData,
    });

    console.log(
      "🔥 validateAppleReceipt: Résultat de la validation:",
      result ? result.length : "null"
    );

    if (result && Array.isArray(result)) {
      console.log("🔥 validateAppleReceipt: Détails du résultat:");
      result.forEach((item, index) => {
        console.log(`🔥 validateAppleReceipt: - Item ${index}:`, {
          productId: item.productId,
          transactionId: item.transactionId,
          purchaseDate: item.purchaseDate,
        });
      });
    }

    if (result && result.length > 0) {
      // Reçu valide
      console.log(
        "🔥 validateAppleReceipt: Reçu valide, traitement des achats"
      );

      // Trouver l'achat correspondant au produit
      const purchase = result.find((item) => item.productId === productId);

      if (purchase) {
        console.log("🔥 validateAppleReceipt: Achat trouvé:", {
          productId: purchase.productId,
          transactionId: purchase.transactionId,
          originalTransactionId: purchase.originalTransactionId,
        });

        const subscriptionData = {
          isSubscribed: true,
          subscriptionType: productId,
          purchaseDate: new Date(purchase.purchaseDate),
          expiresDate: purchase.expirationDate
            ? new Date(purchase.expirationDate)
            : null,
          transactionId: purchase.transactionId,
          originalTransactionId: purchase.originalTransactionId,
          lastValidated: admin.firestore.FieldValue.serverTimestamp(),
        };

        // Avec le nouveau flux, l'utilisateur est toujours authentifié avant le paiement
        if (!context.auth) {
          console.log(
            "🔥 validateAppleReceipt: ERREUR - Utilisateur non authentifié"
          );
          throw new functions.https.HttpsError(
            "unauthenticated",
            "Utilisateur non authentifié - l'authentification doit précéder le paiement"
          );
        }

        const userId = context.auth.uid;
        const userRef = admin.firestore().collection("users").doc(userId);

        // CORRECTION: Mise à jour compatible avec le modèle Swift
        await userRef.update({
          isSubscribed: true,
          subscriptionDetails: subscriptionData, // Optionnel : pour le tracking
        });

        console.log(
          "🔥 validateAppleReceipt: Abonnement mis à jour pour l'utilisateur authentifié:",
          userId
        );

        return {
          success: true,
          subscription: subscriptionData,
        };
      } else {
        console.log("🔥 validateAppleReceipt: Produit non trouvé dans le reçu");
        console.log("🔥 validateAppleReceipt: Produit recherché:", productId);
        console.log(
          "🔥 validateAppleReceipt: Produits disponibles:",
          result.map((item) => item.productId)
        );
        throw new functions.https.HttpsError(
          "not-found",
          "Produit non trouvé dans le reçu"
        );
      }
    } else {
      console.log("🔥 validateAppleReceipt: Reçu invalide ou vide");
      console.log(
        "🔥 validateAppleReceipt: Résultat complet:",
        JSON.stringify(result, null, 2)
      );
      throw new functions.https.HttpsError("invalid-argument", "Reçu invalide");
    }
  } catch (error) {
    console.error("🔥 validateAppleReceipt: Erreur détaillée:", error);
    console.error("🔥 validateAppleReceipt: Type d'erreur:", typeof error);
    console.error("🔥 validateAppleReceipt: Message:", error.message);
    console.error("🔥 validateAppleReceipt: Stack:", error.stack);

    // NOUVEAU: Logging plus détaillé pour identifier la cause
    if (error.code) {
      console.error("🔥 validateAppleReceipt: Code d'erreur:", error.code);
    }
    if (error.status) {
      console.error("🔥 validateAppleReceipt: Status HTTP:", error.status);
    }
    if (error.response) {
      console.error(
        "🔥 validateAppleReceipt: Réponse Apple:",
        JSON.stringify(error.response, null, 2)
      );
    }

    // Envoyer des erreurs spécifiques selon le type
    if (error.message && error.message.includes("21007")) {
      // Erreur sandbox vs production
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Environnement Apple incorrect - vérifiez sandbox/production"
      );
    } else if (error.message && error.message.includes("receipt")) {
      // Problème de reçu
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Reçu Apple invalide ou corrompu"
      );
    } else if (error.message && error.message.includes("auth")) {
      // Problème d'authentification
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Problème d'authentification utilisateur"
      );
    } else {
      // Erreur générique avec plus de détails
      throw new functions.https.HttpsError(
        "internal",
        `Erreur validation: ${error.message || "Erreur inconnue"}`
      );
    }
  }
});

/**
 * Webhook pour les notifications serveur-à-serveur d'Apple
 */
exports.appleWebhook = functions.https.onRequest(async (req, res) => {
  try {
    console.log("🔥 appleWebhook: Notification reçue d'Apple");

    if (req.method !== "POST") {
      return res.status(405).send("Method Not Allowed");
    }

    const notification = req.body;
    console.log(
      "🔥 appleWebhook: Type de notification:",
      notification.notification_type
    );

    // Vérifier la signature (optionnel mais recommandé)
    // TODO: Implémenter la vérification de signature JWT

    const notificationType = notification.notification_type;
    const receiptData = notification.unified_receipt;

    switch (notificationType) {
      case "INITIAL_BUY":
      case "DID_RENEW":
        console.log("🔥 appleWebhook: Nouvel achat ou renouvellement");
        await handleSubscriptionActivation(receiptData);
        break;

      case "DID_FAIL_TO_RENEW":
      case "EXPIRED":
        console.log("🔥 appleWebhook: Échec de renouvellement ou expiration");
        await handleSubscriptionExpiration(receiptData);
        break;

      case "DID_CANCEL":
        console.log("🔥 appleWebhook: Annulation d'abonnement");
        await handleSubscriptionCancellation(receiptData);
        break;

      default:
        console.log(
          "🔥 appleWebhook: Type de notification non géré:",
          notificationType
        );
    }

    res.status(200).send("OK");
  } catch (error) {
    console.error("🔥 appleWebhook: Erreur:", error);
    res.status(500).send("Internal Server Error");
  }
});

/**
 * Gérer l'activation d'un abonnement
 */
async function handleSubscriptionActivation(receiptData) {
  try {
    // Trouver l'utilisateur basé sur l'original_transaction_id
    const latestReceiptInfo = receiptData.latest_receipt_info || [];

    for (const purchase of latestReceiptInfo) {
      const originalTransactionId = purchase.original_transaction_id;

      // Chercher l'utilisateur avec cet ID de transaction
      const usersSnapshot = await admin
        .firestore()
        .collection("users")
        .where(
          "subscriptionDetails.originalTransactionId",
          "==",
          originalTransactionId
        )
        .get();

      if (!usersSnapshot.empty) {
        const userDoc = usersSnapshot.docs[0];
        const subscriptionData = {
          isSubscribed: true,
          subscriptionType: purchase.product_id,
          purchaseDate: new Date(parseInt(purchase.purchase_date_ms)),
          expiresDate: purchase.expires_date_ms
            ? new Date(parseInt(purchase.expires_date_ms))
            : null,
          transactionId: purchase.transaction_id,
          originalTransactionId: purchase.original_transaction_id,
          lastValidated: admin.firestore.FieldValue.serverTimestamp(),
        };

        // CORRECTION: Mise à jour compatible avec le modèle Swift
        await userDoc.ref.update({
          isSubscribed: true,
          subscriptionDetails: subscriptionData,
        });

        console.log(
          "🔥 handleSubscriptionActivation: Abonnement activé pour:",
          userDoc.id
        );
      }
    }
  } catch (error) {
    console.error("🔥 handleSubscriptionActivation: Erreur:", error);
  }
}

/**
 * Gérer l'expiration d'un abonnement
 */
async function handleSubscriptionExpiration(receiptData) {
  try {
    const latestReceiptInfo = receiptData.latest_receipt_info || [];

    for (const purchase of latestReceiptInfo) {
      const originalTransactionId = purchase.original_transaction_id;

      const usersSnapshot = await admin
        .firestore()
        .collection("users")
        .where(
          "subscriptionDetails.originalTransactionId",
          "==",
          originalTransactionId
        )
        .get();

      if (!usersSnapshot.empty) {
        const userDoc = usersSnapshot.docs[0];

        // CORRECTION: Mise à jour compatible avec le modèle Swift
        await userDoc.ref.update({
          isSubscribed: false,
          "subscriptionDetails.lastValidated":
            admin.firestore.FieldValue.serverTimestamp(),
        });

        console.log(
          "🔥 handleSubscriptionExpiration: Abonnement expiré pour:",
          userDoc.id
        );
      }
    }
  } catch (error) {
    console.error("🔥 handleSubscriptionExpiration: Erreur:", error);
  }
}

/**
 * Gérer l'annulation d'un abonnement
 */
async function handleSubscriptionCancellation(receiptData) {
  try {
    const latestReceiptInfo = receiptData.latest_receipt_info || [];

    for (const purchase of latestReceiptInfo) {
      const originalTransactionId = purchase.original_transaction_id;

      const usersSnapshot = await admin
        .firestore()
        .collection("users")
        .where(
          "subscriptionDetails.originalTransactionId",
          "==",
          originalTransactionId
        )
        .get();

      if (!usersSnapshot.empty) {
        const userDoc = usersSnapshot.docs[0];

        // CORRECTION: Mise à jour compatible avec le modèle Swift
        await userDoc.ref.update({
          isSubscribed: false,
          "subscriptionDetails.cancelledDate":
            admin.firestore.FieldValue.serverTimestamp(),
          "subscriptionDetails.lastValidated":
            admin.firestore.FieldValue.serverTimestamp(),
        });

        console.log(
          "🔥 handleSubscriptionCancellation: Abonnement annulé pour:",
          userDoc.id
        );
      }
    }
  } catch (error) {
    console.error("🔥 handleSubscriptionCancellation: Erreur:", error);
  }
}

/**
 * Vérifier le statut d'abonnement d'un utilisateur
 */
exports.checkSubscriptionStatus = functions.https.onCall(
  async (data, context) => {
    try {
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const userId = context.auth.uid;
      const userDoc = await admin
        .firestore()
        .collection("users")
        .doc(userId)
        .get();

      if (!userDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur non trouvé"
        );
      }

      const userData = userDoc.data();
      const subscriptionDetails = userData.subscriptionDetails || {};

      // Vérifier si l'abonnement est encore valide
      const now = new Date();
      const expiresDate = subscriptionDetails.expiresDate
        ? subscriptionDetails.expiresDate.toDate()
        : null;

      const isActive =
        userData.isSubscribed && (!expiresDate || expiresDate > now);

      return {
        isSubscribed: isActive,
        subscription: subscriptionDetails,
      };
    } catch (error) {
      console.error("🔥 checkSubscriptionStatus: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

/**
 * Supprimer un compte utilisateur et toutes ses données associées
 */
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
  try {
    console.log("🔥 deleteUserAccount: Début de la suppression du compte");

    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const userId = context.auth.uid;
    console.log(
      "🔥 deleteUserAccount: Suppression pour l'utilisateur:",
      userId
    );

    // Étape 1: Gérer la déconnexion partenaire proprement
    try {
      const userDoc = await admin
        .firestore()
        .collection("users")
        .doc(userId)
        .get();
      if (userDoc.exists) {
        const userData = userDoc.data();
        const partnerCode = userData.partnerCode;
        const partnerId = userData.partnerId;

        // 1. Si l'utilisateur a un code partenaire ET un partenaire connecté
        if (partnerCode) {
          const codeDoc = await admin
            .firestore()
            .collection("partnerCodes")
            .doc(partnerCode)
            .get();

          if (codeDoc.exists) {
            const codeData = codeDoc.data();

            // Si quelqu'un est connecté à ce code, le déconnecter proprement
            if (codeData.connectedPartnerId) {
              console.log(
                "🔗 deleteUserAccount: Déconnexion du partenaire connecté:",
                codeData.connectedPartnerId
              );

              // 🔧 CORRECTION: Récupérer d'abord les données du partenaire pour vérifier son abonnement
              const connectedPartnerDoc = await admin
                .firestore()
                .collection("users")
                .doc(codeData.connectedPartnerId)
                .get();

              if (connectedPartnerDoc.exists) {
                const connectedPartnerData = connectedPartnerDoc.data();

                // Préparer les mises à jour pour le partenaire connecté
                const connectedPartnerUpdate = {
                  partnerId: admin.firestore.FieldValue.delete(),
                  partnerConnectedAt: admin.firestore.FieldValue.delete(),
                  connectedPartnerCode: admin.firestore.FieldValue.delete(),
                  connectedPartnerId: admin.firestore.FieldValue.delete(),
                  connectedAt: admin.firestore.FieldValue.delete(),
                  subscriptionInheritedFrom:
                    admin.firestore.FieldValue.delete(),
                  subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
                  subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
                  subscriptionSharedAt: admin.firestore.FieldValue.delete(),
                };

                // 🔧 CORRECTION: Vérifier si le partenaire connecté avait un abonnement hérité du compte supprimé
                const connectedPartnerSubscriptionType =
                  connectedPartnerData.subscriptionType;
                const connectedPartnerHadInheritedSubscription = Boolean(
                  connectedPartnerSubscriptionType === "shared_from_partner" ||
                    connectedPartnerData.subscriptionInheritedFrom === userId ||
                    connectedPartnerData.subscriptionSharedFrom === userId
                );

                console.log(
                  "🔗 deleteUserAccount: Partenaire connecté avait abonnement hérité:",
                  connectedPartnerHadInheritedSubscription
                );

                // Seulement désactiver l'abonnement si il était vraiment hérité
                if (connectedPartnerHadInheritedSubscription) {
                  connectedPartnerUpdate.isSubscribed = false;
                  connectedPartnerUpdate.subscriptionType =
                    admin.firestore.FieldValue.delete();
                  console.log(
                    "🔗 deleteUserAccount: Désactivation abonnement hérité pour le partenaire connecté:",
                    codeData.connectedPartnerId
                  );
                }

                // Appliquer les mises à jour
                await admin
                  .firestore()
                  .collection("users")
                  .doc(codeData.connectedPartnerId)
                  .update(connectedPartnerUpdate);

                console.log(
                  "✅ deleteUserAccount: Partenaire connecté mis à jour:",
                  codeData.connectedPartnerId
                );
              }
            }

            // Maintenant supprimer le code partenaire
            await admin
              .firestore()
              .collection("partnerCodes")
              .doc(partnerCode)
              .delete();
            console.log(
              "✅ deleteUserAccount: Code partenaire supprimé:",
              partnerCode
            );
          }
        }

        // 2. Si l'utilisateur était connecté à un code partenaire d'un autre utilisateur
        const connectedPartnerCode = userData.connectedPartnerCode;
        if (connectedPartnerCode) {
          await admin
            .firestore()
            .collection("partnerCodes")
            .doc(connectedPartnerCode)
            .update({
              connectedPartnerId: null,
              connectedAt: null,
            });
          console.log(
            "✅ deleteUserAccount: Déconnexion du code partenaire:",
            connectedPartnerCode
          );

          // 🔧 CORRECTION: Mettre à jour l'autre utilisateur (propriétaire du code) et vérifier son abonnement
          if (partnerId) {
            // Récupérer les données du propriétaire du code pour vérifier son abonnement
            const partnerDoc = await admin
              .firestore()
              .collection("users")
              .doc(partnerId)
              .get();

            if (partnerDoc.exists) {
              const partnerData = partnerDoc.data();

              // Préparer les mises à jour pour le propriétaire du code
              const partnerUpdate = {
                partnerId: admin.firestore.FieldValue.delete(),
                partnerConnectedAt: admin.firestore.FieldValue.delete(),
                subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
                subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
                subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
                subscriptionSharedAt: admin.firestore.FieldValue.delete(),
              };

              // 🔧 CORRECTION: Vérifier si le propriétaire du code avait un abonnement hérité du compte supprimé
              const partnerSubscriptionType = partnerData.subscriptionType;
              const partnerHadInheritedSubscription =
                partnerSubscriptionType === "shared_from_partner" ||
                partnerData.subscriptionInheritedFrom === userId ||
                partnerData.subscriptionSharedFrom === userId;

              if (partnerHadInheritedSubscription) {
                partnerUpdate.isSubscribed = false;
                partnerUpdate.subscriptionType =
                  admin.firestore.FieldValue.delete();
                console.log(
                  "🔗 deleteUserAccount: Désactivation abonnement hérité pour le propriétaire du code:",
                  partnerId
                );
              }

              await admin
                .firestore()
                .collection("users")
                .doc(partnerId)
                .update(partnerUpdate);

              console.log(
                "✅ deleteUserAccount: Partenaire mis à jour:",
                partnerId
              );
            }
          }
        }
      }
    } catch (error) {
      console.error(
        "❌ deleteUserAccount: Erreur libération code partenaire:",
        error
      );
      // Ne pas faire échouer la suppression pour cela
    }

    // 🔧 NOUVEAU: Vérifier que tous les partenaires ont été déconnectés proprement
    console.log(
      "🔗 deleteUserAccount: Vérification finale des connexions partenaires..."
    );
    try {
      // Chercher tout utilisateur qui a encore ce userId comme partnerId
      const orphanedPartnersSnapshot = await admin
        .firestore()
        .collection("users")
        .where("partnerId", "==", userId)
        .get();

      for (const doc of orphanedPartnersSnapshot.docs) {
        const partnerData = doc.data();
        console.log(
          "🔗 deleteUserAccount: Nettoyage partenaire orphelin:",
          doc.id
        );

        // Nettoyer complètement ce partenaire
        const cleanupUpdate = {
          partnerId: admin.firestore.FieldValue.delete(),
          partnerConnectedAt: admin.firestore.FieldValue.delete(),
          connectedPartnerCode: admin.firestore.FieldValue.delete(),
          connectedPartnerId: admin.firestore.FieldValue.delete(),
          connectedAt: admin.firestore.FieldValue.delete(),
          subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
          subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
          subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
          subscriptionSharedAt: admin.firestore.FieldValue.delete(),
        };

        // Vérifier si ce partenaire avait un abonnement hérité
        const partnerSubscriptionType = partnerData.subscriptionType;
        const partnerHadInheritedSubscription = Boolean(
          partnerSubscriptionType === "shared_from_partner" ||
            partnerData.subscriptionInheritedFrom === userId ||
            partnerData.subscriptionSharedFrom === userId
        );

        if (partnerHadInheritedSubscription) {
          cleanupUpdate.isSubscribed = false;
          cleanupUpdate.subscriptionType = admin.firestore.FieldValue.delete();
          console.log(
            "🔗 deleteUserAccount: Désactivation abonnement hérité pour partenaire orphelin:",
            doc.id
          );
        }

        await doc.ref.update(cleanupUpdate);
        console.log(
          "✅ deleteUserAccount: Partenaire orphelin nettoyé:",
          doc.id
        );
      }

      console.log(
        `✅ deleteUserAccount: ${orphanedPartnersSnapshot.docs.length} partenaires orphelins nettoyés`
      );
    } catch (error) {
      console.error(
        "❌ deleteUserAccount: Erreur nettoyage partenaires orphelins:",
        error
      );
      // Continuer malgré l'erreur
    }

    // Étape 2: Supprimer le document utilisateur de Firestore
    try {
      await admin.firestore().collection("users").doc(userId).delete();
      console.log(
        "✅ deleteUserAccount: Document utilisateur supprimé de Firestore"
      );
    } catch (error) {
      console.error(
        "❌ deleteUserAccount: Erreur suppression Firestore:",
        error
      );
      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de la suppression des données utilisateur"
      );
    }

    // Étape 3: Supprimer toutes les données associées (abonnements temporaires, etc.)
    try {
      // Supprimer les abonnements temporaires s'il y en a
      const tempSubscriptionsSnapshot = await admin
        .firestore()
        .collection("tempSubscriptions")
        .where("userId", "==", userId)
        .get();

      const deletePromises = tempSubscriptionsSnapshot.docs.map((doc) =>
        doc.ref.delete()
      );
      await Promise.all(deletePromises);

      console.log(
        "✅ deleteUserAccount: Données temporaires supprimées:",
        deletePromises.length
      );
    } catch (error) {
      console.error(
        "❌ deleteUserAccount: Erreur suppression données temporaires:",
        error
      );
      // Ne pas faire échouer la suppression pour cela
    }

    // Étape 4: Supprimer le compte Firebase Auth
    try {
      await admin.auth().deleteUser(userId);
      console.log("✅ deleteUserAccount: Compte Firebase Auth supprimé");
    } catch (error) {
      console.error(
        "❌ deleteUserAccount: Erreur suppression Firebase Auth:",
        error
      );
      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de la suppression du compte d'authentification"
      );
    }

    console.log(
      "✅ deleteUserAccount: Suppression du compte terminée avec succès"
    );

    return {
      success: true,
      message: "Compte supprimé avec succès",
    };
  } catch (error) {
    console.error("🔥 deleteUserAccount: Erreur détaillée:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Créer un code partenaire pour un utilisateur
 */
exports.createPartnerCode = functions.https.onCall(async (data, context) => {
  try {
    console.log("🔥 createPartnerCode: Début de la création du code");

    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const userId = context.auth.uid;
    console.log("🔥 createPartnerCode: Création pour l'utilisateur:", userId);

    // Vérifier si l'utilisateur a déjà un code
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const userData = userDoc.data();
    if (userData.partnerCode) {
      console.log(
        "🔥 createPartnerCode: L'utilisateur a déjà un code:",
        userData.partnerCode
      );
      return {
        success: true,
        code: userData.partnerCode,
        message: "Code existant retourné",
      };
    }

    // Générer un nouveau code unique
    let newCode;
    let attempts = 0;
    const maxAttempts = 10;

    do {
      newCode = Math.floor(10000000 + Math.random() * 90000000).toString();
      attempts++;

      const existingCode = await admin
        .firestore()
        .collection("partnerCodes")
        .doc(newCode)
        .get();
      if (!existingCode.exists) {
        break;
      }

      if (attempts >= maxAttempts) {
        throw new functions.https.HttpsError(
          "internal",
          "Impossible de générer un code unique"
        );
      }
    } while (true);

    // Créer le code dans Firestore
    await admin.firestore().collection("partnerCodes").doc(newCode).set({
      userId: userId,
      isActive: true,
      connectedPartnerId: null,
      connectedAt: null,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Mettre à jour l'utilisateur
    await admin.firestore().collection("users").doc(userId).update({
      partnerCode: newCode,
    });

    console.log("✅ createPartnerCode: Code créé avec succès:", newCode);

    return {
      success: true,
      code: newCode,
      message: "Code créé avec succès",
    };
  } catch (error) {
    console.error("🔥 createPartnerCode: Erreur détaillée:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Connecter un utilisateur à un partenaire via un code
 */
exports.connectToPartner = functions.https.onCall(async (data, context) => {
  try {
    console.log("🔥 connectToPartner: Début de la connexion");

    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const { partnerCode } = data;
    if (!partnerCode) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Code partenaire requis"
      );
    }

    const userId = context.auth.uid;
    console.log("🔥 connectToPartner: Connexion pour l'utilisateur:", userId);
    console.log("🔥 connectToPartner: Code partenaire:", partnerCode);

    // Vérifier que le code existe et est valide
    const codeDoc = await admin
      .firestore()
      .collection("partnerCodes")
      .doc(partnerCode)
      .get();
    if (!codeDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Code partenaire invalide"
      );
    }

    const codeData = codeDoc.data();

    // Vérifications de sécurité
    if (!codeData.isActive) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Code partenaire inactif"
      );
    }

    // 🛡️ CONFORMITÉ APPLE : Vérifier expiration code (24h max)
    if (codeData.expiresAt && codeData.expiresAt.toDate() < new Date()) {
      // Désactiver le code expiré
      await admin
        .firestore()
        .collection("partnerCodes")
        .doc(partnerCode)
        .update({
          isActive: false,
          deactivatedAt: admin.firestore.FieldValue.serverTimestamp(),
          deactivationReason: "expired_24h",
        });

      throw new functions.https.HttpsError(
        "deadline-exceeded",
        "Code partenaire expiré (24h max). Demandez un nouveau code à votre partenaire."
      );
    }

    if (codeData.userId === userId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Vous ne pouvez pas utiliser votre propre code"
      );
    }

    if (codeData.connectedPartnerId) {
      throw new functions.https.HttpsError(
        "already-exists",
        "Ce code est déjà utilisé par un autre partenaire"
      );
    }

    // Vérifier que l'utilisateur n'est pas déjà connecté à un partenaire
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const userData = userDoc.data();
    if (userData.connectedPartnerCode) {
      throw new functions.https.HttpsError(
        "already-exists",
        "Vous êtes déjà connecté à un partenaire"
      );
    }

    // Récupérer les données du partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(codeData.userId)
      .get();
    if (!partnerDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Partenaire non trouvé"
      );
    }

    const partnerData = partnerDoc.data();

    // Vérifier si le partenaire a un abonnement actif
    const partnerSubscription = partnerData.subscription || {};
    const hasActiveSubscription = partnerSubscription.isSubscribed === true;

    // Effectuer la connexion
    const batch = admin.firestore().batch();

    // Mettre à jour le code partenaire
    batch.update(
      admin.firestore().collection("partnerCodes").doc(partnerCode),
      {
        connectedPartnerId: userId,
        connectedAt: admin.firestore.FieldValue.serverTimestamp(),
      }
    );

    // Mettre à jour l'utilisateur
    const userUpdate = {
      connectedPartnerCode: partnerCode,
      connectedPartnerId: codeData.userId,
      connectedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // Si le partenaire a un abonnement actif, l'hériter
    if (hasActiveSubscription) {
      userUpdate.subscription = {
        ...partnerSubscription,
        subscriptionType: "inherited",
        inheritedFrom: codeData.userId,
        inheritedAt: admin.firestore.FieldValue.serverTimestamp(),
      };
    }

    batch.update(admin.firestore().collection("users").doc(userId), userUpdate);

    await batch.commit();

    console.log("✅ connectToPartner: Connexion réussie");
    console.log(
      "✅ connectToPartner: Abonnement hérité:",
      hasActiveSubscription
    );

    return {
      success: true,
      hasInheritedSubscription: hasActiveSubscription,
      partnerInfo: {
        id: codeData.userId,
        // Ne pas exposer d'informations sensibles
      },
      message: hasActiveSubscription
        ? "Connexion réussie avec héritage d'abonnement"
        : "Connexion réussie",
    };
  } catch (error) {
    console.error("🔥 connectToPartner: Erreur détaillée:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Déconnecter deux partenaires sans supprimer les comptes
 */
exports.disconnectPartners = functions.https.onCall(async (data, context) => {
  try {
    console.log("🔗 disconnectPartners: Début déconnexion partenaires");
    console.log("🔗 disconnectPartners: Version avec logs détaillés - v2.0");

    if (!context.auth) {
      console.log("❌ disconnectPartners: Utilisateur non authentifié");
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    console.log(
      "🔗 disconnectPartners: Utilisateur authentifié:",
      currentUserId
    );

    // Récupérer les données de l'utilisateur actuel
    console.log(
      "🔗 disconnectPartners: Récupération données utilisateur actuel"
    );
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();

    if (!currentUserDoc.exists) {
      console.log("❌ disconnectPartners: Utilisateur actuel non trouvé");
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const currentUserData = currentUserDoc.data();
    console.log("🔗 disconnectPartners: Données utilisateur récupérées:", {
      hasPartnerId: !!currentUserData.partnerId,
      subscriptionType: currentUserData.subscriptionType,
      isSubscribed: currentUserData.isSubscribed,
    });

    const partnerId = currentUserData.partnerId;
    const partnerCode = currentUserData.partnerCode;
    const connectedPartnerCode = currentUserData.connectedPartnerCode;

    if (!partnerId) {
      console.log("❌ disconnectPartners: Aucun partenaire connecté");
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Aucun partenaire connecté"
      );
    }

    console.log("🔗 disconnectPartners: Partenaire à déconnecter:", partnerId);
    console.log("🔗 disconnectPartners: Codes:", {
      partnerCode: partnerCode || "aucun",
      connectedPartnerCode: connectedPartnerCode || "aucun",
    });

    // Effectuer la déconnexion dans une transaction
    console.log("🔗 disconnectPartners: Démarrage de la transaction");
    await admin.firestore().runTransaction(async (transaction) => {
      console.log(
        "🔗 disconnectPartners: DANS la transaction - Récupération données partenaire"
      );

      // 🔧 CORRECTION: Récupérer les données du partenaire DANS la transaction
      const partnerDoc = await transaction.get(
        admin.firestore().collection("users").doc(partnerId)
      );

      console.log(
        "🔗 disconnectPartners: Partenaire doc récupéré, existe?",
        partnerDoc.exists
      );

      if (!partnerDoc.exists) {
        console.log(
          "❌ disconnectPartners: TRANSACTION - Partenaire non trouvé"
        );
        throw new functions.https.HttpsError(
          "not-found",
          "Partenaire non trouvé"
        );
      }

      const partnerData = partnerDoc.data();
      console.log("🔗 disconnectPartners: TRANSACTION - Données partenaire:", {
        hasPartnerId: !!partnerData.partnerId,
        subscriptionType: partnerData.subscriptionType,
        isSubscribed: partnerData.isSubscribed,
      });
      // 1. Mettre à jour l'utilisateur actuel
      console.log(
        "🔗 disconnectPartners: TRANSACTION - Préparation mise à jour utilisateur actuel"
      );
      const currentUserUpdate = {
        partnerId: admin.firestore.FieldValue.delete(),
        partnerConnectedAt: admin.firestore.FieldValue.delete(),
        connectedPartnerCode: admin.firestore.FieldValue.delete(),
        connectedPartnerId: admin.firestore.FieldValue.delete(),
        connectedAt: admin.firestore.FieldValue.delete(),
        subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
        subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
        subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
        subscriptionSharedAt: admin.firestore.FieldValue.delete(),
      };

      // 🔧 CORRECTION: Désactiver l'abonnement si il était hérité (vérifier les deux types de champs)
      const currentSubscriptionType = currentUserData.subscriptionType;
      const currentHasInheritedSubscription = Boolean(
        currentSubscriptionType === "shared_from_partner" ||
          currentUserData.subscriptionInheritedFrom ||
          currentUserData.subscriptionSharedFrom
      );

      console.log(
        "🔗 disconnectPartners: TRANSACTION - Vérification abonnement utilisateur actuel:",
        {
          subscriptionType: currentSubscriptionType,
          hasInheritedSubscription: currentHasInheritedSubscription,
          willDeactivate: currentHasInheritedSubscription,
        }
      );

      if (currentHasInheritedSubscription) {
        currentUserUpdate.isSubscribed = false;
        currentUserUpdate.subscriptionType =
          admin.firestore.FieldValue.delete();
        console.log(
          "🔗 disconnectPartners: TRANSACTION - Désactivation abonnement hérité pour utilisateur actuel"
        );
      }

      console.log(
        "🔗 disconnectPartners: TRANSACTION - Application mise à jour utilisateur actuel"
      );
      transaction.update(currentUserDoc.ref, currentUserUpdate);

      // 2. 🔧 CORRECTION: Mettre à jour le partenaire avec vérification de son abonnement
      console.log(
        "🔗 disconnectPartners: TRANSACTION - Préparation mise à jour partenaire"
      );
      const partnerUserUpdate = {
        partnerId: admin.firestore.FieldValue.delete(),
        partnerConnectedAt: admin.firestore.FieldValue.delete(),
        connectedPartnerCode: admin.firestore.FieldValue.delete(),
        connectedPartnerId: admin.firestore.FieldValue.delete(),
        connectedAt: admin.firestore.FieldValue.delete(),
        subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
        subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
        subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
        subscriptionSharedAt: admin.firestore.FieldValue.delete(),
      };

      // 🔧 CORRECTION: Désactiver l'abonnement du partenaire si il était hérité
      const partnerSubscriptionType = partnerData.subscriptionType;
      const partnerHasInheritedSubscription = Boolean(
        partnerSubscriptionType === "shared_from_partner" ||
          partnerData.subscriptionInheritedFrom ||
          partnerData.subscriptionSharedFrom
      );

      console.log(
        "🔗 disconnectPartners: TRANSACTION - Vérification abonnement partenaire:",
        {
          subscriptionType: partnerSubscriptionType,
          hasInheritedSubscription: partnerHasInheritedSubscription,
          willDeactivate: partnerHasInheritedSubscription,
        }
      );

      if (partnerHasInheritedSubscription) {
        partnerUserUpdate.isSubscribed = false;
        partnerUserUpdate.subscriptionType =
          admin.firestore.FieldValue.delete();
        console.log(
          "🔗 disconnectPartners: TRANSACTION - Désactivation abonnement hérité pour le partenaire"
        );
      }

      console.log(
        "🔗 disconnectPartners: TRANSACTION - Application mise à jour partenaire"
      );
      transaction.update(partnerDoc.ref, partnerUserUpdate);

      // 3. Mettre à jour le code partenaire de l'utilisateur actuel
      console.log(
        "🔗 disconnectPartners: TRANSACTION - Mise à jour codes partenaires"
      );
      if (partnerCode) {
        console.log(
          "🔗 disconnectPartners: TRANSACTION - Mise à jour partnerCode:",
          partnerCode
        );
        transaction.update(
          admin.firestore().collection("partnerCodes").doc(partnerCode),
          {
            connectedPartnerId: null,
            connectedAt: null,
          }
        );
      }

      // 4. Mettre à jour le code partenaire connecté
      if (connectedPartnerCode) {
        console.log(
          "🔗 disconnectPartners: TRANSACTION - Mise à jour connectedPartnerCode:",
          connectedPartnerCode
        );
        transaction.update(
          admin
            .firestore()
            .collection("partnerCodes")
            .doc(connectedPartnerCode),
          {
            connectedPartnerId: null,
            connectedAt: null,
          }
        );
      }

      // 5. 🔧 NOUVEAU: Logger la déconnexion pour audit
      console.log(
        "🔗 disconnectPartners: TRANSACTION - Création log de déconnexion"
      );

      // 🔧 CORRECTION: S'assurer que les valeurs ne sont pas undefined pour Firestore
      const logData = {
        disconnectedBy: currentUserId,
        disconnectedPartner: partnerId,
        disconnectedAt: admin.firestore.FieldValue.serverTimestamp(),
        currentUserHadInheritedSubscription: currentHasInheritedSubscription,
        partnerHadInheritedSubscription: partnerHasInheritedSubscription,
        reason: "manual_disconnect",
        currentUserSubscriptionType: currentSubscriptionType || "none",
        partnerSubscriptionType: partnerSubscriptionType || "none",
      };

      console.log(
        "🔗 disconnectPartners: TRANSACTION - Données log à sauvegarder:",
        logData
      );

      transaction.create(
        admin.firestore().collection("partner_disconnection_logs").doc(),
        logData
      );

      console.log(
        "🔗 disconnectPartners: TRANSACTION - Fin de la transaction, commit en cours"
      );
    });

    console.log("✅ disconnectPartners: Transaction terminée avec succès");
    console.log("✅ disconnectPartners: Déconnexion réussie");

    return {
      success: true,
      message: "Partenaires déconnectés avec succès",
    };
  } catch (error) {
    console.error("❌ disconnectPartners: ERREUR DÉTAILLÉE:");
    console.error("❌ disconnectPartners: Type d'erreur:", typeof error);
    console.error("❌ disconnectPartners: Message:", error.message);
    console.error("❌ disconnectPartners: Code:", error.code);
    console.error("❌ disconnectPartners: Stack:", error.stack);

    // Si c'est déjà une HttpsError, la relancer
    if (error.code && error.message) {
      console.error("❌ disconnectPartners: Relance HttpsError existante");
      throw error;
    }

    console.error("❌ disconnectPartners: Création nouvelle HttpsError");
    throw new functions.https.HttpsError(
      "internal",
      `Erreur déconnexion: ${error.message || "Erreur inconnue"}`
    );
  }
});

// NOUVEAU: Fonction pour nettoyer les codes partenaires orphelins
exports.cleanupOrphanedPartnerCodes = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("🧹 cleanupOrphanedPartnerCodes: Début du nettoyage");

      // Récupérer tous les codes partenaires
      const codesSnapshot = await admin
        .firestore()
        .collection("partnerCodes")
        .get();

      let deletedCount = 0;
      let checkedCount = 0;

      for (const codeDoc of codesSnapshot.docs) {
        const codeData = codeDoc.data();
        const userId = codeData.userId;
        checkedCount++;

        console.log(
          `🧹 Vérification du code ${codeDoc.id} - Propriétaire: ${userId}`
        );

        try {
          // Vérifier si l'utilisateur propriétaire existe encore
          const userDoc = await admin
            .firestore()
            .collection("users")
            .doc(userId)
            .get();

          if (!userDoc.exists) {
            console.log(
              `❌ Utilisateur ${userId} n'existe plus - Suppression du code ${codeDoc.id}`
            );

            // Supprimer le code orphelin
            await codeDoc.ref.delete();
            deletedCount++;
          } else {
            console.log(
              `✅ Utilisateur ${userId} existe - Code ${codeDoc.id} conservé`
            );
          }
        } catch (error) {
          console.log(
            `❌ Erreur vérification utilisateur ${userId}: ${error.message}`
          );

          // Si erreur d'accès, probablement que l'utilisateur n'existe plus
          if (
            error.code === "permission-denied" ||
            error.code === "not-found"
          ) {
            console.log(
              `🗑️ Suppression du code ${codeDoc.id} (utilisateur inaccessible)`
            );
            await codeDoc.ref.delete();
            deletedCount++;
          }
        }
      }

      console.log(`✅ cleanupOrphanedPartnerCodes: Terminé`);
      console.log(
        `📊 Codes vérifiés: ${checkedCount}, Codes supprimés: ${deletedCount}`
      );

      return {
        success: true,
        checkedCount,
        deletedCount,
        message: `Nettoyage terminé: ${deletedCount} codes orphelins supprimés sur ${checkedCount} vérifiés`,
      };
    } catch (error) {
      console.error("❌ cleanupOrphanedPartnerCodes: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// Fonction pour valider un code partenaire avant connexion
exports.validatePartnerCode = functions.https.onCall(async (data, context) => {
  console.log("🔗 validatePartnerCode: Début validation code");

  // Vérifier l'authentification
  if (!context.auth) {
    console.log("❌ validatePartnerCode: Utilisateur non authentifié");
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const { partnerCode } = data;
  const currentUserId = context.auth.uid;

  console.log(`🔗 validatePartnerCode: Code à valider: ${partnerCode}`);
  console.log(`🔗 validatePartnerCode: Utilisateur: ${currentUserId}`);

  if (!partnerCode || typeof partnerCode !== "string") {
    console.log("❌ validatePartnerCode: Code invalide");
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Code partenaire invalide"
    );
  }

  try {
    // 1. Rechercher le code dans la collection partnerCodes (le code est l'ID du document)
    const codeDoc = await admin
      .firestore()
      .collection("partnerCodes")
      .doc(partnerCode)
      .get();

    if (!codeDoc.exists) {
      console.log("❌ validatePartnerCode: Code non trouvé");
      return {
        isValid: false,
        reason: "CODE_NOT_FOUND",
        message: "Code partenaire introuvable",
      };
    }

    const codeData = codeDoc.data();
    const ownerId = codeData.userId;
    const isActive = codeData.isActive;

    console.log(
      `🔗 validatePartnerCode: Code trouvé - Propriétaire: ${ownerId}`
    );
    console.log(`🔗 validatePartnerCode: Code actif: ${isActive}`);

    // Vérifier que le code est actif
    if (!isActive) {
      console.log("❌ validatePartnerCode: Code inactif");
      return {
        isValid: false,
        reason: "CODE_INACTIVE",
        message: "Ce code n'est plus actif",
      };
    }

    // 2. Vérifier que l'utilisateur ne se connecte pas à son propre code
    if (ownerId === currentUserId) {
      console.log(
        "❌ validatePartnerCode: Tentative de connexion à son propre code"
      );
      return {
        isValid: false,
        reason: "SELF_CONNECTION",
        message: "Vous ne pouvez pas vous connecter à votre propre code",
      };
    }

    // 3. Vérifier que le code n'est pas déjà utilisé
    if (
      codeData.connectedPartnerId &&
      codeData.connectedPartnerId !== currentUserId
    ) {
      console.log(
        "❌ validatePartnerCode: Code déjà utilisé par un autre utilisateur"
      );
      return {
        isValid: false,
        reason: "CODE_ALREADY_USED",
        message: "Ce code est déjà utilisé par un autre utilisateur",
      };
    }

    // 4. Vérifier l'existence du propriétaire du code (côté serveur)
    console.log(
      `🔗 validatePartnerCode: Vérification existence propriétaire: ${ownerId}`
    );
    const ownerDoc = await admin
      .firestore()
      .collection("users")
      .doc(ownerId)
      .get();

    if (!ownerDoc.exists) {
      console.log(
        "❌ validatePartnerCode: Propriétaire du code inexistant - Code orphelin"
      );

      // Marquer le code comme inactif (nettoyage automatique)
      await codeDoc.ref.update({
        isActive: false,
        deactivatedAt: admin.firestore.FieldValue.serverTimestamp(),
        deactivationReason: "owner_not_found",
      });

      return {
        isValid: false,
        reason: "OWNER_NOT_FOUND",
        message: "Le propriétaire de ce code n'existe plus",
      };
    }

    const ownerData = ownerDoc.data();
    console.log(
      `✅ validatePartnerCode: Propriétaire trouvé: ${
        ownerData.name || "Nom non défini"
      }`
    );

    // 5. Vérifier que l'utilisateur actuel n'a pas déjà un partenaire
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();
    const currentUserData = currentUserDoc.data();

    if (
      currentUserData &&
      currentUserData.partnerId &&
      currentUserData.partnerId !== ownerId
    ) {
      console.log(
        "❌ validatePartnerCode: Utilisateur déjà connecté à un autre partenaire"
      );
      return {
        isValid: false,
        reason: "ALREADY_CONNECTED",
        message: "Vous êtes déjà connecté à un autre partenaire",
      };
    }

    console.log("✅ validatePartnerCode: Code valide - Connexion autorisée");
    return {
      isValid: true,
      ownerName: ownerData.name || "Partenaire",
      ownerId: ownerId,
      codeId: codeDoc.id,
    };
  } catch (error) {
    console.error("❌ validatePartnerCode: Erreur:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Erreur lors de la validation du code"
    );
  }
});

// Fonction pour connecter deux partenaires de manière sécurisée
exports.connectPartners = functions.https.onCall(async (data, context) => {
  console.log("🔗 connectPartners: Début connexion partenaires");

  // Vérifier l'authentification
  if (!context.auth) {
    console.log("❌ connectPartners: Utilisateur non authentifié");
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const { partnerCode } = data;
  const currentUserId = context.auth.uid;

  console.log(`🔗 connectPartners: Code: ${partnerCode}`);
  console.log(`🔗 connectPartners: Utilisateur: ${currentUserId}`);

  if (!partnerCode || typeof partnerCode !== "string") {
    console.log("❌ connectPartners: Code invalide");
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Code partenaire invalide"
    );
  }

  try {
    // 1. Valider le code partenaire
    console.log("🔗 connectPartners: Validation du code...");
    const codeDoc = await admin
      .firestore()
      .collection("partnerCodes")
      .doc(partnerCode)
      .get();

    if (!codeDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Code partenaire introuvable"
      );
    }

    const codeData = codeDoc.data();
    const partnerUserId = codeData.userId;

    if (!codeData.isActive) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Ce code n'est plus actif"
      );
    }

    if (partnerUserId === currentUserId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Vous ne pouvez pas vous connecter à votre propre code"
      );
    }

    if (
      codeData.connectedPartnerId &&
      codeData.connectedPartnerId !== currentUserId
    ) {
      throw new functions.https.HttpsError(
        "already-exists",
        "Ce code est déjà utilisé par un autre utilisateur"
      );
    }

    // 2. Vérifier l'existence des deux utilisateurs
    console.log("🔗 connectPartners: Vérification des utilisateurs...");
    const [currentUserDoc, partnerUserDoc] = await Promise.all([
      admin.firestore().collection("users").doc(currentUserId).get(),
      admin.firestore().collection("users").doc(partnerUserId).get(),
    ]);

    if (!currentUserDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Votre compte utilisateur est introuvable"
      );
    }

    if (!partnerUserDoc.exists) {
      console.log(
        "❌ connectPartners: Propriétaire du code inexistant - Nettoyage automatique"
      );
      await codeDoc.ref.update({
        isActive: false,
        deactivatedAt: admin.firestore.FieldValue.serverTimestamp(),
        deactivationReason: "owner_not_found",
      });
      throw new functions.https.HttpsError(
        "not-found",
        "Le propriétaire de ce code n'existe plus"
      );
    }

    const currentUserData = currentUserDoc.data();
    const partnerUserData = partnerUserDoc.data();

    // 3. Vérifier que l'utilisateur actuel n'est pas déjà connecté à quelqu'un d'autre
    if (
      currentUserData.partnerId &&
      currentUserData.partnerId !== partnerUserId
    ) {
      throw new functions.https.HttpsError(
        "already-exists",
        "Vous êtes déjà connecté à un autre partenaire"
      );
    }

    // 4. CONFORMITÉ APPLE: Vérifier les règles de partage d'abonnement
    const partnerIsSubscribed = partnerUserData.isSubscribed || false;
    if (partnerIsSubscribed) {
      console.log(
        "🔗 connectPartners: Partenaire abonné - Vérification conformité Apple"
      );

      // Compter les partages existants
      const existingShares = await admin
        .firestore()
        .collection("users")
        .where("subscriptionInheritedFrom", "==", partnerUserId)
        .get();

      if (existingShares.size >= 1) {
        throw new functions.https.HttpsError(
          "resource-exhausted",
          "Ce partenaire partage déjà son abonnement avec quelqu'un d'autre"
        );
      }
    }

    // 5. Effectuer la connexion dans une transaction
    console.log("🔗 connectPartners: Création de la connexion...");
    await admin.firestore().runTransaction(async (transaction) => {
      // Marquer le code comme utilisé
      transaction.update(codeDoc.ref, {
        connectedPartnerId: currentUserId,
        connectedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // Mettre à jour l'utilisateur actuel
      const currentUserUpdate = {
        partnerId: partnerUserId,
        partnerConnectedAt: admin.firestore.FieldValue.serverTimestamp(),
      };

      // Hériter de l'abonnement si le partenaire est abonné
      if (partnerIsSubscribed) {
        console.log("🔗 connectPartners: Héritage de l'abonnement...");
        currentUserUpdate.isSubscribed = true;
        currentUserUpdate.subscriptionInheritedFrom = partnerUserId;
        currentUserUpdate.subscriptionInheritedAt =
          admin.firestore.FieldValue.serverTimestamp();
        currentUserUpdate.subscriptionType = "shared_from_partner";

        // Logger le partage pour conformité Apple
        const logData = {
          fromUserId: partnerUserId,
          toUserId: currentUserId,
          sharedAt: admin.firestore.FieldValue.serverTimestamp(),
          subscriptionType: "inherited",
          deviceInfo: "iOS App",
          appVersion: "1.0",
        };

        transaction.create(
          admin.firestore().collection("subscription_sharing_logs").doc(),
          logData
        );
      }

      transaction.update(currentUserDoc.ref, currentUserUpdate);

      // Mettre à jour le partenaire
      transaction.update(partnerUserDoc.ref, {
        partnerId: currentUserId,
        partnerConnectedAt: admin.firestore.FieldValue.serverTimestamp(),
        hasUnreadPartnerConnection: true, // Notification de connexion
      });
    });

    console.log("✅ connectPartners: Connexion créée avec succès");

    // 6. Synchroniser automatiquement les entrées de journal existantes
    try {
      console.log(
        "📚 connectPartners: Synchronisation des entrées de journal..."
      );

      // Appeler la fonction de synchronisation interne (pas via HTTPS)
      const syncResult = await syncPartnerJournalEntriesInternal(
        currentUserId,
        partnerUserId
      );

      console.log(
        `✅ connectPartners: Synchronisation journal terminée - ${syncResult.updatedEntriesCount} entrées mises à jour`
      );
    } catch (syncError) {
      console.error(
        "❌ connectPartners: Erreur synchronisation journal:",
        syncError
      );
      // Ne pas faire échouer la connexion pour une erreur de synchronisation
    }

    // 7. Synchroniser automatiquement les favoris existants
    try {
      console.log("❤️ connectPartners: Synchronisation des favoris...");

      // Appeler la fonction de synchronisation interne des favoris
      const syncFavoritesResult = await syncPartnerFavoritesInternal(
        currentUserId,
        partnerUserId
      );

      console.log(
        `✅ connectPartners: Synchronisation favoris terminée - ${syncFavoritesResult.updatedFavoritesCount} favoris mis à jour`
      );
    } catch (syncError) {
      console.error(
        "❌ connectPartners: Erreur synchronisation favoris:",
        syncError
      );
      // Ne pas faire échouer la connexion pour une erreur de synchronisation
    }

    return {
      success: true,
      partnerName: partnerUserData.name || "Partenaire",
      partnerIsSubscribed: partnerIsSubscribed,
      subscriptionInherited: partnerIsSubscribed,
      message: partnerIsSubscribed
        ? `Connecté à ${
            partnerUserData.name || "votre partenaire"
          } - Abonnement premium débloqué !`
        : `Connecté à ${partnerUserData.name || "votre partenaire"}`,
    };
  } catch (error) {
    console.error("❌ connectPartners: Erreur:", error);

    // Si c'est déjà une HttpsError, la relancer
    if (error.code && error.message) {
      throw error;
    }

    // Sinon, créer une nouvelle erreur générique
    throw new functions.https.HttpsError(
      "internal",
      "Erreur lors de la connexion"
    );
  }
});

// Fonction de synchronisation des abonnements entre partenaires
exports.syncPartnerSubscriptions = functions.https.onCall(
  async (data, context) => {
    console.log("🔄 syncPartnerSubscriptions: Début synchronisation");

    // Vérifier l'authentification
    if (!context.auth) {
      console.log("❌ syncPartnerSubscriptions: Utilisateur non authentifié");
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    console.log(`🔄 syncPartnerSubscriptions: Utilisateur: ${currentUserId}`);
    console.log(`🔄 syncPartnerSubscriptions: Partenaire: ${partnerId}`);
    console.log(
      `🔄 syncPartnerSubscriptions: Type currentUserId: ${typeof currentUserId}`
    );
    console.log(
      `🔄 syncPartnerSubscriptions: Longueur currentUserId: ${
        currentUserId ? currentUserId.length : "null/undefined"
      }`
    );
    console.log(
      `🔄 syncPartnerSubscriptions: Type partnerId: ${typeof partnerId}`
    );
    console.log(
      `🔄 syncPartnerSubscriptions: Longueur partnerId: ${
        partnerId ? partnerId.length : "null/undefined"
      }`
    );

    // 🔧 CORRECTION: Vérification robuste du currentUserId
    if (
      !currentUserId ||
      typeof currentUserId !== "string" ||
      currentUserId.trim() === ""
    ) {
      console.log(
        "❌ syncPartnerSubscriptions: ID utilisateur actuel invalide ou vide"
      );
      throw new functions.https.HttpsError(
        "unauthenticated",
        "ID utilisateur actuel invalide"
      );
    }

    // 🔧 CORRECTION: Vérification plus robuste du partnerId
    if (
      !partnerId ||
      typeof partnerId !== "string" ||
      partnerId.trim() === ""
    ) {
      console.log(
        "❌ syncPartnerSubscriptions: ID partenaire invalide ou vide"
      );
      throw new functions.https.HttpsError(
        "invalid-argument",
        "ID partenaire requis et ne peut pas être vide"
      );
    }

    try {
      // Récupérer les données des deux utilisateurs
      const [currentUserDoc, partnerUserDoc] = await Promise.all([
        admin.firestore().collection("users").doc(currentUserId).get(),
        admin.firestore().collection("users").doc(partnerId).get(),
      ]);

      if (!currentUserDoc.exists || !partnerUserDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur ou partenaire non trouvé"
        );
      }

      const currentUserData = currentUserDoc.data();
      const partnerUserData = partnerUserDoc.data();

      // Vérifier que les utilisateurs sont bien connectés
      if (
        currentUserData.partnerId !== partnerId ||
        partnerUserData.partnerId !== currentUserId
      ) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Les utilisateurs ne sont pas connectés en tant que partenaires"
        );
      }

      const currentIsSubscribed = currentUserData.isSubscribed || false;
      const currentSubscriptionType = currentUserData.subscriptionType;

      const partnerIsSubscribed = partnerUserData.isSubscribed || false;
      const partnerSubscriptionType = partnerUserData.subscriptionType;

      console.log("🔄 syncPartnerSubscriptions: État actuel:");
      console.log(
        `🔄 User: isSubscribed=${currentIsSubscribed}, type=${currentSubscriptionType}`
      );
      console.log(
        `🔄 Partner: isSubscribed=${partnerIsSubscribed}, type=${partnerSubscriptionType}`
      );

      let subscriptionInherited = false;
      let fromPartnerName = "";

      // Logique de synchronisation
      if (currentIsSubscribed && currentSubscriptionType === "direct") {
        // L'utilisateur actuel a un abonnement direct, partager avec le partenaire
        if (
          !partnerIsSubscribed ||
          partnerSubscriptionType !== "shared_from_partner"
        ) {
          await admin.firestore().collection("users").doc(partnerId).update({
            isSubscribed: true,
            subscriptionType: "shared_from_partner",
            subscriptionSharedFrom: currentUserId,
            subscriptionSharedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
          console.log(
            "✅ syncPartnerSubscriptions: Abonnement partagé vers le partenaire"
          );
        }
      } else if (partnerIsSubscribed && partnerSubscriptionType === "direct") {
        // Le partenaire a un abonnement direct, partager avec l'utilisateur actuel
        if (
          !currentIsSubscribed ||
          currentSubscriptionType !== "shared_from_partner"
        ) {
          await admin
            .firestore()
            .collection("users")
            .doc(currentUserId)
            .update({
              isSubscribed: true,
              subscriptionType: "shared_from_partner",
              subscriptionSharedFrom: partnerId,
              subscriptionSharedAt:
                admin.firestore.FieldValue.serverTimestamp(),
            });
          subscriptionInherited = true;
          fromPartnerName = partnerUserData.name || "Partenaire";
          console.log(
            "✅ syncPartnerSubscriptions: Abonnement hérité du partenaire"
          );
        }
      } else if (!currentIsSubscribed && !partnerIsSubscribed) {
        // Aucun des deux n'a d'abonnement direct, nettoyer les abonnements partagés
        const batch = admin.firestore().batch();

        const currentUserRef = admin
          .firestore()
          .collection("users")
          .doc(currentUserId);
        const partnerUserRef = admin
          .firestore()
          .collection("users")
          .doc(partnerId);

        batch.update(currentUserRef, {
          isSubscribed: false,
          subscriptionType: admin.firestore.FieldValue.delete(),
          subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
          subscriptionSharedAt: admin.firestore.FieldValue.delete(),
        });

        batch.update(partnerUserRef, {
          isSubscribed: false,
          subscriptionType: admin.firestore.FieldValue.delete(),
          subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
          subscriptionSharedAt: admin.firestore.FieldValue.delete(),
        });

        await batch.commit();
        console.log(
          "✅ syncPartnerSubscriptions: Abonnements nettoyés - mode gratuit"
        );
      }

      return {
        success: true,
        subscriptionInherited: subscriptionInherited,
        fromPartnerName: fromPartnerName,
      };
    } catch (error) {
      console.error("❌ syncPartnerSubscriptions: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// Fonction pour récupérer les informations du partenaire de manière sécurisée
exports.getPartnerInfo = functions.https.onCall(async (data, context) => {
  console.log("👥 getPartnerInfo: Début récupération info partenaire");

  // Vérifier l'authentification
  if (!context.auth) {
    console.log("❌ getPartnerInfo: Utilisateur non authentifié");
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  console.log(`👥 getPartnerInfo: Utilisateur: ${currentUserId}`);
  console.log(`👥 getPartnerInfo: Partenaire demandé: ${partnerId}`);

  if (!partnerId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "ID partenaire requis"
    );
  }

  try {
    // Vérifier que l'utilisateur actuel est bien connecté à ce partenaire
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();

    if (!currentUserDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const currentUserData = currentUserDoc.data();

    // Vérifier que le partenaire demandé est bien le partenaire connecté
    if (currentUserData.partnerId !== partnerId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Vous n'êtes pas autorisé à accéder aux informations de cet utilisateur"
      );
    }

    // Récupérer les informations du partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerId)
      .get();

    if (!partnerDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Partenaire non trouvé"
      );
    }

    const partnerData = partnerDoc.data();

    // Retourner seulement les informations nécessaires
    const partnerInfo = {
      name: partnerData.name || "Partenaire",
      isSubscribed: partnerData.isSubscribed || false,
      subscriptionType: partnerData.subscriptionType || null,
      subscriptionSharedFrom: partnerData.subscriptionSharedFrom || null,
      profileImageURL: partnerData.profileImageURL || null,
    };

    console.log("✅ getPartnerInfo: Informations récupérées avec succès");
    console.log(
      `✅ getPartnerInfo: Photo profil: ${
        partnerInfo.profileImageURL ? "Présente" : "Absente"
      }`
    );

    return {
      success: true,
      partnerInfo: partnerInfo,
    };
  } catch (error) {
    console.error("❌ getPartnerInfo: Erreur:", error);

    // Si c'est déjà une HttpsError, la relancer
    if (error.code && error.message) {
      throw error;
    }

    throw new functions.https.HttpsError("internal", error.message);
  }
});

// Fonction pour récupérer l'image de profil du partenaire avec URL signée
exports.getPartnerProfileImage = functions.https.onCall(
  async (data, context) => {
    console.log(
      "🖼️ getPartnerProfileImage: Début récupération image partenaire"
    );

    // Vérifier l'authentification
    if (!context.auth) {
      console.log("❌ getPartnerProfileImage: Utilisateur non authentifié");
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    console.log(`🖼️ getPartnerProfileImage: Utilisateur: ${currentUserId}`);
    console.log(`🖼️ getPartnerProfileImage: Partenaire: ${partnerId}`);

    if (!partnerId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "ID partenaire requis"
      );
    }

    try {
      // Vérifier que l'utilisateur actuel est bien connecté à ce partenaire
      const currentUserDoc = await admin
        .firestore()
        .collection("users")
        .doc(currentUserId)
        .get();

      if (!currentUserDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur non trouvé"
        );
      }

      const currentUserData = currentUserDoc.data();

      // Vérifier que le partenaire demandé est bien le partenaire connecté
      if (currentUserData.partnerId !== partnerId) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Vous n'êtes pas autorisé à accéder aux informations de cet utilisateur"
        );
      }

      // Récupérer les informations du partenaire
      const partnerDoc = await admin
        .firestore()
        .collection("users")
        .doc(partnerId)
        .get();

      if (!partnerDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Partenaire non trouvé"
        );
      }

      const partnerData = partnerDoc.data();
      const profileImageURL = partnerData.profileImageURL;

      if (!profileImageURL) {
        console.log(
          "❌ getPartnerProfileImage: Aucune photo de profil pour ce partenaire"
        );
        return {
          success: false,
          reason: "NO_PROFILE_IMAGE",
          message: "Aucune photo de profil disponible",
        };
      }

      // Générer une URL signée temporaire pour l'image (1 heure de validité)
      const bucket = admin.storage().bucket();

      // Extraire le chemin du fichier depuis l'URL Firebase Storage
      const urlMatch = profileImageURL.match(/\/o\/(.*?)\?/);
      if (!urlMatch) {
        throw new functions.https.HttpsError(
          "internal",
          "Format d'URL d'image invalide"
        );
      }

      const filePath = decodeURIComponent(urlMatch[1]);
      const file = bucket.file(filePath);

      console.log(
        `🖼️ getPartnerProfileImage: Génération URL signée pour: ${filePath}`
      );

      const [signedUrl] = await file.getSignedUrl({
        action: "read",
        expires: Date.now() + 60 * 60 * 1000, // 1 heure
      });

      console.log("✅ getPartnerProfileImage: URL signée générée avec succès");

      return {
        success: true,
        imageUrl: signedUrl,
        expiresIn: 3600, // 1 heure en secondes
      };
    } catch (error) {
      console.error("❌ getPartnerProfileImage: Erreur:", error);

      // Si c'est déjà une HttpsError, la relancer
      if (error.code && error.message) {
        throw error;
      }

      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// 🔧 NOUVELLE FONCTION: Générer URL signée pour toutes les images Firebase Storage
exports.getSignedImageURL = functions.https.onCall(async (data, context) => {
  console.log("🔧 getSignedImageURL: Début génération URL signée");

  // Vérifier l'authentification
  if (!context.auth) {
    console.log("❌ getSignedImageURL: Utilisateur non authentifié");
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  const { filePath } = data;

  console.log(`🔧 getSignedImageURL: Utilisateur: ${currentUserId}`);
  console.log(`🔧 getSignedImageURL: Chemin fichier: ${filePath}`);

  if (!filePath) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Chemin de fichier requis"
    );
  }

  try {
    // Vérifier les permissions selon le type d'image
    if (filePath.startsWith("profile_images/")) {
      // Image de profil - vérifier que c'est l'utilisateur ou son partenaire
      const pathComponents = filePath.split("/");
      if (pathComponents.length < 2) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Chemin d'image de profil invalide"
        );
      }

      const imageOwnerId = pathComponents[1];

      // Permettre l'accès si c'est l'utilisateur lui-même
      if (imageOwnerId === currentUserId) {
        console.log(
          "✅ getSignedImageURL: Accès autorisé - Propriétaire de l'image"
        );
      } else {
        // Vérifier si c'est le partenaire connecté
        const currentUserDoc = await admin
          .firestore()
          .collection("users")
          .doc(currentUserId)
          .get();

        if (!currentUserDoc.exists) {
          throw new functions.https.HttpsError(
            "not-found",
            "Utilisateur non trouvé"
          );
        }

        const currentUserData = currentUserDoc.data();

        if (currentUserData.partnerId !== imageOwnerId) {
          throw new functions.https.HttpsError(
            "permission-denied",
            "Vous n'êtes pas autorisé à accéder à cette image"
          );
        }

        console.log(
          "✅ getSignedImageURL: Accès autorisé - Image du partenaire"
        );
      }
    } else if (filePath.startsWith("journal_images/")) {
      // Image du journal - vérifier que c'est l'utilisateur ou son partenaire
      const pathComponents = filePath.split("/");
      if (pathComponents.length < 2) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Chemin d'image de journal invalide"
        );
      }

      const imageOwnerId = pathComponents[1];

      // Permettre l'accès si c'est l'utilisateur lui-même
      if (imageOwnerId === currentUserId) {
        console.log(
          "✅ getSignedImageURL: Accès autorisé - Propriétaire de l'image journal"
        );
      } else {
        // Vérifier si c'est le partenaire connecté
        const currentUserDoc = await admin
          .firestore()
          .collection("users")
          .doc(currentUserId)
          .get();

        if (!currentUserDoc.exists) {
          throw new functions.https.HttpsError(
            "not-found",
            "Utilisateur non trouvé"
          );
        }

        const currentUserData = currentUserDoc.data();

        // Vérification bidirectionnelle pour les images du journal
        if (currentUserData.partnerId !== imageOwnerId) {
          // Vérifier aussi avec connectedPartnerId (compatibilité)
          if (currentUserData.connectedPartnerId !== imageOwnerId) {
            throw new functions.https.HttpsError(
              "permission-denied",
              "Vous n'êtes pas autorisé à accéder à cette image de journal"
            );
          }
        }

        console.log(
          "✅ getSignedImageURL: Accès autorisé - Image journal du partenaire"
        );
      }
    } else {
      // Type d'image non reconnu
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Type d'image non supporté"
      );
    }

    // Générer l'URL signée
    const bucket = admin.storage().bucket();
    const file = bucket.file(filePath);

    console.log(
      `🔧 getSignedImageURL: Génération URL signée pour: ${filePath}`
    );

    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 60 * 60 * 1000, // 1 heure
    });

    console.log("✅ getSignedImageURL: URL signée générée avec succès");

    return {
      success: true,
      signedUrl: signedUrl,
      expiresIn: 3600, // 1 heure en secondes
    };
  } catch (error) {
    console.error("❌ getSignedImageURL: Erreur:", error);

    // Si c'est déjà une HttpsError, la relancer
    if (error.code && error.message) {
      throw error;
    }

    throw new functions.https.HttpsError("internal", error.message);
  }
});

// Fonction interne pour synchroniser les entrées de journal (appelée en interne)
async function syncPartnerJournalEntriesInternal(currentUserId, partnerId) {
  console.log("📚 syncPartnerJournalEntriesInternal: Début synchronisation");
  console.log(`📚 Utilisateur: ${currentUserId}, Partenaire: ${partnerId}`);

  // 1. Récupérer toutes les entrées créées par l'utilisateur actuel
  const currentUserEntriesSnapshot = await admin
    .firestore()
    .collection("journalEntries")
    .where("authorId", "==", currentUserId)
    .get();

  // 2. Récupérer toutes les entrées créées par le partenaire
  const partnerEntriesSnapshot = await admin
    .firestore()
    .collection("journalEntries")
    .where("authorId", "==", partnerId)
    .get();

  let updatedCount = 0;
  const batch = admin.firestore().batch();

  // 3. Mettre à jour les entrées de l'utilisateur actuel pour inclure le partenaire
  for (const doc of currentUserEntriesSnapshot.docs) {
    const entryData = doc.data();
    const currentPartnerIds = entryData.partnerIds || [];

    // Ajouter le partenaire s'il n'est pas déjà présent
    if (!currentPartnerIds.includes(partnerId)) {
      const updatedPartnerIds = [...currentPartnerIds, partnerId];
      batch.update(doc.ref, {
        partnerIds: updatedPartnerIds,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      updatedCount++;
      console.log(`📚 Mise à jour entrée utilisateur: ${doc.id}`);
    }
  }

  // 4. Mettre à jour les entrées du partenaire pour inclure l'utilisateur actuel
  for (const doc of partnerEntriesSnapshot.docs) {
    const entryData = doc.data();
    const currentPartnerIds = entryData.partnerIds || [];

    // Ajouter l'utilisateur actuel s'il n'est pas déjà présent
    if (!currentPartnerIds.includes(currentUserId)) {
      const updatedPartnerIds = [...currentPartnerIds, currentUserId];
      batch.update(doc.ref, {
        partnerIds: updatedPartnerIds,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      updatedCount++;
      console.log(`📚 Mise à jour entrée partenaire: ${doc.id}`);
    }
  }

  // 5. Exécuter toutes les mises à jour
  if (updatedCount > 0) {
    await batch.commit();
    console.log(
      `✅ syncPartnerJournalEntriesInternal: ${updatedCount} entrées mises à jour`
    );
  } else {
    console.log(
      "📚 syncPartnerJournalEntriesInternal: Aucune entrée à mettre à jour"
    );
  }

  return {
    success: true,
    updatedEntriesCount: updatedCount,
    userEntriesCount: currentUserEntriesSnapshot.docs.length,
    partnerEntriesCount: partnerEntriesSnapshot.docs.length,
  };
}

// NOUVEAU: Fonction pour synchroniser les entrées de journal après connexion partenaire
exports.syncPartnerJournalEntries = functions.https.onCall(
  async (data, context) => {
    console.log("📚 syncPartnerJournalEntries: Début synchronisation journal");

    // Vérifier l'authentification
    if (!context.auth) {
      console.log("❌ syncPartnerJournalEntries: Utilisateur non authentifié");
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    console.log(`📚 syncPartnerJournalEntries: Utilisateur: ${currentUserId}`);
    console.log(`📚 syncPartnerJournalEntries: Partenaire: ${partnerId}`);

    if (
      !partnerId ||
      typeof partnerId !== "string" ||
      partnerId.trim() === ""
    ) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "ID partenaire requis"
      );
    }

    try {
      // Vérifier que les utilisateurs sont bien connectés
      const [currentUserDoc, partnerUserDoc] = await Promise.all([
        admin.firestore().collection("users").doc(currentUserId).get(),
        admin.firestore().collection("users").doc(partnerId).get(),
      ]);

      if (!currentUserDoc.exists || !partnerUserDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur ou partenaire non trouvé"
        );
      }

      const currentUserData = currentUserDoc.data();
      const partnerUserData = partnerUserDoc.data();

      // Vérifier que les utilisateurs sont bien connectés
      if (
        currentUserData.partnerId !== partnerId ||
        partnerUserData.partnerId !== currentUserId
      ) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Les utilisateurs ne sont pas connectés en tant que partenaires"
        );
      }

      console.log(
        "📚 syncPartnerJournalEntries: Connexion partenaire vérifiée"
      );

      // Appeler la fonction interne de synchronisation
      const result = await syncPartnerJournalEntriesInternal(
        currentUserId,
        partnerId
      );

      return {
        success: true,
        updatedEntriesCount: result.updatedEntriesCount,
        userEntriesCount: result.userEntriesCount,
        partnerEntriesCount: result.partnerEntriesCount,
        message: `Synchronisation terminée: ${result.updatedEntriesCount} entrées mises à jour`,
      };
    } catch (error) {
      console.error("❌ syncPartnerJournalEntries: Erreur:", error);

      // Si c'est déjà une HttpsError, la relancer
      if (error.code && error.message) {
        throw error;
      }

      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// 🔧 NOUVELLE FONCTION: Nettoyer les abonnements orphelins en production
exports.cleanupOrphanedSubscriptions = functions.https.onCall(
  async (data, context) => {
    try {
      console.log(
        "🧹 cleanupOrphanedSubscriptions: Début du nettoyage des abonnements orphelins"
      );

      // Cette fonction doit être protégée - seulement pour les admins
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { adminSecret } = data;
      const expectedSecret =
        functions.config().admin?.secret || "your-admin-secret";

      if (adminSecret !== expectedSecret) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Accès non autorisé - secret admin requis"
        );
      }

      let cleanedCount = 0;
      let checkedCount = 0;

      // Récupérer tous les utilisateurs avec des abonnements partagés
      const usersSnapshot = await admin
        .firestore()
        .collection("users")
        .where("subscriptionType", "==", "shared_from_partner")
        .get();

      console.log(
        `🧹 cleanupOrphanedSubscriptions: ${usersSnapshot.docs.length} utilisateurs avec abonnements partagés trouvés`
      );

      for (const userDoc of usersSnapshot.docs) {
        const userData = userDoc.data();
        const userId = userDoc.id;
        checkedCount++;

        console.log(`🧹 Vérification utilisateur: ${userId}`);

        // Vérifier si l'utilisateur a encore un partenaire connecté
        const partnerId = userData.partnerId;
        const sharedFrom =
          userData.subscriptionSharedFrom || userData.subscriptionInheritedFrom;

        if (!partnerId) {
          // Utilisateur sans partenaire mais avec abonnement partagé = orphelin
          console.log(
            `❌ Utilisateur ${userId} a un abonnement partagé mais pas de partenaire`
          );

          await userDoc.ref.update({
            isSubscribed: false,
            subscriptionType: admin.firestore.FieldValue.delete(),
            subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
            subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
            subscriptionSharedAt: admin.firestore.FieldValue.delete(),
            subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
          });

          cleanedCount++;
          console.log(`🧹 Nettoyé abonnement orphelin pour: ${userId}`);
          continue;
        }

        // Vérifier si le partenaire existe encore
        const partnerDoc = await admin
          .firestore()
          .collection("users")
          .doc(partnerId)
          .get();

        if (!partnerDoc.exists) {
          // Partenaire n'existe plus = abonnement orphelin
          console.log(
            `❌ Partenaire ${partnerId} de l'utilisateur ${userId} n'existe plus`
          );

          await userDoc.ref.update({
            isSubscribed: false,
            subscriptionType: admin.firestore.FieldValue.delete(),
            subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
            subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
            subscriptionSharedAt: admin.firestore.FieldValue.delete(),
            subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
            partnerId: admin.firestore.FieldValue.delete(),
            partnerConnectedAt: admin.firestore.FieldValue.delete(),
          });

          cleanedCount++;
          console.log(
            `🧹 Nettoyé abonnement orphelin (partenaire inexistant) pour: ${userId}`
          );
          continue;
        }

        const partnerData = partnerDoc.data();

        // Vérifier si le partenaire a encore un abonnement direct
        if (sharedFrom === partnerId && !partnerData.isSubscribed) {
          // Le partenaire qui partageait l'abonnement ne l'a plus = orphelin
          console.log(
            `❌ Partenaire ${partnerId} n'a plus d'abonnement à partager avec ${userId}`
          );

          await userDoc.ref.update({
            isSubscribed: false,
            subscriptionType: admin.firestore.FieldValue.delete(),
            subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
            subscriptionInheritedFrom: admin.firestore.FieldValue.delete(),
            subscriptionSharedAt: admin.firestore.FieldValue.delete(),
            subscriptionInheritedAt: admin.firestore.FieldValue.delete(),
          });

          cleanedCount++;
          console.log(
            `🧹 Nettoyé abonnement orphelin (partenaire sans abonnement) pour: ${userId}`
          );
        }
      }

      console.log(`✅ cleanupOrphanedSubscriptions: Terminé`);
      console.log(
        `📊 Utilisateurs vérifiés: ${checkedCount}, Abonnements orphelins nettoyés: ${cleanedCount}`
      );

      return {
        success: true,
        checkedCount,
        cleanedCount,
        message: `Nettoyage terminé: ${cleanedCount} abonnements orphelins supprimés sur ${checkedCount} vérifiés`,
      };
    } catch (error) {
      console.error("❌ cleanupOrphanedSubscriptions: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// 🔧 NOUVELLE FONCTION: Diagnostiquer les abonnements orphelins (lecture seule)
exports.diagnoseOrphanedSubscriptions = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("🔍 diagnoseOrphanedSubscriptions: Début du diagnostic");

      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { adminSecret } = data;
      const expectedSecret =
        functions.config().admin?.secret || "your-admin-secret";

      if (adminSecret !== expectedSecret) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Accès non autorisé - secret admin requis"
        );
      }

      const orphanedUsers = [];
      let checkedCount = 0;

      // Récupérer tous les utilisateurs avec des abonnements partagés
      const usersSnapshot = await admin
        .firestore()
        .collection("users")
        .where("subscriptionType", "==", "shared_from_partner")
        .get();

      console.log(
        `🔍 diagnoseOrphanedSubscriptions: ${usersSnapshot.docs.length} utilisateurs avec abonnements partagés trouvés`
      );

      for (const userDoc of usersSnapshot.docs) {
        const userData = userDoc.data();
        const userId = userDoc.id;
        checkedCount++;

        const partnerId = userData.partnerId;
        const sharedFrom =
          userData.subscriptionSharedFrom || userData.subscriptionInheritedFrom;

        let issue = null;
        let partnerStatus = null;

        if (!partnerId) {
          issue = "NO_PARTNER";
          partnerStatus = "MISSING";
        } else {
          const partnerDoc = await admin
            .firestore()
            .collection("users")
            .doc(partnerId)
            .get();

          if (!partnerDoc.exists) {
            issue = "PARTNER_NOT_EXISTS";
            partnerStatus = "DELETED";
          } else {
            const partnerData = partnerDoc.data();
            partnerStatus = partnerData.isSubscribed
              ? "SUBSCRIBED"
              : "NOT_SUBSCRIBED";

            if (sharedFrom === partnerId && !partnerData.isSubscribed) {
              issue = "PARTNER_NO_SUBSCRIPTION";
            }
          }
        }

        if (issue) {
          orphanedUsers.push({
            userId,
            name: userData.name || "Sans nom",
            partnerId: partnerId || "N/A",
            sharedFrom: sharedFrom || "N/A",
            issue,
            partnerStatus,
            subscriptionType: userData.subscriptionType,
            isSubscribed: userData.isSubscribed,
          });
        }
      }

      console.log(`✅ diagnoseOrphanedSubscriptions: Diagnostic terminé`);
      console.log(
        `📊 Utilisateurs vérifiés: ${checkedCount}, Problèmes détectés: ${orphanedUsers.length}`
      );

      return {
        success: true,
        checkedCount,
        orphanedCount: orphanedUsers.length,
        orphanedUsers,
        summary: {
          totalSharedSubscriptions: checkedCount,
          orphanedSubscriptions: orphanedUsers.length,
          issueTypes: {
            noPartner: orphanedUsers.filter((u) => u.issue === "NO_PARTNER")
              .length,
            partnerDeleted: orphanedUsers.filter(
              (u) => u.issue === "PARTNER_NOT_EXISTS"
            ).length,
            partnerNoSubscription: orphanedUsers.filter(
              (u) => u.issue === "PARTNER_NO_SUBSCRIPTION"
            ).length,
          },
        },
      };
    } catch (error) {
      console.error("❌ diagnoseOrphanedSubscriptions: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// NOUVEAU: Fonction pour récupérer la localisation du partenaire de manière sécurisée
exports.getPartnerLocation = functions.https.onCall(async (data, context) => {
  console.log(
    "🌍 getPartnerLocation: Début récupération localisation partenaire"
  );

  // Vérifier l'authentification
  if (!context.auth) {
    console.log("❌ getPartnerLocation: Utilisateur non authentifié");
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  console.log(`🌍 getPartnerLocation: Utilisateur: ${currentUserId}`);
  console.log(`🌍 getPartnerLocation: Partenaire demandé: ${partnerId}`);

  if (!partnerId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "ID partenaire requis"
    );
  }

  try {
    // Vérifier que l'utilisateur actuel est bien connecté à ce partenaire
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();

    if (!currentUserDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const currentUserData = currentUserDoc.data();

    // Vérifier que le partenaire demandé est bien le partenaire connecté
    if (currentUserData.partnerId !== partnerId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Vous n'êtes pas autorisé à accéder à la localisation de cet utilisateur"
      );
    }

    // Récupérer les informations du partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerId)
      .get();

    if (!partnerDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Partenaire non trouvé"
      );
    }

    const partnerData = partnerDoc.data();
    const currentLocation = partnerData.currentLocation;

    console.log(
      "🌍 getPartnerLocation: Localisation partenaire trouvée:",
      currentLocation ? "OUI" : "NON"
    );

    if (!currentLocation) {
      console.log(
        "❌ getPartnerLocation: Aucune localisation pour ce partenaire"
      );
      return {
        success: false,
        reason: "NO_LOCATION",
        message: "Aucune localisation disponible pour ce partenaire",
      };
    }

    console.log("✅ getPartnerLocation: Localisation récupérée avec succès");

    return {
      success: true,
      location: {
        latitude: currentLocation.latitude,
        longitude: currentLocation.longitude,
        address: currentLocation.address || null,
        city: currentLocation.city || null,
        country: currentLocation.country || null,
        lastUpdated: currentLocation.lastUpdated,
      },
    };
  } catch (error) {
    console.error("❌ getPartnerLocation: Erreur:", error);

    // Si c'est déjà une HttpsError, la relancer
    if (error.code && error.message) {
      throw error;
    }

    throw new functions.https.HttpsError("internal", error.message);
  }
});

// Fonction interne pour synchroniser les favoris entre partenaires (appelée en interne)
async function syncPartnerFavoritesInternal(currentUserId, partnerId) {
  console.log("❤️ syncPartnerFavoritesInternal: Début synchronisation");
  console.log(`❤️ Utilisateur: ${currentUserId}, Partenaire: ${partnerId}`);

  // 1. Récupérer tous les favoris créés par l'utilisateur actuel
  const currentUserFavoritesSnapshot = await admin
    .firestore()
    .collection("favoriteQuestions")
    .where("authorId", "==", currentUserId)
    .get();

  // 2. Récupérer tous les favoris créés par le partenaire
  const partnerFavoritesSnapshot = await admin
    .firestore()
    .collection("favoriteQuestions")
    .where("authorId", "==", partnerId)
    .get();

  let updatedCount = 0;
  const batch = admin.firestore().batch();

  // 3. Mettre à jour les favoris de l'utilisateur actuel pour inclure le partenaire
  for (const doc of currentUserFavoritesSnapshot.docs) {
    const favoriteData = doc.data();
    const currentPartnerIds = favoriteData.partnerIds || [];

    // Ajouter le partenaire s'il n'est pas déjà présent
    if (!currentPartnerIds.includes(partnerId)) {
      const updatedPartnerIds = [...currentPartnerIds, partnerId];
      batch.update(doc.ref, {
        partnerIds: updatedPartnerIds,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      updatedCount++;
      console.log(`❤️ Mise à jour favori utilisateur: ${doc.id}`);
    }
  }

  // 4. Mettre à jour les favoris du partenaire pour inclure l'utilisateur actuel
  for (const doc of partnerFavoritesSnapshot.docs) {
    const favoriteData = doc.data();
    const currentPartnerIds = favoriteData.partnerIds || [];

    // Ajouter l'utilisateur actuel s'il n'est pas déjà présent
    if (!currentPartnerIds.includes(currentUserId)) {
      const updatedPartnerIds = [...currentPartnerIds, currentUserId];
      batch.update(doc.ref, {
        partnerIds: updatedPartnerIds,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      updatedCount++;
      console.log(`❤️ Mise à jour favori partenaire: ${doc.id}`);
    }
  }

  // 5. Exécuter toutes les mises à jour
  if (updatedCount > 0) {
    await batch.commit();
    console.log(
      `✅ syncPartnerFavoritesInternal: ${updatedCount} favoris mis à jour`
    );
  } else {
    console.log(
      "❤️ syncPartnerFavoritesInternal: Aucun favori à mettre à jour"
    );
  }

  return {
    success: true,
    updatedFavoritesCount: updatedCount,
    userFavoritesCount: currentUserFavoritesSnapshot.docs.length,
    partnerFavoritesCount: partnerFavoritesSnapshot.docs.length,
  };
}

// NOUVEAU: Fonction pour synchroniser les favoris après connexion partenaire
exports.syncPartnerFavorites = functions.https.onCall(async (data, context) => {
  console.log("❤️ syncPartnerFavorites: Début synchronisation favoris");

  // Vérifier l'authentification
  if (!context.auth) {
    console.log("❌ syncPartnerFavorites: Utilisateur non authentifié");
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  console.log(`❤️ syncPartnerFavorites: Utilisateur: ${currentUserId}`);
  console.log(`❤️ syncPartnerFavorites: Partenaire: ${partnerId}`);

  if (!partnerId || typeof partnerId !== "string" || partnerId.trim() === "") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "ID partenaire requis"
    );
  }

  try {
    // Vérifier que les utilisateurs sont bien connectés
    const [currentUserDoc, partnerUserDoc] = await Promise.all([
      admin.firestore().collection("users").doc(currentUserId).get(),
      admin.firestore().collection("users").doc(partnerId).get(),
    ]);

    if (!currentUserDoc.exists || !partnerUserDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur ou partenaire non trouvé"
      );
    }

    const currentUserData = currentUserDoc.data();
    const partnerUserData = partnerUserDoc.data();

    // Vérifier que les utilisateurs sont bien connectés
    if (
      currentUserData.partnerId !== partnerId ||
      partnerUserData.partnerId !== currentUserId
    ) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Les utilisateurs ne sont pas connectés en tant que partenaires"
      );
    }

    console.log("❤️ syncPartnerFavorites: Connexion partenaire vérifiée");

    // Appeler la fonction interne de synchronisation
    const result = await syncPartnerFavoritesInternal(currentUserId, partnerId);

    return {
      success: true,
      updatedFavoritesCount: result.updatedFavoritesCount,
      userFavoritesCount: result.userFavoritesCount,
      partnerFavoritesCount: result.partnerFavoritesCount,
      message: `Synchronisation terminée: ${result.updatedFavoritesCount} favoris mis à jour`,
    };
  } catch (error) {
    console.error("❌ syncPartnerFavorites: Erreur:", error);

    // Si c'est déjà une HttpsError, la relancer
    if (error.code && error.message) {
      throw error;
    }

    throw new functions.https.HttpsError("internal", error.message);
  }
});

// ========================================
// QUESTIONS QUOTIDIENNES - CLOUD FUNCTIONS
// ========================================

/**
 * Détecte automatiquement le nombre total de questions disponibles
 * Synchronisé avec les clés du fichier DailyQuestions.xcstrings
 */
function getTotalQuestionsCount() {
  // NOUVEAU: Liste des clés synchronisée avec DailyQuestions.xcstrings
  // Cette liste doit être mise à jour quand vous ajoutez des questions
  const availableQuestionKeys = [
    "daily_question_1",
    "daily_question_2",
    "daily_question_3",
    "daily_question_4",
    "daily_question_5",
    "daily_question_6",
    "daily_question_7",
    "daily_question_8",
    "daily_question_9",
    "daily_question_10",
    "daily_question_11",
    "daily_question_12",
    "daily_question_13",
    "daily_question_14",
    "daily_question_15",
    "daily_question_16",
    "daily_question_17",
    "daily_question_18",
    "daily_question_19",
    "daily_question_20",
    "daily_question_21",
    "daily_question_22",
    "daily_question_23",
    "daily_question_24",
    "daily_question_25",
    "daily_question_26",
    "daily_question_27",
    "daily_question_28",
    "daily_question_29",
    "daily_question_30",
    "daily_question_31",
    "daily_question_32",
    "daily_question_33",
    "daily_question_34",
    "daily_question_35",
    "daily_question_36",
    "daily_question_37",
    "daily_question_38",
    "daily_question_39",
    "daily_question_40",
    "daily_question_41",
    "daily_question_42",
    "daily_question_43",
    "daily_question_44",
    "daily_question_45",
    "daily_question_46",
    "daily_question_47",
    "daily_question_48",
    "daily_question_49",
    "daily_question_50",
    "daily_question_51",
  ];

  console.log(
    `📊 getTotalQuestionsCount: ${availableQuestionKeys.length} questions disponibles`
  );

  // ÉVOLUTIF: Vous pouvez ajouter autant de questions que vous voulez
  // Il suffit d'ajouter les clés correspondantes dans ce tableau
  return availableQuestionKeys.length;
}

// NOUVEAU: Fonction pour générer la clé de question avec cycle infini
function generateQuestionKey(questionDay) {
  const availableQuestions = getTotalQuestionsCount();
  const cycledDay = ((questionDay - 1) % availableQuestions) + 1;
  console.log(
    `📝 generateQuestionKey: Cycle question ${questionDay} → ${cycledDay}/${availableQuestions}`
  );
  return `daily_question_${cycledDay}`;
}

// 🗑️ FONCTION SUPPRIMÉE : getNotificationTemplates()
// Cette fonction généraient des templates localisés type "💬 Nouveau message"
// SUPPRIMÉE car maintenant les notifications FCM utilisent directement :
// - Titre : Nom du partenaire
// - Body : Message complet
// Format unifié avec les notifications locales iOS

/**
 * Calcule le jour actuel de la question basé sur les settings
 * CORRIGÉ : Utilise UTC pour éviter les problèmes de timezone
 */
function calculateCurrentQuestionDay(settings, currentTime = new Date()) {
  const totalQuestions = getTotalQuestionsCount();

  if (!settings || !settings.startDate) {
    console.log(
      "📅 calculateCurrentQuestionDay: Pas de settings ou startDate - retour jour 1"
    );
    return 1; // Première visite
  }

  // STANDARD: startDate est TOUJOURS un Timestamp côté Firebase
  const startDate = settings.startDate.toDate
    ? settings.startDate.toDate()
    : new Date(settings.startDate);

  console.log("📅 calculateCurrentQuestionDay: LOGS TIMEZONE DÉTAILLÉS");
  console.log(`📅 - startDate original: ${startDate.toISOString()}`);
  console.log(`📅 - currentTime original: ${currentTime.toISOString()}`);
  console.log(`📅 - timezone settings: ${settings.timezone}`);
  console.log(`📅 - currentDay dans settings: ${settings.currentDay}`);

  // 🔧 CORRECTION : Utiliser UTC EXCLUSIVEMENT pour éviter les problèmes de timezone
  const startDateUTC = new Date(
    Date.UTC(
      startDate.getFullYear(),
      startDate.getMonth(),
      startDate.getDate(),
      0,
      0,
      0,
      0
    )
  );

  const currentTimeUTC = new Date(
    Date.UTC(
      currentTime.getFullYear(),
      currentTime.getMonth(),
      currentTime.getDate(),
      0,
      0,
      0,
      0
    )
  );

  console.log(`📅 - startDateUTC normalisée: ${startDateUTC.toISOString()}`);
  console.log(
    `📅 - currentTimeUTC normalisée: ${currentTimeUTC.toISOString()}`
  );

  const timeDiff = currentTimeUTC.getTime() - startDateUTC.getTime();
  const daysSinceStart = Math.floor(timeDiff / (1000 * 3600 * 24));

  console.log(`📅 - timeDiff (ms): ${timeDiff}`);
  console.log(`📅 - daysSinceStart: ${daysSinceStart}`);

  // 🔧 CORRECTION : Logic plus robuste pour l'incrémentation
  // Si on est le même jour que la création, currentDay = 1
  // Si on est le jour suivant, currentDay = 2, etc.
  const expectedDay = daysSinceStart + 1;

  console.log(`📅 - expectedDay calculé: ${expectedDay}`);
  console.log(`📅 - currentDay actuel: ${settings.currentDay}`);

  // CYCLE INFINI: Plus de limite sur totalQuestions
  const cycledDay = ((expectedDay - 1) % totalQuestions) + 1;

  console.log(`📅 - cycledDay final: ${cycledDay}/${totalQuestions}`);
  console.log("📅 calculateCurrentQuestionDay: FIN LOGS TIMEZONE");

  return cycledDay;
}

/**
 * Récupère ou crée les settings pour un couple
 * CORRIGÉ : Utilise UTC pour la cohérence timezone
 */
async function getOrCreateDailyQuestionSettings(
  coupleId,
  timezone = "Europe/Paris"
) {
  try {
    console.log(
      `📅 getOrCreateDailyQuestionSettings: Récupération/création settings pour ${coupleId}`
    );

    const settingsRef = admin
      .firestore()
      .collection("dailyQuestionSettings")
      .doc(coupleId);

    const settingsDoc = await settingsRef.get();

    if (settingsDoc.exists) {
      console.log(
        `✅ getOrCreateDailyQuestionSettings: Settings existants trouvés pour ${coupleId}`
      );
      const data = settingsDoc.data();

      // OPTIMISATION : S'assurer que nextScheduledDate existe
      if (!data.nextScheduledDate) {
        console.log(
          `🔧 getOrCreateDailyQuestionSettings: Ajout nextScheduledDate manquant pour ${coupleId}`
        );

        const today = new Date();
        const nextDate = new Date(today);
        nextDate.setDate(nextDate.getDate() + 1);
        const nextDateString = nextDate.toISOString().split("T")[0];

        await settingsRef.update({
          nextScheduledDate: nextDateString,
        });

        return {
          ...data,
          nextScheduledDate: nextDateString,
        };
      }

      return data;
    }

    // 🔧 CORRECTION : Créer startDate en UTC minuit pour éviter les problèmes de timezone
    console.log(
      `🆕 getOrCreateDailyQuestionSettings: Création nouveaux settings pour ${coupleId}`
    );

    const now = new Date();

    // 🔧 NOUVEAU : Créer startDate en UTC minuit pour cohérence
    const startDateUTC = new Date(
      Date.UTC(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0)
    );

    console.log(`📅 getOrCreateDailyQuestionSettings: CRÉATION SETTINGS UTC:`);
    console.log(`📅 - now local: ${now.toISOString()}`);
    console.log(`📅 - startDateUTC: ${startDateUTC.toISOString()}`);
    console.log(`📅 - timezone: ${timezone}`);

    // OPTIMISATION : Calculer nextScheduledDate dès la création
    const nextDate = new Date(startDateUTC);
    nextDate.setDate(nextDate.getDate() + 1); // Demain
    const nextDateString = nextDate.toISOString().split("T")[0];

    const newSettings = {
      coupleId: coupleId,
      startDate: admin.firestore.Timestamp.fromDate(startDateUTC), // 🔧 UTC
      timezone: timezone,
      currentDay: 1,
      nextScheduledDate: nextDateString,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      lastVisitDate: null,
    };

    console.log(
      `📅 getOrCreateDailyQuestionSettings: Nouveaux settings pour ${coupleId}:`
    );
    console.log(`   - startDate: ${startDateUTC.toISOString()}`);
    console.log(`   - currentDay: 1`);
    console.log(`   - nextScheduledDate: ${nextDateString}`);
    console.log(`   - timezone: ${timezone}`);

    await settingsRef.set(newSettings);

    console.log(
      `✅ getOrCreateDailyQuestionSettings: Settings créés avec succès pour ${coupleId}`
    );

    return {
      ...newSettings,
      // TOUJOURS garder startDate comme Timestamp côté Firebase
    };
  } catch (error) {
    console.error(
      `❌ getOrCreateDailyQuestionSettings: Erreur pour ${coupleId}:`,
      error
    );
    throw error;
  }
}

/**
 * 🔥 CORRECTION: Fonction commune pour génération questions (sans auth/rate limiting)
 */
async function generateDailyQuestionCore(
  coupleId,
  timezone,
  questionDay = null
) {
  console.log(
    `🎯 generateDailyQuestionCore: coupleId=${coupleId}, timezone=${timezone}`
  );

  try {
    // Récupérer ou créer les settings
    const settings = await getOrCreateDailyQuestionSettings(coupleId, timezone);

    // Calculer le jour si pas fourni
    const targetDay = questionDay || calculateCurrentQuestionDay(settings);

    const today = new Date();
    const todayString = today.toISOString().split("T")[0];

    // Vérifier si question existe déjà (idempotence)
    const questionId = `${coupleId}_${todayString}`;
    const existingDoc = await admin
      .firestore()
      .collection("dailyQuestions")
      .doc(questionId)
      .get();

    if (existingDoc.exists) {
      console.log(
        `✅ Question déjà existante pour ${coupleId} jour ${targetDay}`
      );
      return {
        success: true,
        question: existingDoc.data(),
        alreadyExists: true,
      };
    }

    // Générer nouvelle question
    const questionKey = generateQuestionKey(targetDay);
    const questionData = {
      id: questionId,
      coupleId,
      questionKey,
      questionDay: targetDay,
      scheduledDate: todayString,
      scheduledDateTime: admin.firestore.Timestamp.fromDate(today),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      isCompleted: false,
    };

    // Sauvegarder
    await admin
      .firestore()
      .collection("dailyQuestions")
      .doc(questionId)
      .set(questionData);

    console.log(`✅ Question générée: ${questionKey} pour couple ${coupleId}`);
    return {
      success: true,
      question: questionData,
      generated: true,
    };
  } catch (error) {
    console.error(`❌ Erreur génération question pour ${coupleId}:`, error);
    throw error;
  }
}

/**
 * Générer la question du jour pour un couple
 */
exports.generateDailyQuestion = functions.https.onCall(
  async (data, context) => {
    try {
      // Vérifier l'authentification
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { coupleId, userId, questionDay, timezone } = data;

      if (!coupleId || !userId || !questionDay) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "coupleId, userId et questionDay requis"
        );
      }

      console.log(`⚙️ generateDailyQuestion: PARAMÈTRES REÇUS:`);
      console.log(`⚙️ - coupleId: ${coupleId}`);
      console.log(`⚙️ - userId: ${userId}`);
      console.log(`⚙️ - questionDay: ${questionDay}`);
      console.log(`⚙️ - timezone: ${timezone}`);

      const today = new Date();
      const todayString = today.toISOString().split("T")[0];

      // NOUVEAU: Supprimer automatiquement la question d'hier AVANT de créer celle d'aujourd'hui
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayString = yesterday.toISOString().split("T")[0];

      console.log(
        `🧹 generateDailyQuestion: Vérification suppression question d'hier: ${yesterdayString}`
      );

      try {
        const yesterdayQuestionRef = admin
          .firestore()
          .collection("dailyQuestions")
          .doc(`${coupleId}_${yesterdayString}`);

        const yesterdayDoc = await yesterdayQuestionRef.get();
        if (yesterdayDoc.exists) {
          console.log(
            `🧹 generateDailyQuestion: Suppression question d'hier trouvée: ${yesterdayString}`
          );

          // Supprimer les réponses d'hier en premier
          const responsesSnapshot = await yesterdayQuestionRef
            .collection("responses")
            .get();

          const batch = admin.firestore().batch();

          // Supprimer toutes les réponses
          responsesSnapshot.docs.forEach((doc) => {
            batch.delete(doc.ref);
          });

          // Supprimer la question principale
          batch.delete(yesterdayQuestionRef);

          await batch.commit();

          console.log(
            `✅ generateDailyQuestion: Question d'hier supprimée avec succès: ${yesterdayString} (${responsesSnapshot.docs.length} réponses supprimées)`
          );
        } else {
          console.log(
            `ℹ️ generateDailyQuestion: Aucune question d'hier à supprimer pour: ${yesterdayString}`
          );
        }
      } catch (cleanupError) {
        console.error(
          `❌ generateDailyQuestion: Erreur suppression question d'hier:`,
          cleanupError
        );
        // Ne pas faire échouer la génération pour une erreur de nettoyage
      }

      // Vérifier si cette question existe déjà pour aujourd'hui
      const existingQuestionQuery = await admin
        .firestore()
        .collection("dailyQuestions")
        .where("coupleId", "==", coupleId)
        .where("scheduledDate", "==", todayString)
        .get();

      if (!existingQuestionQuery.empty) {
        const existingDoc = existingQuestionQuery.docs[0];
        const existingData = existingDoc.data();

        return {
          success: true,
          message: "Question déjà existante pour aujourd'hui",
          existingQuestion: {
            id: existingDoc.id,
            questionKey: existingData.questionKey,
            questionDay: existingData.questionDay,
          },
        };
      }

      // Utiliser la fonction globale pour générer la clé
      const questionKey = generateQuestionKey(questionDay);

      console.log(`⚙️ generateDailyQuestion: GÉNÉRATION:`);
      console.log(`⚙️ - questionKey: ${questionKey}`);
      console.log(`⚙️ - questionDay: ${questionDay}`);
      console.log(`⚙️ - date: ${todayString}`);

      // Créer la question
      const newQuestion = {
        id: `${coupleId}_${todayString}`,
        coupleId: coupleId,
        questionKey: questionKey,
        questionDay: questionDay,
        scheduledDate: todayString,
        scheduledDateTime: admin.firestore.Timestamp.fromDate(today),
        responses: {},
        status: "pending",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        timezone: timezone || "Europe/Paris",
      };

      // Sauvegarder dans Firestore
      await admin
        .firestore()
        .collection("dailyQuestions")
        .doc(newQuestion.id)
        .set(newQuestion);

      // Mettre à jour currentDay dans settings (questionDay = nouveau currentDay)
      try {
        await admin
          .firestore()
          .collection("dailyQuestionSettings")
          .doc(coupleId)
          .update({
            currentDay: questionDay,
            lastVisitDate: admin.firestore.FieldValue.serverTimestamp(),
          });

        console.log(
          `⚙️ generateDailyQuestion: currentDay mis à jour → ${questionDay}`
        );
      } catch (settingsError) {
        console.error(
          "❌ generateDailyQuestion: Erreur mise à jour settings:",
          settingsError
        );
      }

      return {
        success: true,
        questionId: newQuestion.id,
        questionKey: questionKey,
        questionDay: questionDay,
        message: "Question générée avec succès",
        question: {
          id: newQuestion.id,
          coupleId: newQuestion.coupleId,
          questionKey: newQuestion.questionKey,
          questionDay: newQuestion.questionDay,
          scheduledDate: newQuestion.scheduledDate,
          status: newQuestion.status,
        },
      };
    } catch (error) {
      console.error("❌ generateDailyQuestion: Erreur:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de la génération de la question"
      );
    }
  }
);

/**
 * 🔧 NOUVEAU: Créer ou récupérer les settings pour un couple
 * Callable sécurisé pour initialiser immédiatement les settings à la première connexion
 */
exports.getOrCreateDailyQuestionSettings = functions.https.onCall(
  async (data, context) => {
    try {
      // Vérifier l'authentification
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { coupleId, timezone } = data;

      if (!coupleId) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "coupleId requis"
        );
      }

      console.log(
        `📅 getOrCreateDailyQuestionSettings: Callable pour ${coupleId}`
      );
      console.log(`📅 - timezone: ${timezone || "Europe/Paris"}`);
      console.log(`📅 - userId: ${context.auth.uid}`);

      // Utiliser la fonction utilitaire existante
      const settings = await getOrCreateDailyQuestionSettings(
        coupleId,
        timezone || "Europe/Paris"
      );

      console.log(
        `✅ getOrCreateDailyQuestionSettings: Succès pour ${coupleId}`
      );
      console.log(`   - currentDay: ${settings.currentDay}`);
      console.log(`   - startDate: ${settings.startDate}`);

      return {
        success: true,
        settings: {
          coupleId: settings.coupleId,
          currentDay: settings.currentDay,
          startDate: settings.startDate,
          timezone: settings.timezone,
          nextScheduledDate: settings.nextScheduledDate,
          createdAt: settings.createdAt,
        },
        message: "Settings créés/récupérés avec succès",
      };
    } catch (error) {
      console.error("❌ getOrCreateDailyQuestionSettings: Erreur:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de la création/récupération des settings"
      );
    }
  }
);

/**
 * Notifier les partenaires quand une réponse est ajoutée (nouveau système sous-collections)
 */
exports.notifyPartnerResponseSubcollection = functions.firestore
  .document("dailyQuestions/{questionId}/responses/{responseId}")
  .onCreate(async (snap, context) => {
    try {
      const responseData = snap.data();
      const questionId = context.params.questionId;
      const responseId = context.params.responseId;

      // Récupérer les données de la question
      const questionDoc = await admin
        .firestore()
        .collection("dailyQuestions")
        .doc(questionId)
        .get();

      if (!questionDoc.exists) {
        console.log(
          "❌ notifyPartnerResponseSubcollection: Question non trouvée"
        );
        return null;
      }

      const questionData = questionDoc.data();
      const coupleId = questionData.coupleId;
      const respondingUserId = responseData.userId;

      // Identifier le partenaire à notifier
      const userIds = coupleId.split("_");
      const partnerUserId = userIds.find((id) => id !== respondingUserId);

      if (!partnerUserId) {
        return null;
      }

      // Marquer la question comme active
      await questionDoc.ref.update({
        status: "active",
        lastResponseAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // Récupérer les données des utilisateurs pour FCM
      const [respondingUserDoc, partnerUserDoc] = await Promise.all([
        admin.firestore().collection("users").doc(respondingUserId).get(),
        admin.firestore().collection("users").doc(partnerUserId).get(),
      ]);

      if (!respondingUserDoc.exists || !partnerUserDoc.exists) {
        console.log(
          "❌ notifyPartnerResponseSubcollection: Utilisateur(s) non trouvé(s)"
        );
        return null;
      }

      const respondingUserData = respondingUserDoc.data();
      const partnerUserData = partnerUserDoc.data();
      const fcmToken = partnerUserData.fcmToken;

      console.log(
        `🔥 notifyPartnerResponseSubcollection: Utilisateur répondant: ${
          respondingUserData.name || "Inconnu"
        }`
      );
      console.log(
        `🔥 notifyPartnerResponseSubcollection: Partenaire: ${
          partnerUserData.name || "Inconnu"
        }`
      );
      console.log(
        `🔥 notifyPartnerResponseSubcollection: Token FCM partenaire: ${
          fcmToken ? fcmToken.substring(0, 20) + "..." : "AUCUN"
        }`
      );

      if (!fcmToken) {
        console.log(
          `❌ notifyPartnerResponseSubcollection: Pas de token FCM pour ${partnerUserId}`
        );
        return null;
      }

      // Envoyer la notification FCM avec localisation
      const messageText = responseData.text || "";
      const previewText =
        messageText.length > 50
          ? messageText.substring(0, 50) + "..."
          : messageText;

      // NOUVEAU: Localisation basée sur la langue de l'utilisateur
      const userLanguage = partnerUserData.languageCode || "fr"; // Défaut français

      const payload = {
        notification: {
          title: respondingUserData.name || "Votre partenaire", // 🎯 FORMAT UNIFIÉ : Nom en titre
          body: messageText, // 🎯 FORMAT UNIFIÉ : Message complet en body
        },
        data: {
          questionId: questionId,
          senderId: respondingUserId,
          senderName: respondingUserData.name || "Votre partenaire",
          type: "new_message",
          language: userLanguage, // NOUVEAU: Inclure la langue dans les data
        },
        token: fcmToken,
      };

      console.log(
        `🔔 notifyPartnerResponseSubcollection: Notification FCM format unifié:`
      );
      console.log(`   - Titre: ${payload.notification.title}`);
      console.log(
        `   - Body: ${payload.notification.body.substring(0, 50)}...`
      );
      console.log(`   - Langue: ${userLanguage}`);

      console.log(
        `🔔 notifyPartnerResponseSubcollection: Préparation envoi FCM:`,
        JSON.stringify(payload, null, 2)
      );

      try {
        const response = await admin.messaging().send(payload);
        console.log(
          `✅ notifyPartnerResponseSubcollection: Push FCM envoyé à ${partnerUserId} - Response: ${response}`
        );
      } catch (fcmError) {
        console.error(
          `❌ notifyPartnerResponseSubcollection: Erreur envoi FCM - ${fcmError}`
        );

        // Si le token est invalide, le supprimer
        if (fcmError.code === "messaging/registration-token-not-registered") {
          await admin
            .firestore()
            .collection("users")
            .doc(partnerUserId)
            .update({
              fcmToken: admin.firestore.FieldValue.delete(),
            });
          console.log(
            `🧹 notifyPartnerResponseSubcollection: Token FCM invalide supprimé pour ${partnerUserId}`
          );
        }
      }

      console.log(
        `✅ notifyPartnerResponseSubcollection: Question mise à jour, notification envoyée`
      );

      return null;
    } catch (error) {
      console.error("❌ notifyPartnerResponseSubcollection: Erreur", error);
      return null;
    }
  });

/**
 * Fonction planifiée pour générer les questions quotidiennes
 * 🔧 CORRIGÉ : S'exécute à minuit UTC pour cohérence timezone
 * OPTIMISÉ : traite seulement les couples qui ont une question prévue aujourd'hui
 */
exports.scheduledDailyQuestionGenerator = functions.pubsub
  .schedule("0 0 * * *") // 🔧 CORRECTION: Minuit UTC au lieu de 21h
  .timeZone("UTC") // 🔧 CORRECTION: UTC explicite pour éviter confusion
  .onRun(async (context) => {
    try {
      console.log(
        "🔥 scheduledDailyQuestionGenerator: Début de la génération planifiée OPTIMISÉE"
      );

      const today = new Date();
      const todayString = today.toISOString().split("T")[0]; // Format YYYY-MM-DD

      // OPTIMISATION : Ne traiter que les couples qui ont une question prévue aujourd'hui
      console.log(
        `📅 scheduledDailyQuestionGenerator: Recherche couples avec nextScheduledDate = ${todayString}`
      );

      const settingsSnapshot = await admin
        .firestore()
        .collection("dailyQuestionSettings")
        .where("nextScheduledDate", "==", todayString)
        .get();

      console.log(
        `📦 scheduledDailyQuestionGenerator: ${settingsSnapshot.size} couples à traiter (au lieu de TOUS)`
      );

      if (settingsSnapshot.empty) {
        console.log(
          "✅ scheduledDailyQuestionGenerator: Aucun couple à traiter aujourd'hui"
        );
        return {
          success: true,
          generated: 0,
          message: "Aucune génération nécessaire",
        };
      }

      let totalGenerated = 0;
      let totalSkipped = 0;
      let totalErrors = 0;

      // Traitement parallèle optimisé
      const batchPromises = settingsSnapshot.docs.map(async (settingsDoc) => {
        try {
          const settings = settingsDoc.data();
          const coupleId = settings.coupleId;

          console.log(
            `🎯 scheduledDailyQuestionGenerator: Traitement couple ${coupleId}`
          );

          // Calculer le jour actuel pour ce couple
          const currentDay = calculateCurrentQuestionDay(settings);

          if (!currentDay) {
            console.log(
              `✅ scheduledDailyQuestionGenerator: Couple ${coupleId} - toutes questions épuisées`
            );

            // OPTIMISATION : Programmer la prochaine date (cycle infini)
            const nextDay = 1; // Recommencer au début
            const nextDate = new Date(today);
            nextDate.setDate(nextDate.getDate() + 1);
            const nextDateString = nextDate.toISOString().split("T")[0];

            await admin
              .firestore()
              .collection("dailyQuestionSettings")
              .doc(coupleId)
              .update({
                nextScheduledDate: nextDateString,
                currentDay: nextDay,
              });

            return { type: "cycle_restart", nextDay };
          }

          // Vérifier si une question existe déjà pour aujourd'hui
          const existingQuery = await admin
            .firestore()
            .collection("dailyQuestions")
            .where("coupleId", "==", coupleId)
            .where("scheduledDate", "==", todayString)
            .get();

          if (!existingQuery.empty) {
            console.log(
              `⚠️ scheduledDailyQuestionGenerator: Question déjà existante pour ${coupleId}`
            );
            return { type: "skipped", reason: "already_exists" };
          }

          // NOUVEAU: Supprimer la question d'hier AVANT de créer celle d'aujourd'hui
          const yesterday = new Date(today);
          yesterday.setDate(yesterday.getDate() - 1);
          const yesterdayString = yesterday.toISOString().split("T")[0];

          try {
            const yesterdayQuestionRef = admin
              .firestore()
              .collection("dailyQuestions")
              .doc(`${coupleId}_${yesterdayString}`);

            const yesterdayDoc = await yesterdayQuestionRef.get();
            if (yesterdayDoc.exists) {
              // Supprimer les réponses d'hier
              const responsesSnapshot = await yesterdayQuestionRef
                .collection("responses")
                .get();

              const batch = admin.firestore().batch();
              responsesSnapshot.docs.forEach((doc) => batch.delete(doc.ref));
              batch.delete(yesterdayQuestionRef);
              await batch.commit();

              console.log(
                `🧹 scheduledDailyQuestionGenerator: Question d'hier supprimée pour ${coupleId}: ${yesterdayString}`
              );
            }
          } catch (cleanupError) {
            console.error(
              `❌ scheduledDailyQuestionGenerator: Erreur suppression pour ${coupleId}:`,
              cleanupError
            );
          }

          // Créer la question pour le jour actuel
          const questionKey = `daily_question_${currentDay}`;

          const newQuestion = {
            id: `${coupleId}_${todayString}`,
            coupleId: coupleId,
            questionKey: questionKey,
            questionDay: currentDay,
            scheduledDate: todayString,
            scheduledDateTime: admin.firestore.Timestamp.fromDate(today),
            responses: {},
            status: "pending",
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            timezone: settings.timezone || "Europe/Paris",
          };

          await admin
            .firestore()
            .collection("dailyQuestions")
            .doc(newQuestion.id)
            .set(newQuestion);

          // OPTIMISATION : Programmer la prochaine date
          const nextDay = currentDay + 1;
          const nextDate = new Date(today);
          nextDate.setDate(nextDate.getDate() + 1);
          const nextDateString = nextDate.toISOString().split("T")[0];

          await admin
            .firestore()
            .collection("dailyQuestionSettings")
            .doc(coupleId)
            .update({
              currentDay: nextDay,
              nextScheduledDate: nextDateString,
              lastQuestionGenerated:
                admin.firestore.FieldValue.serverTimestamp(),
            });

          console.log(
            `✅ scheduledDailyQuestionGenerator: Question créée pour ${coupleId} - ${questionKey} (jour ${currentDay})`
          );
          console.log(
            `📅 scheduledDailyQuestionGenerator: Prochaine question le ${nextDateString} (jour ${nextDay})`
          );

          return { type: "generated", questionKey, currentDay, nextDay };
        } catch (error) {
          console.error(
            `❌ scheduledDailyQuestionGenerator: Erreur pour le couple ${settingsDoc.id}`,
            error
          );
          return { type: "error", error: error.message };
        }
      });

      // Attendre tous les traitements
      const results = await Promise.all(batchPromises);

      // Compter les résultats
      results.forEach((result) => {
        switch (result.type) {
          case "generated":
          case "cycle_restart":
            totalGenerated++;
            break;
          case "skipped":
            totalSkipped++;
            break;
          case "error":
            totalErrors++;
            break;
        }
      });

      console.log(
        `✅ scheduledDailyQuestionGenerator: Traitement terminé - ${totalGenerated} générées, ${totalSkipped} ignorées, ${totalErrors} erreurs`
      );

      return {
        success: true,
        generated: totalGenerated,
        skipped: totalSkipped,
        errors: totalErrors,
        processed: settingsSnapshot.size,
        optimization: "nextScheduledDate_filter_active",
      };
    } catch (error) {
      console.error(
        "❌ scheduledDailyQuestionGenerator: Erreur globale",
        error
      );
      throw error;
    }
  });

// 🗑️ FONCTION SUPPRIMÉE : scheduleDailyQuestionNotification
// Cette fonction était utilisée pour envoyer des notifications de rappel à 21h
// SUPPRIMÉE car seules les notifications de messages sont souhaitées

/**
 * Soumettre une réponse à une question quotidienne (nouveau système avec sous-collections)
 */
exports.submitDailyQuestionResponse = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("💬 submitDailyQuestionResponse: Début soumission réponse");

      // Vérifier l'authentification
      if (!context.auth) {
        console.error(
          "❌ submitDailyQuestionResponse: Utilisateur non authentifié"
        );
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { questionId, responseText, userName } = data;
      const userId = context.auth.uid;

      if (!questionId || !responseText || !userName) {
        console.error("❌ submitDailyQuestionResponse: Paramètres manquants");
        throw new functions.https.HttpsError(
          "invalid-argument",
          "questionId, responseText et userName requis"
        );
      }

      console.log(
        `💬 submitDailyQuestionResponse: Question: ${questionId}, Utilisateur: ${userId}`
      );

      // Vérifier que la question existe
      const questionRef = admin
        .firestore()
        .collection("dailyQuestions")
        .doc(questionId);
      const questionDoc = await questionRef.get();

      if (!questionDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Question non trouvée"
        );
      }

      const questionData = questionDoc.data();

      // Vérifier que l'utilisateur fait partie du couple
      const coupleId = questionData.coupleId;
      if (!coupleId.includes(userId)) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Vous n'êtes pas autorisé à répondre à cette question"
        );
      }

      // Créer la réponse dans la sous-collection
      const responseData = {
        id: admin.firestore().collection("temp").doc().id, // Générer un ID unique
        userId: userId,
        userName: userName,
        text: responseText.trim(),
        respondedAt: admin.firestore.FieldValue.serverTimestamp(),
        status: "answered",
        isReadByPartner: false,
      };

      // Ajouter la réponse à la sous-collection
      const responseRef = questionRef
        .collection("responses")
        .doc(responseData.id);
      await responseRef.set(responseData);

      // Mettre à jour le timestamp de la question principale
      await questionRef.update({
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        status: "active", // Marquer la question comme active dès la première réponse
      });

      console.log(
        `✅ submitDailyQuestionResponse: Réponse ajoutée avec succès: ${responseData.id}`
      );

      return {
        success: true,
        responseId: responseData.id,
        message: "Réponse ajoutée avec succès",
      };
    } catch (error) {
      console.error("❌ submitDailyQuestionResponse: Erreur", error);

      // Si c'est déjà une HttpsError, la relancer
      if (error.code && error.message) {
        throw error;
      }

      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de l'ajout de la réponse"
      );
    }
  }
);

/**
 * Récupérer les réponses d'une question quotidienne (sous-collection)
 */
exports.getDailyQuestionResponses = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("📋 getDailyQuestionResponses: Début récupération réponses");

      // Vérifier l'authentification
      if (!context.auth) {
        console.error(
          "❌ getDailyQuestionResponses: Utilisateur non authentifié"
        );
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { questionId } = data;
      const userId = context.auth.uid;

      if (!questionId) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "questionId requis"
        );
      }

      console.log(
        `📋 getDailyQuestionResponses: Question: ${questionId}, Utilisateur: ${userId}`
      );

      // Vérifier que la question existe et l'utilisateur y a accès
      const questionRef = admin
        .firestore()
        .collection("dailyQuestions")
        .doc(questionId);
      const questionDoc = await questionRef.get();

      if (!questionDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Question non trouvée"
        );
      }

      const questionData = questionDoc.data();
      const coupleId = questionData.coupleId;

      if (!coupleId.includes(userId)) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Vous n'êtes pas autorisé à accéder à cette question"
        );
      }

      // Récupérer toutes les réponses de la sous-collection
      const responsesSnapshot = await questionRef
        .collection("responses")
        .orderBy("respondedAt", "asc")
        .get();

      const responses = [];
      responsesSnapshot.forEach((doc) => {
        const responseData = doc.data();
        responses.push({
          id: doc.id,
          ...responseData,
          respondedAt: responseData.respondedAt
            ? responseData.respondedAt.toDate().toISOString()
            : null,
        });
      });

      console.log(
        `✅ getDailyQuestionResponses: ${responses.length} réponses récupérées`
      );

      return {
        success: true,
        responses: responses,
        count: responses.length,
      };
    } catch (error) {
      console.error("❌ getDailyQuestionResponses: Erreur", error);

      if (error.code && error.message) {
        throw error;
      }

      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de la récupération des réponses"
      );
    }
  }
);

/**
 * Migrer les réponses existantes vers le nouveau système de sous-collections
 */
exports.migrateDailyQuestionResponses = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("🔄 migrateDailyQuestionResponses: Début migration");

      // Cette fonction doit être protégée - seulement pour les admins ou l'utilisateur lui-même
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { coupleId, adminSecret } = data;
      const userId = context.auth.uid;

      // Vérifier les permissions
      if (adminSecret) {
        const expectedSecret =
          functions.config().admin?.secret || "your-admin-secret";
        if (adminSecret !== expectedSecret) {
          throw new functions.https.HttpsError(
            "permission-denied",
            "Accès admin non autorisé"
          );
        }
      } else if (coupleId && !coupleId.includes(userId)) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Vous n'êtes pas autorisé à migrer ce couple"
        );
      }

      let migratedCount = 0;
      let skippedCount = 0;
      let errorCount = 0;

      // Récupérer les questions à migrer
      let query = admin.firestore().collection("dailyQuestions");

      if (coupleId) {
        query = query.where("coupleId", "==", coupleId);
      }

      const questionsSnapshot = await query.get();

      console.log(
        `🔄 migrateDailyQuestionResponses: ${questionsSnapshot.size} questions à vérifier`
      );

      for (const questionDoc of questionsSnapshot.docs) {
        try {
          const questionData = questionDoc.data();
          const oldResponses = questionData.responses || {};

          // Vérifier s'il y a des réponses à migrer
          if (Object.keys(oldResponses).length === 0) {
            skippedCount++;
            continue;
          }

          // Vérifier si la migration a déjà été faite
          const existingResponsesSnapshot = await questionDoc.ref
            .collection("responses")
            .limit(1)
            .get();

          if (!existingResponsesSnapshot.empty) {
            console.log(`⏭️ Question ${questionDoc.id} déjà migrée`);
            skippedCount++;
            continue;
          }

          console.log(
            `🔄 Migration question ${questionDoc.id} - ${
              Object.keys(oldResponses).length
            } réponses`
          );

          // Migrer chaque réponse vers la sous-collection
          const batch = admin.firestore().batch();

          for (const [responseUserId, responseData] of Object.entries(
            oldResponses
          )) {
            const responseId = admin.firestore().collection("temp").doc().id;
            const responseRef = questionDoc.ref
              .collection("responses")
              .doc(responseId);

            const migratedResponse = {
              id: responseId,
              userId: responseUserId,
              userName: responseData.userName || "Utilisateur",
              text: responseData.text || "",
              respondedAt:
                responseData.respondedAt ||
                admin.firestore.FieldValue.serverTimestamp(),
              status: responseData.status || "answered",
              isReadByPartner: responseData.isReadByPartner || false,
            };

            batch.set(responseRef, migratedResponse);
          }

          // Nettoyer l'ancien champ responses
          batch.update(questionDoc.ref, {
            responses: admin.firestore.FieldValue.delete(),
            migratedAt: admin.firestore.FieldValue.serverTimestamp(),
            migrationVersion: "v2_subcollections",
          });

          await batch.commit();
          migratedCount++;

          console.log(`✅ Question ${questionDoc.id} migrée avec succès`);
        } catch (error) {
          console.error(
            `❌ Erreur migration question ${questionDoc.id}:`,
            error
          );
          errorCount++;
        }
      }

      console.log(`✅ migrateDailyQuestionResponses: Migration terminée`);
      console.log(
        `📊 ${migratedCount} migrées, ${skippedCount} ignorées, ${errorCount} erreurs`
      );

      return {
        success: true,
        migratedCount,
        skippedCount,
        errorCount,
        message: `Migration terminée: ${migratedCount} questions migrées`,
      };
    } catch (error) {
      console.error("❌ migrateDailyQuestionResponses: Erreur", error);

      if (error.code && error.message) {
        throw error;
      }

      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors de la migration"
      );
    }
  }
);

// 🗑️ FONCTION SUPPRIMÉE : scheduleDailyQuestionNotifications
// Cette fonction s'exécutait tous les jours à 21h pour envoyer des notifications de rappel
// SUPPRIMÉE car seules les notifications de messages entre partenaires sont souhaitées
//
// Économies réalisées :
// - 0 Cloud Scheduler jobs = -$0.10/mois
// - 0 exécutions quotidiennes = -720 exécutions/mois
// - 0 Firestore reads pour vérifier les réponses = économies importantes

/**
 * Signaler un contenu inapproprié
 */
exports.reportInappropriateContent = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("🚨 reportInappropriateContent: Début du signalement");

      // Vérifier l'authentification
      if (!context.auth) {
        console.error(
          "❌ reportInappropriateContent: Utilisateur non authentifié"
        );
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const {
        messageId,
        reportedUserId,
        reportedUserName,
        messageText,
        reason,
      } = data;
      const reporterUserId = context.auth.uid;

      // Validation des paramètres
      if (
        !messageId ||
        !reportedUserId ||
        !messageText ||
        !reason ||
        reporterUserId === reportedUserId
      ) {
        console.error(
          "❌ reportInappropriateContent: Paramètres invalides ou auto-signalement"
        );
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Paramètres manquants ou invalides"
        );
      }

      console.log(
        `🚨 reportInappropriateContent: Signalement de ${reportedUserId} par ${reporterUserId}`
      );

      // Récupérer les informations du rapporteur
      const reporterDoc = await admin
        .firestore()
        .collection("users")
        .doc(reporterUserId)
        .get();

      if (!reporterDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur rapporteur non trouvé"
        );
      }

      const reporterData = reporterDoc.data();

      // Créer le document de signalement
      const reportData = {
        id: admin.firestore().collection("temp").doc().id,
        messageId: messageId,
        reportedUserId: reportedUserId,
        reportedUserName: reportedUserName || "Utilisateur inconnu",
        reporterUserId: reporterUserId,
        reporterUserName: reporterData.name || "Rapporteur inconnu",
        messageText: messageText.substring(0, 500), // Limiter la taille
        reason: reason,
        status: "pending", // pending, reviewed, resolved, dismissed
        reportedAt: admin.firestore.FieldValue.serverTimestamp(),
        reviewedAt: null,
        reviewedBy: null,
        moderationAction: null, // warning, temporary_ban, permanent_ban, none
        notes: "",
        severity: "medium", // low, medium, high, critical
      };

      // Sauvegarder le signalement
      await admin
        .firestore()
        .collection("content_reports")
        .doc(reportData.id)
        .set(reportData);

      console.log(
        `✅ reportInappropriateContent: Signalement sauvegardé: ${reportData.id}`
      );

      // NOUVEAU: Notification automatique aux administrateurs si contenu critique
      if (await isContentCritical(messageText)) {
        console.log(
          "🚨 reportInappropriateContent: Contenu critique détecté - notification admin"
        );
        await notifyAdministrators(reportData);
      }

      // Incrémenter les statistiques de signalement pour l'utilisateur signalé
      await admin
        .firestore()
        .collection("user_moderation_stats")
        .doc(reportedUserId)
        .set(
          {
            totalReports: admin.firestore.FieldValue.increment(1),
            lastReportedAt: admin.firestore.FieldValue.serverTimestamp(),
            pendingReports: admin.firestore.FieldValue.increment(1),
          },
          { merge: true }
        );

      console.log(
        `✅ reportInappropriateContent: Statistiques mises à jour pour ${reportedUserId}`
      );

      return {
        success: true,
        reportId: reportData.id,
        message: "Signalement enregistré avec succès",
        reviewTime: "24-48 heures",
      };
    } catch (error) {
      console.error("❌ reportInappropriateContent: Erreur", error);

      // Si c'est déjà une HttpsError, la relancer
      if (error.code && error.message) {
        throw error;
      }

      throw new functions.https.HttpsError(
        "internal",
        "Erreur lors du signalement"
      );
    }
  }
);

/**
 * Vérifier si le contenu est critique (mots-clés sensibles)
 */
async function isContentCritical(messageText) {
  const criticalKeywords = [
    "violence",
    "menace",
    "harcèlement",
    "suicide",
    "drogue",
    "illegal",
    // Ajouter d'autres mots-clés selon les besoins
  ];

  const lowerText = messageText.toLowerCase();
  return criticalKeywords.some((keyword) => lowerText.includes(keyword));
}

/**
 * Notifier les administrateurs d'un contenu critique
 */
async function notifyAdministrators(reportData) {
  try {
    console.log(
      "📧 notifyAdministrators: Notification admin pour rapport critique"
    );

    // Ici vous pouvez ajouter l'envoi d'emails ou notifications push aux admins
    // Pour l'instant, on log simplement
    console.log("📧 Admin notification:", {
      reportId: reportData.id,
      severity: "critical",
      reportedUser: reportData.reportedUserId,
      reason: reportData.reason,
    });

    // OPTIONNEL: Sauvegarder dans une collection d'alertes admin
    await admin.firestore().collection("admin_alerts").doc().set({
      type: "critical_content_report",
      reportId: reportData.id,
      severity: "high",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      resolved: false,
    });

    console.log("✅ notifyAdministrators: Alerte admin créée");
  } catch (error) {
    console.error("❌ notifyAdministrators: Erreur", error);
    // Ne pas faire échouer le signalement pour cette erreur
  }
}

/**
 * OPTIONNEL: Cloud Function pour récupérer les signalements (pour interface admin)
 */
exports.getContentReports = functions.https.onCall(async (data, context) => {
  try {
    // SÉCURITÉ: Vérifier que c'est un administrateur
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Authentification requise"
      );
    }

    // TODO: Ajouter vérification des droits admin
    // Pour l'instant, seulement pour debug/tests
    const { limit = 50, status = "pending" } = data;

    console.log(
      `📋 getContentReports: Récupération signalements (status: ${status})`
    );

    const reportsSnapshot = await admin
      .firestore()
      .collection("content_reports")
      .where("status", "==", status)
      .orderBy("reportedAt", "desc")
      .limit(limit)
      .get();

    const reports = reportsSnapshot.docs.map((doc) => ({
      id: doc.id,
      ...doc.data(),
    }));

    console.log(`✅ getContentReports: ${reports.length} signalements trouvés`);

    return {
      success: true,
      reports: reports,
      count: reports.length,
    };
  } catch (error) {
    console.error("❌ getContentReports: Erreur", error);
    throw new functions.https.HttpsError("internal", "Erreur serveur");
  }
});

// 🗑️ FONCTION DEBUG SUPPRIMÉE : fixDailyQuestionSettings
// Cette fonction était utilisée pour corriger manuellement les settings des questions quotidiennes
// SUPPRIMÉE car plus nécessaire après les corrections timezone automatiques

/**
 * 🌍 SOLUTION TIMEZONE UNIVERSELLE - Cron Horaire Optimisé
 * Gère toutes les timezones du monde automatiquement
 * S'exécute toutes les heures et check seulement les timezones pertinentes
 */
exports.hourlyGlobalTimezoneManager = functions.pubsub
  .schedule("0 * * * *") // Toutes les heures pile
  .timeZone("UTC") // Fixe en UTC pour cohérence
  .onRun(async (context) => {
    const startTime = Date.now();
    const currentUTCTime = new Date();
    const currentUTCHour = currentUTCTime.getUTCHours();
    const currentUTCMinute = currentUTCTime.getUTCMinutes();

    console.log("🌍 === TIMEZONE MANAGER START ===");
    console.log(`🕐 UTC Time: ${currentUTCTime.toISOString()}`);
    console.log(`🎯 Checking UTC Hour: ${currentUTCHour}`);

    try {
      // 🎯 OPTIMISATION: Mapper les timezones pertinentes pour cette heure UTC
      const timezonesToCheck = getTimezonesForUTCHour(currentUTCHour);

      console.log(
        `🔍 Timezones à vérifier (${timezonesToCheck.length}):`,
        timezonesToCheck
      );

      if (timezonesToCheck.length === 0) {
        console.log("⏭️  Aucune timezone pertinente pour cette heure - skip");
        return {
          success: true,
          skipped: true,
          reason: "no_relevant_timezones",
        };
      }

      // 📊 Stats tracking
      let couplesProcessed = 0;
      let questionsGenerated = 0;
      let notificationsSent = 0;
      let errors = 0;

      // 🔍 Récupérer les couples dans les timezones pertinentes
      const couplesQuery = admin
        .firestore()
        .collection("dailyQuestionSettings")
        .where("timezone", "in", timezonesToCheck);

      const couplesSnapshot = await couplesQuery.get();
      console.log(
        `📋 ${couplesSnapshot.size} couple(s) trouvé(s) dans les timezones pertinentes`
      );

      // 🔄 Traiter chaque couple
      for (const coupleDoc of couplesSnapshot.docs) {
        const coupleId = coupleDoc.id;
        const coupleData = coupleDoc.data();
        const userTimezone = coupleData.timezone || "Europe/Paris";

        couplesProcessed++;

        try {
          console.log(
            `\n👫 Couple ${couplesProcessed}/${couplesSnapshot.size}: ${coupleId}`
          );
          console.log(`🌍 Timezone: ${userTimezone}`);

          // 🕐 Calculer l'heure locale du couple
          const userLocalTime = getCurrentTimeInTimezone(userTimezone);
          const userHour = userLocalTime.getHours();
          const userMinute = userLocalTime.getMinutes();

          console.log(
            `⏰ Heure locale couple: ${userHour}:${userMinute
              .toString()
              .padStart(2, "0")}`
          );

          // ✅ Nouvelle question à minuit local (00:00)
          if (userHour === 0 && userMinute === 0) {
            console.log("🎯 MINUIT LOCAL - Génération nouvelle question");

            try {
              const result = await generateDailyQuestionForCouple(
                coupleId,
                userTimezone
              );
              if (result.success) {
                questionsGenerated++;
                console.log(`✅ Question générée: ${result.questionKey}`);
              } else {
                errors++;
                console.log(`❌ Erreur génération: ${result.error}`);
              }
            } catch (error) {
              errors++;
              console.log(`❌ Exception génération: ${error.message}`);
            }
          }

          // 🗑️ SUPPRIMÉ : Notification à 21h local
          // Cette logique envoyait des notifications de rappel à 21h si pas de réponse
          // SUPPRIMÉE car seules les notifications de messages sont souhaitées

          // ⏭️ Autres heures - skip
          else {
            console.log(`⏭️  Heure non critique (${userHour}h) - skip`);
          }
        } catch (error) {
          errors++;
          console.log(
            `❌ Erreur traitement couple ${coupleId}: ${error.message}`
          );
        }
      }

      // 📊 Rapport final
      const executionTime = Date.now() - startTime;
      const finalStats = {
        success: true,
        executionTimeMs: executionTime,
        utcHour: currentUTCHour,
        timezonesChecked: timezonesToCheck,
        stats: {
          couplesFound: couplesSnapshot.size,
          couplesProcessed,
          questionsGenerated,
          notificationsSent,
          errors,
        },
      };

      console.log("\n📊 === RAPPORT FINAL ===");
      console.log(`⏱️  Temps d'exécution: ${executionTime}ms`);
      console.log(
        `👫 Couples traités: ${couplesProcessed}/${couplesSnapshot.size}`
      );
      console.log(`❓ Questions générées: ${questionsGenerated}`);
      console.log(`🔔 Notifications envoyées: ${notificationsSent}`);
      console.log(`❌ Erreurs: ${errors}`);
      console.log("🌍 === TIMEZONE MANAGER END ===\n");

      return finalStats;
    } catch (error) {
      console.log(`❌ ERREUR GLOBALE Timezone Manager: ${error.message}`);
      console.log("Stack:", error.stack);

      return {
        success: false,
        error: error.message,
        executionTimeMs: Date.now() - startTime,
      };
    }
  });

/**
 * 🎯 Mapper les timezones pertinentes selon l'heure UTC
 * OPTIMISATION: Évite de checker tous les couples inutilement
 */
function getTimezonesForUTCHour(utcHour) {
  // 🗺️ Mapping intelligent des timezones principales
  const timezoneMap = {
    // UTC 0 = Minuit pour UTC+0, 21h pour UTC-3
    0: [
      "UTC",
      "Europe/London",
      "Atlantic/Reykjavik",
      "America/Argentina/Buenos_Aires",
      "America/Sao_Paulo",
    ],

    // UTC 1 = Minuit pour UTC+1, 21h pour UTC-2
    1: [
      "Europe/Paris",
      "Europe/Berlin",
      "Europe/Rome",
      "Europe/Madrid",
      "Africa/Lagos",
    ],

    // UTC 2 = Minuit pour UTC+2, 21h pour UTC-1
    2: [
      "Europe/Helsinki",
      "Europe/Athens",
      "Africa/Cairo",
      "Asia/Jerusalem",
      "Atlantic/Azores",
    ],

    // UTC 3 = Minuit pour UTC+3, 21h pour UTC+0
    3: ["Europe/Moscow", "Asia/Riyadh", "Africa/Nairobi", "Europe/London"],

    // UTC 4 = Minuit pour UTC+4, 21h pour UTC+1
    4: ["Asia/Dubai", "Asia/Baku", "Europe/Paris", "Europe/Berlin"],

    // UTC 5 = Minuit pour UTC+5, 21h pour UTC+2
    5: ["Asia/Karachi", "Asia/Tashkent", "Europe/Helsinki", "Europe/Athens"],

    // UTC 6 = Minuit pour UTC+6, 21h pour UTC+3
    6: ["Asia/Almaty", "Asia/Dhaka", "Europe/Moscow", "Asia/Riyadh"],

    // UTC 7 = Minuit pour UTC+7, 21h pour UTC+4
    7: ["Asia/Bangkok", "Asia/Jakarta", "Asia/Dubai", "Asia/Baku"],

    // UTC 8 = Minuit pour UTC+8, 21h pour UTC+5
    8: ["Asia/Shanghai", "Asia/Singapore", "Asia/Karachi", "Asia/Tashkent"],

    // UTC 9 = Minuit pour UTC+9, 21h pour UTC+6
    9: ["Asia/Tokyo", "Asia/Seoul", "Asia/Almaty", "Asia/Dhaka"],

    // UTC 10 = Minuit pour UTC+10, 21h pour UTC+7
    10: ["Australia/Sydney", "Pacific/Guam", "Asia/Bangkok", "Asia/Jakarta"],

    // UTC 11 = Minuit pour UTC+11, 21h pour UTC+8
    11: ["Pacific/Norfolk", "Asia/Magadan", "Asia/Shanghai", "Asia/Singapore"],

    // UTC 12 = Minuit pour UTC+12, 21h pour UTC+9
    12: ["Pacific/Auckland", "Pacific/Fiji", "Asia/Tokyo", "Asia/Seoul"],

    // UTC 13 = Minuit pour UTC+13, 21h pour UTC+10
    13: ["Pacific/Tongatapu", "Australia/Sydney", "Pacific/Guam"],

    // UTC 14 = Minuit pour UTC+14, 21h pour UTC+11
    14: ["Pacific/Kiritimati", "Pacific/Norfolk", "Asia/Magadan"],

    // UTC 15 = 21h pour UTC+12
    15: ["Pacific/Auckland", "Pacific/Fiji"],

    // UTC 16 = 21h pour UTC+13
    16: ["Pacific/Tongatapu"],

    // UTC 17 = 21h pour UTC+14
    17: ["Pacific/Kiritimati"],

    // UTC 18 = Minuit pour UTC-6, 21h pour UTC-3
    18: [
      "America/Chicago",
      "America/Mexico_City",
      "America/Argentina/Buenos_Aires",
      "America/Sao_Paulo",
    ],

    // UTC 19 = Minuit pour UTC-5, 21h pour UTC-2
    19: ["America/New_York", "America/Toronto", "Atlantic/Azores"],

    // UTC 20 = Minuit pour UTC-4, 21h pour UTC-1
    20: ["America/Caracas", "Atlantic/Bermuda"],

    // UTC 21 = Minuit pour UTC-3, 21h pour UTC+0
    21: [
      "America/Argentina/Buenos_Aires",
      "America/Sao_Paulo",
      "Europe/London",
    ],

    // UTC 22 = Minuit pour UTC-2, 21h pour UTC+1
    22: ["Atlantic/Azores", "Europe/Paris", "Europe/Berlin"],

    // UTC 23 = Minuit pour UTC-1, 21h pour UTC+2
    23: ["Atlantic/Cape_Verde", "Europe/Helsinki", "Europe/Athens"],
  };

  return timezoneMap[utcHour] || [];
}

/**
 * 🕐 Obtenir l'heure actuelle dans une timezone donnée
 */
function getCurrentTimeInTimezone(timezone) {
  try {
    const now = new Date();

    // Utiliser Intl.DateTimeFormat pour la conversion timezone
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    });

    const parts = formatter.formatToParts(now);
    const year = parseInt(parts.find((p) => p.type === "year").value);
    const month = parseInt(parts.find((p) => p.type === "month").value) - 1; // Month is 0-indexed
    const day = parseInt(parts.find((p) => p.type === "day").value);
    const hour = parseInt(parts.find((p) => p.type === "hour").value);
    const minute = parseInt(parts.find((p) => p.type === "minute").value);
    const second = parseInt(parts.find((p) => p.type === "second").value);

    return new Date(year, month, day, hour, minute, second);
  } catch (error) {
    console.log(`❌ Erreur conversion timezone ${timezone}: ${error.message}`);
    return new Date(); // Fallback UTC
  }
}

/**
 * 🎯 Générer une question quotidienne pour un couple spécifique
 */
async function generateDailyQuestionForCouple(coupleId, timezone) {
  try {
    console.log(`🎯 generateDailyQuestionForCouple: ${coupleId} (${timezone})`);

    // Récupérer les settings du couple
    const settingsDoc = await admin
      .firestore()
      .collection("dailyQuestionSettings")
      .doc(coupleId)
      .get();

    if (!settingsDoc.exists) {
      return { success: false, error: "Settings not found" };
    }

    const settings = settingsDoc.data();
    const currentDay = settings.currentDay || 1;
    const nextDay = currentDay + 1;

    console.log(`📊 Settings: currentDay=${currentDay}, nextDay=${nextDay}`);

    // 🔥 CORRECTION: Utiliser fonction commune au lieu de exports.run()
    const result = await generateDailyQuestionCore(coupleId, timezone, nextDay);

    return {
      success: true,
      questionKey: result.question?.questionKey,
      day: nextDay,
    };
  } catch (error) {
    console.log(`❌ Erreur generateDailyQuestionForCouple: ${error.message}`);
    return { success: false, error: error.message };
  }
}

// ================================
// 🎯 DAILY CHALLENGES FUNCTIONS
// ================================

/**
 * Retourne le nombre total de défis disponibles
 */
function getTotalChallengesCount() {
  return 53;
}

/**
 * Génère la clé de défi basée sur le jour
 */
function generateChallengeKey(challengeDay) {
  const totalChallenges = getTotalChallengesCount();

  // Cycle à travers les défis si on dépasse le total
  const challengeIndex = ((challengeDay - 1) % totalChallenges) + 1;
  return `daily_challenge_${challengeIndex}`;
}

/**
 * Calcule le jour actuel du défi basé sur les settings
 * Réutilise la même logique que calculateCurrentQuestionDay
 */
function calculateCurrentChallengeDay(settings, currentTime = new Date()) {
  const totalChallenges = getTotalChallengesCount();

  if (!settings || !settings.startDate) {
    console.log(
      "📅 calculateCurrentChallengeDay: Pas de settings ou startDate - retour jour 1"
    );
    return 1; // Première visite
  }

  // STANDARD: startDate est TOUJOURS un Timestamp côté Firebase
  const startDate = settings.startDate.toDate
    ? settings.startDate.toDate()
    : new Date(settings.startDate);

  console.log("📅 calculateCurrentChallengeDay: LOGS TIMEZONE DÉTAILLÉS");
  console.log(`📅 - startDate original: ${startDate.toISOString()}`);
  console.log(`📅 - currentTime: ${currentTime.toISOString()}`);

  // 🔧 CORRECTION: Utiliser la même logique que les questions du jour
  const startDateUTC = new Date(
    Date.UTC(
      startDate.getFullYear(),
      startDate.getMonth(),
      startDate.getDate(),
      0,
      0,
      0,
      0
    )
  );

  const currentTimeUTC = new Date(
    Date.UTC(
      currentTime.getFullYear(),
      currentTime.getMonth(),
      currentTime.getDate(),
      0,
      0,
      0,
      0
    )
  );

  console.log(`📅 - startDateUTC: ${startDateUTC.toISOString()}`);
  console.log(`📅 - currentTimeUTC: ${currentTimeUTC.toISOString()}`);

  // Calcul de la différence en jours (aligné avec les questions du jour)
  const timeDiff = currentTimeUTC.getTime() - startDateUTC.getTime();
  const daysSinceStart = Math.floor(timeDiff / (1000 * 3600 * 24));

  console.log(`📅 - timeDiff (ms): ${timeDiff}`);
  console.log(`📅 - daysSinceStart: ${daysSinceStart}`);

  // 🔧 CORRECTION : Logic plus robuste pour l'incrémentation (comme les questions)
  // Si on est le même jour que la création, currentDay = 1
  // Si on est le jour suivant, currentDay = 2, etc.
  const expectedDay = daysSinceStart + 1;

  console.log(`📅 - expectedDay calculé: ${expectedDay}`);
  console.log(`📅 - currentDay actuel: ${settings.currentDay || "non défini"}`);

  // CYCLE INFINI: Plus de limite sur totalChallenges
  const cycledDay = ((expectedDay - 1) % totalChallenges) + 1;

  console.log(`📅 - cycledDay final: ${cycledDay}/${totalChallenges}`);
  console.log("📅 calculateCurrentChallengeDay: FIN LOGS TIMEZONE");

  return cycledDay;
}

/**
 * Récupère ou crée les settings de défis quotidiens pour un couple
 */
async function getOrCreateDailyChallengeSettings(
  coupleId,
  timezone = "Europe/Paris"
) {
  try {
    console.log(
      `📅 getOrCreateDailyChallengeSettings: Récupération/création settings pour ${coupleId}`
    );

    const settingsRef = admin
      .firestore()
      .collection("dailyChallengeSettings")
      .doc(coupleId);

    const settingsDoc = await settingsRef.get();

    if (settingsDoc.exists) {
      console.log(
        `✅ getOrCreateDailyChallengeSettings: Settings existants trouvés pour ${coupleId}`
      );
      const data = settingsDoc.data();

      // OPTIMISATION : S'assurer que nextScheduledDate existe
      if (!data.nextScheduledDate) {
        console.log(
          `🔧 getOrCreateDailyChallengeSettings: Ajout nextScheduledDate manquant pour ${coupleId}`
        );

        const today = new Date();
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);

        await settingsRef.update({
          nextScheduledDate: admin.firestore.Timestamp.fromDate(tomorrow),
        });

        data.nextScheduledDate = admin.firestore.Timestamp.fromDate(tomorrow);
      }

      return data;
    }

    // CRÉATION : Nouveaux settings
    console.log(
      `🆕 getOrCreateDailyChallengeSettings: Création nouveaux settings pour ${coupleId}`
    );

    // 🔧 CORRECTION HARMONISATION : Utiliser UTC minuit comme les questions du jour
    const now = new Date();
    const startDateUTC = new Date(
      Date.UTC(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0)
    );

    console.log(`📅 getOrCreateDailyChallengeSettings: CRÉATION SETTINGS UTC:`);
    console.log(`📅 - now local: ${now.toISOString()}`);
    console.log(`📅 - startDateUTC: ${startDateUTC.toISOString()}`);
    console.log(`📅 - timezone: ${timezone}`);

    const tomorrow = new Date(startDateUTC);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const newSettings = {
      coupleId,
      startDate: admin.firestore.Timestamp.fromDate(startDateUTC), // 🔧 CORRECTION: UTC
      timezone,
      currentDay: 1,
      createdAt: admin.firestore.Timestamp.fromDate(new Date()),
      lastVisitDate: admin.firestore.Timestamp.fromDate(new Date()),
      nextScheduledDate: admin.firestore.Timestamp.fromDate(tomorrow),
    };

    await settingsRef.set(newSettings);

    console.log(
      `✅ getOrCreateDailyChallengeSettings: Settings créés avec succès pour ${coupleId}`
    );

    return newSettings;
  } catch (error) {
    console.error(
      `❌ getOrCreateDailyChallengeSettings: Erreur pour ${coupleId}:`,
      error
    );
    throw error;
  }
}

/**
 * Génère un défi quotidien pour un couple
 */
async function generateDailyChallengeForCouple(coupleId, timezone) {
  try {
    console.log(
      `🎯 generateDailyChallengeForCouple: ${coupleId} (${timezone})`
    );

    // Récupérer les settings du couple
    const settingsDoc = await admin
      .firestore()
      .collection("dailyChallengeSettings")
      .doc(coupleId)
      .get();

    if (!settingsDoc.exists) {
      return { success: false, error: "Settings not found" };
    }

    const settings = settingsDoc.data();
    const currentDay = settings.currentDay || 1;

    console.log(`📊 Settings: currentDay=${currentDay}`);
    console.log(
      `🔧 CORRECTION: Laisser generateDailyChallenge calculer automatiquement le jour (pas de forçage nextDay)`
    );

    // 🔥 CORRECTION: Utiliser fonction commune au lieu de exports.run()
    const result = await generateDailyChallengeCore(coupleId, timezone);

    return {
      success: true,
      challengeKey: result.challenge?.challengeKey,
      day: result.challenge?.challengeDay || currentDay, // ✅ CORRECTION: utiliser le jour calculé
      challengeId: result.challenge?.id,
    };
  } catch (error) {
    console.log(`❌ Erreur generateDailyChallengeForCouple: ${error.message}`);
    return { success: false, error: error.message };
  }
}

/**
 * 🔥 CORRECTION: Fonction commune pour génération défis (sans auth/rate limiting)
 */
async function generateDailyChallengeCore(
  coupleId,
  timezone,
  challengeDay = null
) {
  console.log(
    `🎯 generateDailyChallengeCore: coupleId=${coupleId}, timezone=${timezone}`
  );

  try {
    // Récupérer ou créer les settings
    const settings = await getOrCreateDailyChallengeSettings(
      coupleId,
      timezone
    );

    // Calculer le jour si pas fourni
    const targetDay = challengeDay || calculateCurrentChallengeDay(settings);

    const today = new Date();
    const todayString = today.toISOString().split("T")[0];

    // Vérifier si défi existe déjà (idempotence)
    const challengeId = `${coupleId}_${todayString}`;
    const existingDoc = await admin
      .firestore()
      .collection("dailyChallenges")
      .doc(challengeId)
      .get();

    if (existingDoc.exists) {
      console.log(`✅ Défi déjà existant pour ${coupleId} jour ${targetDay}`);
      return {
        success: true,
        challenge: existingDoc.data(),
        alreadyExists: true,
      };
    }

    // Générer nouveau défi
    const challengeKey = generateChallengeKey(targetDay);
    const challengeData = {
      id: challengeId,
      coupleId,
      challengeKey,
      challengeDay: targetDay,
      scheduledDate: admin.firestore.Timestamp.fromDate(today),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      isCompleted: false,
    };

    // Sauvegarder
    await admin
      .firestore()
      .collection("dailyChallenges")
      .doc(challengeId)
      .set(challengeData);

    console.log(`✅ Défi généré: ${challengeKey} pour couple ${coupleId}`);
    return {
      success: true,
      challenge: challengeData,
      generated: true,
    };
  } catch (error) {
    console.error(`❌ Erreur génération défi pour ${coupleId}:`, error);
    throw error;
  }
}

/**
 * Cloud Function HTTP pour générer un défi quotidien
 */
exports.generateDailyChallenge = functions.https.onCall(
  async (data, context) => {
    try {
      // Vérification de l'authentification
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Authentication required"
        );
      }

      const { coupleId, challengeDay, timezone = "Europe/Paris" } = data;
      const userId = context.auth.uid;

      console.log(
        `🎯 generateDailyChallenge appelée par ${userId} pour ${coupleId}, jour ${challengeDay}`
      );

      // Rate limiting
      await checkRateLimit(userId, "generateDailyChallenge", { coupleId });

      if (!coupleId) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "coupleId is required"
        );
      }

      // Récupérer ou créer les settings
      const settings = await getOrCreateDailyChallengeSettings(
        coupleId,
        timezone
      );

      // Calculer le jour actuel (aligné avec les questions du jour)
      // 🔧 CORRECTION: Utiliser new Date() comme pour les questions du jour
      const currentTime = new Date();
      const calculatedDay = calculateCurrentChallengeDay(settings, currentTime);

      console.log(
        `📊 Jour calculé: ${calculatedDay}, Jour demandé: ${
          challengeDay || "auto"
        }`
      );

      const targetDay = challengeDay || calculatedDay;
      const challengeKey = generateChallengeKey(targetDay);

      // 🔧 IDEMPOTENCE: Vérifier si le défi d'aujourd'hui existe déjà
      const todayDateStr = new Date().toISOString().split("T")[0];
      const todayId = `${coupleId}_${todayDateStr}`;

      console.log(`🔍 Vérification existence défi: ${todayId}`);

      const existingTodayDoc = await admin
        .firestore()
        .collection("dailyChallenges")
        .doc(todayId)
        .get();

      if (existingTodayDoc.exists) {
        console.log(
          `✅ Défi d'aujourd'hui déjà présent: ${todayId}, retour sans génération`
        );
        const existingData = existingTodayDoc.data();
        return {
          success: true,
          challenge: existingData,
          settings: {
            ...settings,
            currentDay: targetDay,
          },
        };
      }

      console.log(`🆕 Aucun défi trouvé pour ${todayId}, génération autorisée`);

      // 🧹 CLEANUP: Supprimer automatiquement le défi d'hier AVANT de créer celui d'aujourd'hui
      const today = new Date(currentTime);
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayString = yesterday.toISOString().split("T")[0];

      console.log(
        `🧹 generateDailyChallenge: Vérification suppression défi d'hier: ${yesterdayString}`
      );

      try {
        const yesterdayChallengeRef = admin
          .firestore()
          .collection("dailyChallenges")
          .doc(`${coupleId}_${yesterdayString}`);

        const yesterdayDoc = await yesterdayChallengeRef.get();
        if (yesterdayDoc.exists) {
          console.log(
            `🧹 generateDailyChallenge: Suppression défi d'hier trouvé: ${yesterdayString}`
          );

          await yesterdayChallengeRef.delete();

          console.log(
            `✅ generateDailyChallenge: Défi d'hier supprimé avec succès: ${yesterdayString}`
          );
        } else {
          console.log(
            `✅ generateDailyChallenge: Aucun défi d'hier à supprimer: ${yesterdayString}`
          );
        }
      } catch (error) {
        console.error(
          `⚠️ generateDailyChallenge: Erreur suppression défi d'hier:`,
          error
        );
        // Ne pas bloquer la génération pour une erreur de cleanup
      }

      // Créer l'ID du défi
      const dateStr = currentTime.toISOString().split("T")[0];
      const challengeId = `${coupleId}_${dateStr}`;

      // Vérifier si le défi existe déjà
      const existingChallengeDoc = await admin
        .firestore()
        .collection("dailyChallenges")
        .doc(challengeId)
        .get();

      let challenge;

      if (existingChallengeDoc.exists) {
        console.log(`✅ Défi existant trouvé: ${challengeId}`);
        challenge = existingChallengeDoc.data();
      } else {
        // Créer nouveau défi
        console.log(`🆕 Création nouveau défi: ${challengeId}`);

        challenge = {
          id: challengeId,
          challengeKey,
          challengeDay: targetDay,
          scheduledDate: admin.firestore.Timestamp.fromDate(currentTime),
          coupleId,
          isCompleted: false,
          createdAt: admin.firestore.Timestamp.fromDate(new Date()),
        };

        await admin
          .firestore()
          .collection("dailyChallenges")
          .doc(challengeId)
          .set(challenge);

        // Mettre à jour les settings si nécessaire
        if (targetDay > (settings.currentDay || 0)) {
          await admin
            .firestore()
            .collection("dailyChallengeSettings")
            .doc(coupleId)
            .update({
              currentDay: targetDay,
              lastVisitDate: admin.firestore.Timestamp.fromDate(new Date()),
            });
        }
      }

      console.log(`✅ generateDailyChallenge: Défi retourné pour ${coupleId}`);

      return {
        success: true,
        challenge,
        settings: {
          ...settings,
          currentDay: targetDay,
        },
      };
    } catch (error) {
      console.error("❌ generateDailyChallenge error:", error);

      if (error instanceof functions.https.HttpsError) {
        throw error;
      }

      throw new functions.https.HttpsError(
        "internal",
        "Internal server error",
        error.message
      );
    }
  }
);

/**
 * Fonction programmée pour générer les défis quotidiens
 * Exécutée chaque jour à 00:00 UTC
 */
exports.scheduledDailyChallengeGeneration = functions.pubsub
  .schedule("0 0 * * *")
  .timeZone("UTC")
  .onRun(async (context) => {
    console.log("🕛 scheduledDailyChallengeGeneration: Démarrage");

    try {
      // Récupérer tous les couples avec des settings de défis
      const settingsSnapshot = await admin
        .firestore()
        .collection("dailyChallengeSettings")
        .get();

      if (settingsSnapshot.empty) {
        console.log("📊 Aucun couple trouvé pour génération défis");
        return null;
      }

      console.log(
        `📊 ${settingsSnapshot.size} couple(s) trouvé(s) pour génération défis`
      );

      const promises = [];
      settingsSnapshot.forEach((doc) => {
        const settings = doc.data();
        const coupleId = doc.id;

        console.log(`🎯 Programmation génération défi pour ${coupleId}`);

        promises.push(
          generateDailyChallengeForCouple(
            coupleId,
            settings.timezone || "Europe/Paris"
          )
        );
      });

      const results = await Promise.allSettled(promises);

      let successCount = 0;
      let errorCount = 0;

      results.forEach((result, index) => {
        if (result.status === "fulfilled") {
          successCount++;
          console.log(
            `✅ Génération défi ${index + 1}: ${
              result.value.success ? "Succès" : "Échec"
            }`
          );
        } else {
          errorCount++;
          console.error(`❌ Génération défi ${index + 1}:`, result.reason);
        }
      });

      console.log(
        `🎯 scheduledDailyChallengeGeneration terminé: ${successCount} succès, ${errorCount} erreurs`
      );

      return null;
    } catch (error) {
      console.error("❌ scheduledDailyChallengeGeneration error:", error);
      return null;
    }
  });

// 🗑️ FONCTION SUPPRIMÉE : sendReminderNotificationIfNeeded
// Cette fonction vérifiait si une notification de rappel était nécessaire à 21h
// SUPPRIMÉE car seules les notifications de messages sont souhaitées

// 🗑️ FONCTION SUPPRIMÉE : sendReminderNotification
// Cette fonction envoyait des notifications de rappel avec templates localisés
// SUPPRIMÉE car seules les notifications de messages entre partenaires sont souhaitées
