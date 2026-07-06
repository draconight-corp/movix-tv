package com.example.movix.config

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.movix.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI Leanback-compatible pour gérer le domaine Movix actif.
 * Utilise un AlertDialog avec liste d'actions (D-pad-friendly, pas de
 * saisie texte sauf en dernier recours).
 */
object SettingsDialog {

    fun show(ctx: Context, scope: CoroutineScope) {
        val current = MovixConfig.currentApiHost(ctx)
        val override = MovixConfig.manualOverride(ctx)
        val resolved = MovixConfig.lastResolved(ctx)
        val resolvedAt = MovixConfig.lastResolvedAt(ctx)
        val resolvedAtStr = if (resolvedAt > 0)
            SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(resolvedAt))
        else "jamais"

        val info = buildString {
            appendLine("Actuel : $current")
            appendLine()
            appendLine("Override manuel : ${override ?: "—"}")
            appendLine("Auto-détecté : ${resolved ?: "—"} (à $resolvedAtStr)")
        }

        val actions = mutableListOf<Pair<String, () -> Unit>>()

        actions += "Re-détecter depuis movix.health" to {
            redetect(ctx, scope)
        }

        actions += "Choisir un domaine connu…" to {
            chooseFromList(ctx)
        }

        actions += "Saisir un domaine manuellement…" to {
            askManual(ctx)
        }

        if (override != null) {
            actions += "Effacer override manuel" to {
                MovixConfig.setManualOverride(ctx, null)
                Toast.makeText(ctx, "Override effacé. Hôte courant : ${MovixConfig.currentApiHost(ctx)}", Toast.LENGTH_LONG).show()
            }
        }

        actions += "Fermer" to {}

        AlertDialog.Builder(ctx, R.style.MovixDialog)
            .setTitle("Serveur Movix")
            .setMessage(info)
            .setItems(actions.map { it.first }.toTypedArray()) { _, idx ->
                actions[idx].second()
            }
            .show()
    }

    private fun redetect(ctx: Context, scope: CoroutineScope) {
        Toast.makeText(ctx, "Détection en cours…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { MirrorResolver.resolve(ctx) }
            if (resolved != null) {
                MovixConfig.setManualOverride(ctx, null) // re-détection annule l'override
                Toast.makeText(ctx, "Domaine détecté : $resolved", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, "Détection échouée. Vérifie ta connexion ou saisis manuellement.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun chooseFromList(ctx: Context) {
        val hosts = MirrorResolver.FALLBACK_HOSTS
        AlertDialog.Builder(ctx, R.style.MovixDialog)
            .setTitle("Choisir un domaine connu")
            .setItems(hosts.toTypedArray()) { _, idx ->
                val chosen = hosts[idx]
                MovixConfig.setManualOverride(ctx, chosen)
                Toast.makeText(ctx, "Domaine défini : $chosen", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun askManual(ctx: Context) {
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "api.movix.date"
            setText(MovixConfig.manualOverride(ctx) ?: MovixConfig.currentApiHost(ctx))
        }
        val container = LinearLayout(ctx).apply {
            setPadding(48, 24, 48, 24)
            orientation = LinearLayout.VERTICAL
            val label = TextView(ctx).apply {
                text = "Hôte API complet (ex: api.movix.date) :"
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, 16)
            }
            addView(label)
            addView(input, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        AlertDialog.Builder(ctx, R.style.MovixDialog)
            .setTitle("Domaine manuel")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                val v = input.text.toString().trim()
                MovixConfig.setManualOverride(ctx, v)
                Toast.makeText(ctx, "Override enregistré : $v", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
