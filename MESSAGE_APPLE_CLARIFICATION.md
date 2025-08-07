# Message pour Apple Developer Support

## Objet : Clarification sur la conformité du système de partage d'abonnements entre couples

---

**Bonjour Apple Developer Support,**

Je développe une application iOS pour couples (CoupleApp) qui a déjà été approuvée et publiée sur l'App Store. Je souhaite obtenir une clarification officielle concernant notre système de partage d'abonnements pour m'assurer de la conformité continue.

## **Description technique de notre système :**

Notre application permet aux couples de se connecter via des codes temporaires (24h d'expiration) et implémente un système de partage d'abonnements avec les caractéristiques suivantes :

**Architecture technique :**

- Génération de codes partenaires sécurisés (8 chiffres, expiration 24h)
- Validation via Firebase Cloud Functions avec contrôles anti-fraude
- Partage automatique d'abonnement lors de la connexion partenaire
- Un seul abonnement actif maximum par couple (limitation 1:1)
- Révocation immédiate lors de la déconnexion

**Logique de partage :**

```
Si Utilisateur A a un abonnement actif ET se connecte avec Utilisateur B
→ Utilisateur B obtient accès aux fonctionnalités premium
→ Type d'abonnement marqué comme "shared_from_partner"
→ Logs de conformité enregistrés pour tracking
```

**Mesures de sécurité :**

- Codes à usage unique avec expiration forcée
- Prévention des connexions multiples et auto-connexion
- Audit trail complet des partages d'abonnements
- Déconnexion volontaire disponible à tout moment

## **Question spécifique :**

Ce système est-il conforme aux App Store Review Guidelines actuelles, notamment :

- Section 3.1.3 (In-App Purchase)
- Règles de partage d'abonnements entre utilisateurs non-familiaux

## **Contexte :**

- Application déjà approuvée et disponible sur l'App Store
- Système similaire utilisé par d'autres applications couples populaires
- Aucun contournement du système de paiement Apple (StoreKit utilisé)
- Partage limité aux couples connectés volontairement

## **Demande :**

Pouvez-vous confirmer si ce système nécessite des ajustements pour maintenir la conformité, ou s'il peut continuer à fonctionner en l'état ?

Si des modifications sont nécessaires, pourriez-vous indiquer les points spécifiques à ajuster ?

**Merci pour votre temps et vos clarifications.**

Cordialement,
[Votre nom]
Développeur iOS
[Votre Apple Developer Account]

---

_App ID: [Votre App ID]_
_Bundle ID: [Votre Bundle ID]_
