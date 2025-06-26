// Fonction sécurisée pour récupérer des URLs signées d'images
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
    // Vérifier que le chemin est autorisé (seulement certains dossiers)
    const allowedPaths = ["journal_images/", "profile_images/"];
    const isAllowedPath = allowedPaths.some((path) =>
      filePath.startsWith(path)
    );

    if (!isAllowedPath) {
      console.log(`❌ getSignedImageURL: Chemin non autorisé: ${filePath}`);
      throw new functions.https.HttpsError(
        "permission-denied",
        "Chemin de fichier non autorisé"
      );
    }

    // Extraire l'ID utilisateur du chemin
    const pathParts = filePath.split("/");
    if (pathParts.length < 2) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Format de chemin invalide"
      );
    }

    const fileOwnerId = pathParts[1];

    // Vérifier les permissions d'accès
    let hasAccess = false;

    if (currentUserId === fileOwnerId) {
      // Propriétaire du fichier
      hasAccess = true;
      console.log("✅ getSignedImageURL: Accès autorisé - Propriétaire");
    } else {
      // Vérifier si c'est un partenaire connecté
      const currentUserDoc = await admin
        .firestore()
        .collection("users")
        .doc(currentUserId)
        .get();

      const fileOwnerDoc = await admin
        .firestore()
        .collection("users")
        .doc(fileOwnerId)
        .get();

      if (currentUserDoc.exists && fileOwnerDoc.exists) {
        const currentUserData = currentUserDoc.data();
        const fileOwnerData = fileOwnerDoc.data();

        // Vérification bidirectionnelle des partenaires (multiple champs)
        const isPartnerViaPartnerId =
          currentUserData.partnerId === fileOwnerId &&
          fileOwnerData.partnerId === currentUserId;

        const isPartnerViaConnectedPartnerId =
          currentUserData.connectedPartnerId === fileOwnerId &&
          fileOwnerData.connectedPartnerId === currentUserId;

        if (isPartnerViaPartnerId || isPartnerViaConnectedPartnerId) {
          hasAccess = true;
          console.log(
            "✅ getSignedImageURL: Accès autorisé - Partenaire connecté"
          );
        }
      }
    }

    if (!hasAccess) {
      console.log("❌ getSignedImageURL: Accès refusé");
      throw new functions.https.HttpsError(
        "permission-denied",
        "Accès non autorisé à ce fichier"
      );
    }

    // Générer l'URL signée avec expiration courte (15 minutes)
    const bucket = admin.storage().bucket();
    const file = bucket.file(filePath);

    // Vérifier que le fichier existe
    const [exists] = await file.exists();
    if (!exists) {
      console.log(`❌ getSignedImageURL: Fichier inexistant: ${filePath}`);
      throw new functions.https.HttpsError("not-found", "Fichier non trouvé");
    }

    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 15 * 60 * 1000, // 15 minutes
    });

    console.log("✅ getSignedImageURL: URL signée générée avec succès");
    return { signedUrl };
  } catch (error) {
    console.log(`❌ getSignedImageURL: Erreur: ${error.message}`);

    if (error instanceof functions.https.HttpsError) {
      throw error;
    }

    throw new functions.https.HttpsError(
      "internal",
      "Erreur lors de la génération de l'URL signée"
    );
  }
});
