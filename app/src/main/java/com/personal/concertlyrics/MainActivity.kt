@file:OptIn(ExperimentalLayoutApi::class)

package com.personal.concertlyrics

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ConcertTheme { App(vm) } }
    }
}

private val PageGutter = 20.dp

@Composable
fun App(vm: MainVm) {
    val ctx = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.detectLocation() else vm.locationRefused()
    }
    // Before Android's permission prompt, explain why and that saying no is fine (shown once).
    var showLocationIntro by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (vm.location == null) {
            when {
                Locator.hasPermission(ctx) -> vm.detectLocation()
                !vm.locationIntroSeen -> showLocationIntro = true
                else -> vm.locationRefused()
            }
        }
    }
    if (showLocationIntro) {
        LocationIntroDialog(
            onAllow = {
                showLocationIntro = false
                vm.markLocationIntroSeen()
                askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onSkip = {
                showLocationIntro = false
                vm.markLocationIntroSeen()
                vm.locationRefused()
            },
        )
    }
    val useDeviceLocation: () -> Unit = {
        vm.autoDetectLocation()
        if (!Locator.hasPermission(ctx)) askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    BackHandler(enabled = vm.screen != Screen.Home) { vm.screen = Screen.Home }

    Scaffold { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (val s = vm.screen) {
                Screen.Home -> HomeScreen(vm, useDeviceLocation)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.ConcertView -> {
                    val c = vm.concert(s.id)
                    if (c == null) LaunchedEffect(s.id) { vm.screen = Screen.Home } else ConcertScreen(vm, c)
                }
            }
        }
    }
}

@Composable
fun LocationIntroDialog(onAllow: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Find the show near you?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Your approximate location helps the app work out which tour is coming to your city, " +
                        "so the setlist matches the show you're going to.",
                )
                Text(
                    "It's completely fine to say no. The app works without it: it will use the artist's " +
                        "latest shows, and you can type your city or a tour name any time.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Only your country is sent to setlist.fm. Your city is matched on the phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onAllow) { Text("Use my location") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("No thanks") } },
    )
}

/** Big page title like the Expense Tracker: no toolbar, just a heading. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else PageGutter, end = 8.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
            )
            actions()
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (onBack != null) 52.dp else 0.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: MainVm, useDeviceLocation: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var tour by rememberSaveable { mutableStateOf("") }
    var editLocation by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Concert?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun go() {
        keyboard?.hide()
        vm.search(query, tour)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            PageHeader("Concert Lyrics", subtitle = "Know every word at the show") {
                IconButton(onClick = { vm.screen = Screen.Settings }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = PageGutter).padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (vm.apiKey.isBlank()) {
                    OutlinedTile(
                        modifier = Modifier.clickable { vm.screen = Screen.Settings },
                        borderColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text("One-time setup", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Add your free setlist.fm API key so the app can look up setlists. Tap here ›",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Artist") },
                    placeholder = { Text("e.g. Coldplay") },
                    singleLine = true,
                    enabled = !vm.busy,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tour,
                    onValueChange = { tour = it },
                    label = { Text("Tour name (optional)") },
                    supportingText = { Text("Leave empty and the app works out the tour from your location.") },
                    singleLine = true,
                    enabled = !vm.busy,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = if (tour.isNotEmpty()) {
                        { IconButton(onClick = { tour = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear tour") } }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                LocationRow(vm, usedForSearch = tour.isBlank()) { editLocation = true }

                Button(
                    onClick = { go() },
                    enabled = !vm.busy && query.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Find setlist & lyrics")
                }

                if (vm.busy) ProgressRow(vm)
                vm.error?.let { ErrorRow(it) { vm.error = null } }
            }
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = PageGutter).padding(top = 28.dp),
                title = "Saved concerts",
                subtitle = "Saved on this phone · work with no signal",
            ) {
                if (vm.concerts.isEmpty()) {
                    Text(
                        "Concerts you look up will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(vm.concerts, key = { _, c -> c.id }) { i, c ->
            Column(Modifier.padding(horizontal = PageGutter)) {
                if (i > 0) Hairline()
                ConcertRow(c, onOpen = { vm.screen = Screen.ConcertView(c.id) }, onDelete = { confirmDelete = c })
            }
        }
    }

    if (editLocation) LocationDialog(vm, useDeviceLocation) { editLocation = false }
    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${c.artist}?") },
            text = { Text("This removes the saved setlist and lyrics from this phone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c.id); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun LocationRow(vm: MainVm, usedForSearch: Boolean, onChange: () -> Unit) {
    val loc = vm.location
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = if (loc != null) MaterialTheme.colorScheme.primary else muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            when {
                vm.locating -> Text("Finding your location…", style = MaterialTheme.typography.bodyMedium)
                loc != null -> Text(loc.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else -> {
                    Text("Location off", style = MaterialTheme.typography.bodyMedium)
                    Text("Using the artist's latest shows", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            if (!usedForSearch && loc != null) {
                Text("Not needed: you entered a tour", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Text(
            if (loc == null && !vm.locating) "Type city ›" else "Change ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ConcertRow(c: Concert, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.artist, style = MaterialTheme.typography.titleMedium)
            c.tour?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("${c.mainSongs} songs", MaterialTheme.colorScheme.onSurfaceVariant)
                val complete = c.songs.all { it.lyrics != null || it.instrumental || !it.looked }
                Pill(
                    "lyrics ${c.withLyrics}/${c.songs.size}",
                    if (complete) Extra.colors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProgressRow(vm: MainVm) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(vm.progress, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { vm.cancel() }) { Text("Stop") }
    }
}

@Composable
fun ErrorRow(msg: String, onDismiss: () -> Unit) {
    OutlinedTile(borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(msg, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clickable { onDismiss() },
            )
        }
    }
}

@Composable
fun LocationDialog(vm: MainVm, useDeviceLocation: () -> Unit, onDone: () -> Unit) {
    var city by remember { mutableStateOf(vm.location?.city.orEmpty()) }
    var country by remember { mutableStateOf(vm.location?.countryCode ?: "IN") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Your city") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Used only to work out which tour is coming to you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(
                    country,
                    { country = it.take(2).uppercase() },
                    label = { Text("Country code (IN, US, GB…)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                TextButton(onClick = { useDeviceLocation(); onDone() }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use my phone's location instead")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.setManualLocation(city, country); onDone() },
                enabled = city.isNotBlank() && country.length == 2,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Concert

@Composable
fun ConcertScreen(vm: MainVm, c: Concert) {
    val uri = LocalUriHandler.current
    val expanded = remember(c.id) { mutableStateMapOf<Int, Boolean>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    // Keep the screen awake while reading lyrics at the concert.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val allOpen = c.songs.isNotEmpty() && c.songs.indices.all { expanded[it] == true }
    val missing = c.songs.count { it.looked && it.lyrics == null && !it.instrumental }
    val firstSometimes = c.songs.indexOfFirst { it.sometimes }
    val lyricSize = (17 * vm.textScale).sp
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            PageHeader(c.artist, subtitle = c.tour, onBack = { vm.screen = Screen.Home }) {
                TextButton(onClick = { vm.changeTextScale(-0.1f) }) { Text("A−") }
                TextButton(onClick = { vm.changeTextScale(0.1f) }) { Text("A+") }
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = PageGutter).padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(c.reason, style = MaterialTheme.typography.bodySmall, color = muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showInfo) "Hide shows ‹" else "Based on ${c.shows.size} show(s) ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { showInfo = !showInfo }.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (allOpen) "Collapse all" else "Expand all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable {
                            if (allOpen) expanded.clear() else c.songs.indices.forEach { expanded[it] = true }
                        }.padding(vertical = 4.dp),
                    )
                }
                if (showInfo) c.shows.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = muted) }
                if (vm.busy) ProgressRow(vm)
                vm.error?.let { ErrorRow(it) { vm.error = null } }
                if (missing > 0 && !vm.busy) {
                    OutlinedButton(onClick = { vm.retryMissing(c.id) }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry $missing song(s) without lyrics")
                    }
                }
            }
        }

        itemsIndexed(c.songs, key = { i, _ -> i }) { i, song ->
            Column(Modifier.padding(horizontal = PageGutter)) {
                if (i == firstSometimes) {
                    SectionCard(
                        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                        title = "Sometimes played",
                        subtitle = "Played at some recent shows but not the newest one.",
                    ) {}
                }
                Hairline()
                SongRow(
                    number = if (song.sometimes) null else i + 1,
                    song = song,
                    open = expanded[i] == true,
                    lyricSize = lyricSize,
                    onToggle = { expanded[i] = expanded[i] != true },
                    onSearchWeb = {
                        val q = URLEncoder.encode("${song.lyricsArtist} ${song.title} lyrics", "UTF-8")
                        uri.openUri("https://www.google.com/search?q=$q")
                    },
                    onEdit = { editing = i },
                )
            }
        }
    }

    editing?.let { i ->
        LyricsEditDialog(c.songs[i], onSave = { vm.setLyrics(c.id, i, it); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
fun SongRow(
    number: Int?,
    song: SavedSong,
    open: Boolean,
    lyricSize: TextUnit,
    onToggle: () -> Unit,
    onSearchWeb: () -> Unit,
    onEdit: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                number?.toString() ?: "•",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(34.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val regular = song.playedAt * 2 >= song.outOf
                    Pill("${song.playedAt}/${song.outOf} shows", if (regular) Extra.colors.good else muted)
                    song.coverOf?.let { Pill("cover · $it", muted) }
                    when {
                        song.instrumental -> Pill("instrumental", muted)
                        !song.looked -> Pill("loading lyrics…", muted)
                        song.lyrics == null -> Pill("no lyrics found", MaterialTheme.colorScheme.error)
                    }
                }
                song.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Hide lyrics" else "Show lyrics",
                tint = muted,
            )
        }
        if (open) {
            Column(Modifier.padding(start = 34.dp, bottom = 16.dp)) {
                when {
                    song.lyrics != null -> SelectionContainer {
                        Text(song.lyrics, fontSize = lyricSize, lineHeight = lyricSize * 1.45f)
                    }
                    song.instrumental -> Text("Instrumental: no lyrics to sing.", color = muted)
                    !song.looked -> Text("Still downloading…", color = muted)
                    else -> Text(
                        "Couldn't find lyrics automatically. Search the web, copy them, then tap Add lyrics.",
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(Modifier.padding(top = 6.dp)) {
                    if (song.lyrics == null && !song.instrumental) {
                        TextButton(onClick = onSearchWeb, contentPadding = PaddingValues(end = 16.dp)) { Text("Search web ›") }
                    }
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(end = 16.dp)) {
                        Text(if (song.lyrics == null) "Add lyrics ›" else "Edit lyrics ›")
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsEditDialog(song: SavedSong, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(song.lyrics.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Paste lyrics here") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: MainVm) {
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf(vm.apiKey) }
    var saved by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Settings", onBack = { vm.screen = Screen.Home })
        Column(
            Modifier.padding(horizontal = PageGutter).padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SectionCard(title = "setlist.fm API key", subtitle = "Free for personal use. Needed to read setlists.") {
                Text(
                    "1. Tap the link below and log in to (or sign up for) setlist.fm.\n" +
                        "2. Apply for an API key: any app name and a short description like \"personal lyrics app\" are fine.\n" +
                        "3. Copy the key and paste it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Get a free key on setlist.fm ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(MaterialTheme.shapes.small)
                        .clickable { uri.openUri("https://www.setlist.fm/settings/api") }
                        .padding(vertical = 4.dp),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; saved = false },
                    label = { Text("API key") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.saveKey(key); saved = true },
                    enabled = key.isNotBlank() && !saved,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(if (saved) "Saved ✓" else "Save key") }
            }

            SectionCard(title = "About") {
                Text(
                    "Setlists come from setlist.fm. Lyrics come from LRCLIB (lrclib.net), a free open lyrics database. " +
                        "Everything is saved on this phone, so saved concerts work with no signal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }
    }
}
