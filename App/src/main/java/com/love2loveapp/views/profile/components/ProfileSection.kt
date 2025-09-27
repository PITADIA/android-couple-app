package com.love2loveapp.views.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 📱 ProfileSection - Section organisée du profil
 * 
 * Équivalent des sections iOS MenuView :
 * - Titre de section
 * - Liste d'éléments ProfileRowItem
 * - Espacement et design cohérents
 */
@Composable
fun ProfileSection(
    title: String,
    items: List<ProfileItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 🏷️ TITRE SECTION
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        )

        // 📋 ITEMS SECTION
        items.forEach { item ->
            ProfileRowItem(
                item = item,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 🎯 ProfileItem - Données pour un élément de profil
 */
data class ProfileItem(
    val title: String,
    val value: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val textColor: Color = Color.Black,
    val showChevron: Boolean = true,
    val onClick: () -> Unit
)

/**
 * 🔗 ProfileRowItem - Élément individuel de profil
 * 
 * Équivalent iOS ProfileRowView avec :
 * - Icône optionnelle à gauche
 * - Titre et valeur
 * - Sous-titre optionnel
 * - Chevron à droite
 * - Couleur personnalisable
 */
@Composable
fun ProfileRowItem(
    item: ProfileItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🎨 CONTENU PRINCIPAL (icône + texte)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🎭 ICÔNE OPTIONNELLE
                item.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFFFF6B9D)
                    )
                }

                // 📝 TEXTES
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Titre principal
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = item.textColor
                    )

                    // Valeur/description
                    if (item.value.isNotEmpty()) {
                        Text(
                            text = item.value,
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Sous-titre optionnel
                    item.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF999999),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // ▶️ CHEVRON DROIT
            if (item.showChevron) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF999999)
                )
            }
        }
    }
}

/**
 * 🎨 Variations de ProfileRowItem
 */

/**
 * ⭐ ProfileRowItem premium - Pour éléments premium
 */
@Composable
fun ProfileRowItemPremium(
    item: ProfileItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8F0) // Fond légèrement doré
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ⭐ Icône premium
                item.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFFFFB300) // Doré
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = item.textColor
                        )
                        
                        // Badge Premium
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFB300)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "PREMIUM",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.value.isNotEmpty()) {
                        Text(
                            text = item.value,
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Normal
                        )
                    }

                    item.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF999999),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            if (item.showChevron) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFFFFB300)
                )
            }
        }
    }
}

/**
 * ⚠️ ProfileRowItem danger - Pour éléments dangereux (suppression)
 */
@Composable
fun ProfileRowItemDanger(
    item: ProfileItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF5F5) // Fond légèrement rouge
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Red
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Red
                    )

                    if (item.value.isNotEmpty()) {
                        Text(
                            text = item.value,
                            fontSize = 14.sp,
                            color = Color(0xFF999999),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            if (item.showChevron) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Red
                )
            }
        }
    }
}
