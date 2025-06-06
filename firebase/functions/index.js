const functions = require("firebase-functions");
const admin = require("firebase-admin");
const appleReceiptVerify = require("node-apple-receipt-verify");
const jwt = require("jsonwebtoken");

admin.initializeApp();

// Configuration App Store Connect API (avec valeurs par défaut)
const APP_STORE_CONNECT_CONFIG = {
  keyId: functions.config().apple?.key_id || "",
  issuerId: functions.config().apple?.issuer_id || "",
  bundleId: "com.lyes.love2love",
  privateKey: functions.config().apple?.private_key || "",
  environment: functions.config().apple?.environment || "sandbox", // 'sandbox' ou 'production'
};

/**
 * Valider un reçu d'achat Apple
 */
exports.validateAppleReceipt = functions.https.onCall(async (data, context) => {
  try {
    console.log("🔥 validateAppleReceipt: Début de la validation");

    const { receiptData, productId } = data;

    if (!receiptData) {
      console.log("🔥 validateAppleReceipt: Données de reçu manquantes");
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Données de reçu manquantes"
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
      environment: [environment],
      verbose: true,
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

        await userRef.update({
          subscription: subscriptionData,
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
    throw new functions.https.HttpsError("internal", error.message);
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
          "subscription.originalTransactionId",
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

        await userDoc.ref.update({
          subscription: subscriptionData,
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
          "subscription.originalTransactionId",
          "==",
          originalTransactionId
        )
        .get();

      if (!usersSnapshot.empty) {
        const userDoc = usersSnapshot.docs[0];

        await userDoc.ref.update({
          "subscription.isSubscribed": false,
          "subscription.lastValidated":
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
          "subscription.originalTransactionId",
          "==",
          originalTransactionId
        )
        .get();

      if (!usersSnapshot.empty) {
        const userDoc = usersSnapshot.docs[0];

        await userDoc.ref.update({
          "subscription.isSubscribed": false,
          "subscription.cancelledDate":
            admin.firestore.FieldValue.serverTimestamp(),
          "subscription.lastValidated":
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
      const subscription = userData.subscription || {};

      // Vérifier si l'abonnement est encore valide
      const now = new Date();
      const expiresDate = subscription.expiresDate
        ? subscription.expiresDate.toDate()
        : null;

      const isActive =
        subscription.isSubscribed && (!expiresDate || expiresDate > now);

      return {
        isSubscribed: isActive,
        subscription: subscription,
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

    // Étape 1: Supprimer le document utilisateur de Firestore
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

    // Étape 2: Supprimer toutes les données associées (abonnements temporaires, etc.)
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

    // Étape 3: Supprimer le compte Firebase Auth
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
