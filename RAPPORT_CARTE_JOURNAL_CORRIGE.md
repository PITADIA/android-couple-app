# Rapport Technique : Système de Carte Interactive - CoupleApp Journal (Version Corrigée)

## 🎨 Affichage des Événements sur la Carte - Version Corrigée

### 7. Affichage d'un Événement Unique

**Composant :** `OptimizedJournalMapAnnotationView`

```swift
struct OptimizedJournalMapAnnotationView: View {
    let entry: JournalEntry
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 4) {
                // Section principale avec image/icône + titre
                VStack(spacing: 6) {

                    // 🖼️ PARTIE IMAGE/ICÔNE
                    if let imageURL = entry.imageURL, !imageURL.isEmpty {
                        // ✅ ÉVÉNEMENT AVEC IMAGE
                        CachedMapImageView(imageURL: imageURL, size: 60)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white, lineWidth: 2)
                            )
                            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                    } else {
                        // ✅ ÉVÉNEMENT SANS IMAGE - Icône cœur
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(hex: "#FD267A"))
                            .frame(width: 60, height: 60)
                            .overlay(
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.white)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white, lineWidth: 2)
                            )
                            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                    }

                    // 📝 TITRE DE L'ÉVÉNEMENT (CORRECTION : pas la date !)
                    Text(entry.title)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color.white)
                                .shadow(color: .black.opacity(0.2), radius: 2, x: 0, y: 1)
                        )
                        .lineLimit(2)                    // Maximum 2 lignes
                        .multilineTextAlignment(.center) // Centré
                        .frame(maxWidth: 100)            // Largeur max 100px
                }
            }
        }
        .scaleEffect(0.9) // Légèrement plus petit pour ne pas encombrer
    }
}
```

**Design événement unique (VERSION CORRIGÉE) :**

- **Avec image** : Photo 60x60px de l'événement, bordure blanche 2px, ombre portée
- **Sans image** : Rectangle rose `#FD267A` avec icône cœur blanc (24px)
- **Badge titre** : **TITRE de l'événement** (pas la date !) en bas sur fond blanc, max 2 lignes, 100px de large

## 🖼️ Détail du Système d'Affichage des Images

### Comment l'Image Apparaît sur la Carte

#### 1. **Vérification de l'Image**

```swift
if let imageURL = entry.imageURL, !imageURL.isEmpty {
    // L'événement a une image → Afficher l'image
    CachedMapImageView(imageURL: imageURL, size: 60)
} else {
    // Pas d'image → Afficher l'icône cœur
    RoundedRectangle(cornerRadius: 8)
        .fill(Color(hex: "#FD267A"))
        .overlay(Image(systemName: "heart.fill"))
}
```

#### 2. **Composant d'Image Optimisé** - `CachedMapImageView`

```swift
struct CachedMapImageView: View {
    let imageURL: String
    let size: CGFloat

    @State private var image: UIImage?
    @State private var isLoading = false

    var body: some View {
        Group {
            if let image = image {
                // ✅ Image chargée avec succès
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)  // Remplit le cadre
                    .frame(width: size, height: size) // 60x60 pixels
                    .clipped()                        // Coupe les dépassements
                    .cornerRadius(8)                  // Coins arrondis
            } else if isLoading {
                // ⏳ État de chargement
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: size, height: size)
                    .overlay(
                        ProgressView()
                            .scaleEffect(0.7)
                    )
            } else {
                // ❌ Erreur ou pas d'image
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: size, height: size)
                    .overlay(
                        Image(systemName: "photo")
                            .font(.title2)
                            .foregroundColor(.gray)
                    )
            }
        }
        .task {
            await loadImageIfNeeded()
        }
    }
}
```

#### 3. **Processus de Chargement d'Image**

```
1. Init du composant
   ↓
2. Vérification cache synchrone
   ↓
3. Si pas en cache → Chargement asynchrone
   ↓
4. Affichage ProgressView pendant chargement
   ↓
5. Image téléchargée depuis Firebase Storage
   ↓
6. Mise en cache automatique
   ↓
7. Affichage de l'image finale
```

#### 4. **États Visuels**

- **Chargé** : Image réelle 60x60, coins arrondis, aspect fill
- **Loading** : Rectangle gris avec ProgressView animé
- **Erreur** : Rectangle gris avec icône photo
- **Pas d'image** : Rectangle rose avec cœur blanc

## 🤖 Implémentation Android - Images sur Carte

### 1. **Structure Marker Android**

```kotlin
// Équivalent OptimizedJournalMapAnnotationView
class JournalMarkerRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<JournalClusterItem>
) : DefaultClusterRenderer<JournalClusterItem>(context, map, clusterManager) {

    override fun onBeforeClusterItemRendered(
        item: JournalClusterItem,
        markerOptions: MarkerOptions
    ) {
        val customView = createCustomMarkerView(item)
        val icon = BitmapDescriptorFactory.fromBitmap(
            createBitmapFromView(customView)
        )
        markerOptions.icon(icon)
    }

    private fun createCustomMarkerView(item: JournalClusterItem): View {
        val markerView = LayoutInflater.from(context)
            .inflate(R.layout.custom_journal_marker, null)

        val imageView = markerView.findViewById<ImageView>(R.id.event_image)
        val titleView = markerView.findViewById<TextView>(R.id.event_title)

        // Configuration du titre
        titleView.text = item.title
        titleView.maxLines = 2
        titleView.ellipsize = TextUtils.TruncateAt.END

        // Configuration de l'image
        loadImageIntoMarker(item, imageView)

        return markerView
    }
}
```

### 2. **Layout XML du Marker**

```xml
<!-- res/layout/custom_journal_marker.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center">

    <!-- Image ou icône de l'événement -->
    <FrameLayout
        android:layout_width="60dp"
        android:layout_height="60dp">

        <ImageView
            android:id="@+id/event_image"
            android:layout_width="60dp"
            android:layout_height="60dp"
            android:scaleType="centerCrop"
            android:background="@drawable/rounded_corner_white_border"
            android:elevation="4dp" />

        <!-- Icône cœur de fallback -->
        <ImageView
            android:id="@+id/heart_icon"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:layout_gravity="center"
            android:src="@drawable/ic_heart_filled"
            android:tint="@android:color/white"
            android:visibility="gone" />
    </FrameLayout>

    <!-- Titre de l'événement -->
    <TextView
        android:id="@+id/event_title"
        android:layout_width="100dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:background="@drawable/title_background_white"
        android:padding="8dp"
        android:textSize="12sp"
        android:textStyle="bold"
        android:textColor="@android:color/black"
        android:gravity="center"
        android:maxLines="2"
        android:ellipsize="end"
        android:elevation="2dp" />

</LinearLayout>
```

### 3. **Chargement d'Images avec Glide**

```kotlin
private fun loadImageIntoMarker(item: JournalClusterItem, imageView: ImageView) {
    val heartIcon = markerView.findViewById<ImageView>(R.id.heart_icon)

    if (!item.imageUrl.isNullOrEmpty()) {
        // ✅ Événement avec image
        heartIcon.visibility = View.GONE

        Glide.with(context)
            .load(item.imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(CenterCrop(), RoundedCorners(16))
            .placeholder(R.drawable.loading_placeholder)
            .error(R.drawable.ic_heart_filled_pink) // Fallback vers cœur rose
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    // En cas d'erreur, afficher le cœur
                    showHeartIcon(imageView, heartIcon)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    // Image chargée avec succès
                    return false
                }
            })
            .into(imageView)
    } else {
        // ✅ Événement sans image → Afficher cœur
        showHeartIcon(imageView, heartIcon)
    }
}

private fun showHeartIcon(imageView: ImageView, heartIcon: ImageView) {
    imageView.setBackgroundColor(Color.parseColor("#FD267A"))
    heartIcon.visibility = View.VISIBLE
}
```

### 4. **Drawables Android**

```xml
<!-- res/drawable/rounded_corner_white_border.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="8dp" />
    <stroke android:width="2dp" android:color="@android:color/white" />
</shape>

<!-- res/drawable/title_background_white.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="6dp" />
    <solid android:color="@android:color/white" />
    <stroke android:width="1dp" android:color="#E0E0E0" />
</shape>
```

### 5. **Conversion View vers Bitmap**

```kotlin
private fun createBitmapFromView(view: View): Bitmap {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    view.layout(0, 0, view.measuredWidth, view.measuredHeight)

    val bitmap = Bitmap.createBitmap(
        view.measuredWidth,
        view.measuredHeight,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    view.draw(canvas)

    return bitmap
}
```

## 📋 Résumé des Corrections

### ✅ **Ce qui était Incorrect dans mon Premier Rapport :**

- J'avais dit que c'était la **date** qui s'affichait sous l'image ❌

### ✅ **Correction Apportée :**

- C'est le **titre de l'événement** qui s'affiche sous l'image ✅
- Limité à 2 lignes maximum
- Largeur maximale de 100px
- Centré sous l'annotation
- Fond blanc avec ombre portée

### 🖼️ **Affichage des Images - Récapitulatif :**

**Avec Image :**

1. Image 60x60px depuis Firebase Storage
2. Cache multi-niveaux (mémoire + disque)
3. Bordure blanche 2px + ombre portée
4. Coins arrondis 8px
5. Titre en dessous sur fond blanc

**Sans Image :**

1. Rectangle rose `#FD267A`
2. Icône cœur blanc 24px centré
3. Même bordure et ombre
4. Titre en dessous identique

**Pour Android :**

- Utiliser **Custom Marker View** avec Layout XML
- **Glide** pour chargement images optimisé
- **Conversion View → Bitmap** pour Google Maps
- **États loading/error/success** identiques à iOS
- **Cache automatique** via Glide DiskCacheStrategy.ALL

Tout le reste du rapport était correct ! 🚀
