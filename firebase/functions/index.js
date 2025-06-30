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
