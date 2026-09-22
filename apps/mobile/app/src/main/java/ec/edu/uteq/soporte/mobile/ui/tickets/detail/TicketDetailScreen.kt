package ec.edu.uteq.soporte.mobile.ui.tickets.detail

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import ec.edu.uteq.soporte.mobile.MobileApp
import ec.edu.uteq.soporte.mobile.ui.theme.PriorityChip
import ec.edu.uteq.soporte.mobile.ui.theme.SlaBreachedChip
import ec.edu.uteq.soporte.mobile.ui.theme.StatusChip
import ec.edu.uteq.soporte.mobile.ui.theme.StatusEscalado
import ec.edu.uteq.soporte.mobile.ui.theme.labelForCategory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(ticketId: String, onBack: () -> Unit, onClosed: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MobileApp
    val viewModel: TicketDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TicketDetailViewModel(app.serviceLocator.ticketRepository, ticketId) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmCloseDialog by remember { mutableStateOf(false) }
    var showLocationConsentDialog by remember { mutableStateOf(false) }

    val pendingPhotoUri = remember {
        val evidenceDir = File(context.cacheDir, "evidencia").apply { mkdirs() }
        val file = File(evidenceDir, "evidencia_$ticketId.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) viewModel.onEvidencePhotoCaptured(pendingPhotoUri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) cameraLauncher.launch(pendingPhotoUri) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) capturarUbicacion(context, viewModel) }

    LaunchedEffect(uiState.closeSucceeded) {
        if (uiState.closeSucceeded) onClosed()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Detalle de Orden", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val ticket = uiState.ticket
        if (ticket == null) {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ticket no disponible en modo offline",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(20.dp),
        ) {
            // --- Información del Ticket ---
            Text(
                text = ticket.description,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // ID completo y seleccionable: sirve para verificar en la consola web que el
            // cierre hecho aqui (movil) se propago al mismo backend real -- buscar este id
            // en apps/web tras presionar "FINALIZAR TICKET".
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = "ID: ${ticket.ticketId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ticket.category?.let { "${ticket.zone.name} • ${labelForCategory(it)}" } ?: ticket.zone.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                StatusChip(status = ticket.status)
                Spacer(modifier = Modifier.width(8.dp))
                ticket.priority?.let { priority ->
                    PriorityChip(priority = priority)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (ticket.slaBreached) {
                    SlaBreachedChip()
                }
            }

            // --- Zona de evidencia mejorada ---
            Text(
                text = "Evidencia de resolución",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val photoUri = uiState.evidencePhotoUri
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Evidencia fotográfica",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Indicador de éxito sobre la foto
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Capturada", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Requiere foto de evidencia",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // --- Ubicación Capturada ---
            AnimatedVisibility(visible = uiState.capturedLatitude != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Ubicación de cierre", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = "Lat: %.5f, Lon: %.5f".format(uiState.capturedLatitude, uiState.capturedLongitude),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- Banner de error ---
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { message ->
                    InfoBanner(text = message, modifier = Modifier.padding(bottom = 12.dp))
                }
            }

            // --- Acciones de captura ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            cameraLauncher.launch(pendingPhotoUri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tomar Foto")
                }
                
                OutlinedButton(
                    onClick = {
                        val hasLocationPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasLocationPermission) {
                            // Ya hay consentimiento de una captura anterior en esta orden: no
                            // se repite el dialogo de consentimiento cada vez que se toca el
                            // boton, solo la primera vez (antes de pedir el permiso del SO).
                            capturarUbicacion(context, viewModel)
                        } else {
                            showLocationConsentDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GPS Cierre")
                }
            }

            // --- Botón de Cierre ---
            Button(
                onClick = { showConfirmCloseDialog = true },
                enabled = uiState.canCloseOnSite && !uiState.isClosing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isClosing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Procesando...")
                } else {
                    Text("FINALIZAR TICKET", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (showConfirmCloseDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmCloseDialog = false },
                title = { Text("¿Finalizar orden de trabajo?") },
                text = { Text("Se enviará la evidencia fotográfica y las coordenadas GPS del sitio. Esta acción es definitiva.") },
                confirmButton = {
                    Button(onClick = {
                        showConfirmCloseDialog = false
                        val photoUri = uiState.evidencePhotoUri ?: return@Button
                        val rawBytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
                            ?: return@Button
                        viewModel.closeOnSite(comprimirEvidenciaFotografica(rawBytes))
                    }) {
                        Text("Sí, finalizar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmCloseDialog = false }) {
                        Text("Revisar")
                    }
                },
            )
        }

        // Consentimiento informado de ubicacion (Entregable 10 de la guia de cierre): el
        // permiso de Android por si solo dice "que" se va a acceder, no "para que" ni "que se
        // hace despues" con el dato -- una coordenada GPS atada a un tecnico identificado es
        // un dato personal sensible (ver la Reflexion etica del manuscrito), asi que este
        // dialogo explica el proposito y el destino del dato ANTES del permiso del sistema,
        // no lo reemplaza: si el tecnico acepta aqui pero luego niega el permiso del SO, no se
        // captura nada (ver locationPermissionLauncher).
        if (showLocationConsentDialog) {
            AlertDialog(
                onDismissRequest = { showLocationConsentDialog = false },
                title = { Text("Ubicación del cierre") },
                text = {
                    Text(
                        "Al aceptar, se tomará tu ubicación GPS actual una sola vez y se guardará " +
                            "junto a este ticket como evidencia de que el cierre se hizo en el sitio " +
                            "del cliente. No se usa para rastrear tu posición en otro momento."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showLocationConsentDialog = false
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) {
                        Text("Aceptar y continuar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationConsentDialog = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = StatusEscalado.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = StatusEscalado, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = StatusEscalado,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Suppress("MissingPermission")
private fun capturarUbicacion(
    context: android.content.Context,
    viewModel: TicketDetailViewModel,
) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
        .addOnSuccessListener { location ->
            if (location != null) {
                viewModel.onLocationCaptured(location.latitude, location.longitude)
            }
        }
}

// Revision externa posterior (Entregable 10 de la guia de cierre) encontro que la foto de
// evidencia se enviaba sin comprimir (~2,4 MB del JPEG crudo de la camara, ~3,2 MB en Base64),
// en contra de la propia recomendacion de CockroachDB de mantener valores BYTES por debajo de
// ~1 MiB (ver V3__add_close_evidence.sql). Reduce dimension y calidad JPEG hasta bajar de
// TARGET_BYTES, con un piso de calidad para no degradar la foto a algo inutil como evidencia.
private const val EVIDENCE_TARGET_BYTES = 800 * 1024
private const val EVIDENCE_MAX_DIMENSION_PX = 1600

private fun comprimirEvidenciaFotografica(rawBytes: ByteArray): ByteArray {
    val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        ?: return rawBytes // decodificacion fallida: se manda el original, mejor que nada
    val escala = EVIDENCE_MAX_DIMENSION_PX.toFloat() / maxOf(original.width, original.height)
    val redimensionada = if (escala < 1f) {
        Bitmap.createScaledBitmap(
            original,
            (original.width * escala).toInt().coerceAtLeast(1),
            (original.height * escala).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        original
    }

    var calidad = 85
    var salida: ByteArray
    do {
        val buffer = java.io.ByteArrayOutputStream()
        redimensionada.compress(Bitmap.CompressFormat.JPEG, calidad, buffer)
        salida = buffer.toByteArray()
        calidad -= 15
    } while (salida.size > EVIDENCE_TARGET_BYTES && calidad >= 25)

    if (redimensionada !== original) {
        original.recycle()
    }
    redimensionada.recycle()
    return salida
}
