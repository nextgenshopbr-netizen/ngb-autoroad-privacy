// ============================================================================
// ARQUIVO: CardGalleryActivity.kt
// LOCALIZAÇÃO: ui/gallery/CardGalleryActivity.kt
// RESPONSABILIDADE: Galeria de cards pré-definidos
//   - Exibe todos os 35 cards disponíveis
//   - Permite selecionar e aplicar cards
// DEPENDÊNCIAS:
//   - data/model/CardGallery.kt → CardGallery.allCards
// ============================================================================
package com.ngbautoroad.ui.gallery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.CardGallery
import com.ngbautoroad.data.model.CardModel
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme

class CardGalleryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SELECTED_MODEL_ID = "selected_model_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NGBAutoRoadTheme {
                GalleryScreen(
                    onBack = { finish() },
                    onSelect = { modelId ->
                        val result = Intent().apply {
                            putExtra(EXTRA_SELECTED_MODEL_ID, modelId)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galeria de Cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CardGallery.models) { model ->
                GalleryCardItem(model = model, onClick = { onSelect(model.id) })
            }
        }
    }
}

@Composable
fun GalleryCardItem(model: CardModel, onClick: () -> Unit) {
    val bgColor = Color(model.backgroundColor)
    val textColor = Color(model.textColor)
    val accentColor = Color(model.accentColor)
    val borderColor = Color(model.borderColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(model.borderRadius.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(model.borderRadius.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = model.name,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Column {
                Text("R$ 18,50", color = textColor, fontSize = 12.sp)
                Text("3.2 km • 12 min", color = textColor.copy(alpha = 0.7f), fontSize = 10.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Score", color = accentColor, fontSize = 10.sp)
                Text("78", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
