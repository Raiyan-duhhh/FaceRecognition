package com.example.facerecognition.ui.theme

import androidx.compose.ui.graphics.Color

// ── Legacy (kept for backward compatibility) ─────────────────────────────────
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ═════════════════════════════════════════════════════════════════════════════
// FaceAttend V3.0 — Single Source of Truth Design Tokens
// ═════════════════════════════════════════════════════════════════════════════

// ── Zinc (dark surface hierarchy) ────────────────────────────────────────────
val Zinc950 = Color(0xFF09090B)   // Primary background
val Zinc900 = Color(0xFF18181B)   // Cards, elevated surfaces
val Zinc800 = Color(0xFF27272A)   // Subtle dividers, snackbars
val Zinc700 = Color(0xFF3F3F46)   // Borders, inactive elements

// ── Glass (translucent overlays) ─────────────────────────────────────────────
val GlassBg = Color.White.copy(alpha = 0.05f)
val GlassBorder = Color.White.copy(alpha = 0.10f)
val GlassBorderHover = Color.White.copy(alpha = 0.15f)

// ── Text ─────────────────────────────────────────────────────────────────────
val TextPrimary = Color.White
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF71717A)

// ── Accent ───────────────────────────────────────────────────────────────────
val AccentGreen = Color(0xFF10B981)    // Approve, success, on-time
val AccentRed = Color(0xFFEF4444)      // Reject, error, delete
val AccentBlue = Color(0xFF3B82F6)     // Edit, links, primary actions
val AccentAmber = Color(0xFFF59E0B)    // Warnings, pending badges
val AccentTeal = Color(0xFF00BCD4)     // Scanning state