@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sorting.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class CsvTable(
    val category: String, // "NSH" or "PH"
    val regionName: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class CardTag(
    val displayText: String,
    val colorType: Int // 0: Yellow, 1: Pink, 2: Cyan, 3: Mint Green, 4: Purple
)

data class SearchCardItem(
    val title: String,
    val region: String,
    val tags: List<CardTag>,
    val fullSearchString: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            PostalSortingApp()
        }
    }
}

// Convert "DHAR DEWAS" -> "Dhar Dewas"
fun formatTitleCase(text: String): String {
    return text.split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
}

// Parses raw CSV stream handling quotes and commas properly
fun parseCsvStream(inputStream: InputStream, category: String, regionName: String): CsvTable? {
    try {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val rawLines = reader.readLines()
        val cleanLines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanLines.isEmpty()) return null

        fun splitLine(line: String): List<String> {
            val tokens = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                when {
                    ch == '\"' -> inQuotes = !inQuotes
                    ch == ',' && !inQuotes -> {
                        tokens.add(sb.toString().trim().removeSurrounding("\""))
                        sb.clear()
                    }
                    else -> sb.append(ch)
                }
            }
            tokens.add(sb.toString().trim().removeSurrounding("\""))
            return tokens
        }

        val headers = splitLine(cleanLines[0]).map { it.uppercase() }
        val rows = cleanLines.drop(1).map { splitLine(it) }
        return CsvTable(category, regionName, headers, rows)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// Loads all CSVs from assets
fun loadBundledTables(context: Context): List<CsvTable> {
    val tables = mutableListOf<CsvTable>()
    val assetManager = context.assets

    try {
        val fileList = assetManager.list("") ?: emptyArray()
        val csvFiles = fileList.filter { it.trim().endsWith(".csv", ignoreCase = true) }

        for (fileName in csvFiles) {
            val lower = fileName.lowercase()
            val category = when {
                lower.startsWith("ph") || lower.contains("- ph") -> "PH"
                else -> "NSH"
            }

            val baseName = fileName.trim()
                .replace(Regex("(?i)^(nsh|ph)\\s*[-_]?\\s*"), "")
                .replace(Regex("(?i)\\.csv$"), "")
                .trim()
            val regionName = if (baseName.isNotBlank()) formatTitleCase(baseName) else "General"

            assetManager.open(fileName).use { stream ->
                val table = parseCsvStream(stream, category, regionName)
                if (table != null) tables.add(table)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return tables
}

@Composable
fun PostalSortingApp() {
    val context = LocalContext.current
    var loadedTables by remember { mutableStateOf(loadBundledTables(context)) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val currentTabCategory = if (selectedTabIndex == 0) "NSH" else "PH"
    val tabTitles = listOf("NSH", "PH", "Sorting Test")

    val currentCategoryTables = remember(loadedTables, currentTabCategory) {
        loadedTables.filter { it.category == currentTabCategory }
    }

    val defaultRegions = listOf("Dhar Dewas", "Khandwa Khargone", "Air")
    val availableRegions = remember(currentCategoryTables) {
        val extracted = currentCategoryTables.map { it.regionName }.distinct()
        if (extracted.isEmpty()) defaultRegions else extracted
    }

    var selectedRegion by remember { mutableStateOf("Search All") }
    var selectedFilterRegions by remember { mutableStateOf(availableRegions.toSet()) }
    var isDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(availableRegions) {
        selectedFilterRegions = availableRegions.toSet()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchColumn by remember { mutableStateOf("All Columns") }

    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var isSortAscending by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // File Picker: Fixed parameter to "*/*"
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val newTables = mutableListOf<CsvTable>()
        uris.forEach { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val fileName = uri.lastPathSegment ?: "Imported"
                    val region = formatTitleCase(fileName.replace(".csv", "").replace(Regex("(?i)^(nsh|ph)\\s*[-_]?\\s*"), ""))
                    val table = parseCsvStream(stream, currentTabCategory, region)
                    if (table != null) newTables.add(table)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (newTables.isNotEmpty()) {
            loadedTables = loadedTables + newTables
        }
    }

    // Theme Colors
    val postalRed = Color(0xFF8B1515L)
    val saffronOrange = Color(0xFFE87A00L)
    val darkHeading = Color(0xFF1E293BL)
    val subHeadingHindi = Color(0xFF555555L)
    val tabInactiveColor = Color(0xFF64748BL)
    val outerBg = Color(0xFFEAEFF5L)
    val pinBgColor = Color(0xFFEEF2FFL)
    val pinTextColor = Color(0xFF3730A3L)

    // Build Search All Cards
    val allCardItems = remember(currentCategoryTables, selectedFilterRegions) {
        val list = mutableListOf<SearchCardItem>()
        val tablesToInclude = currentCategoryTables.filter { selectedFilterRegions.contains(it.regionName) }

        for (tbl in tablesToInclude) {
            val hUpper = tbl.headers.map { it.uppercase() }

            val titleIdx = when {
                hUpper.indexOfFirst { it.contains("VILLAGE") } != -1 -> hUpper.indexOfFirst { it.contains("VILLAGE") }
                hUpper.indexOfFirst { it.contains("OFFICE") && !it.contains("L2") } != -1 -> hUpper.indexOfFirst { it.contains("OFFICE") && !it.contains("L2") }
                hUpper.indexOfFirst { it.contains("L1") } != -1 -> hUpper.indexOfFirst { it.contains("L1") }
                else -> tbl.headers.indices.firstOrNull { idx -> !hUpper[idx].contains("PIN") } ?: 0
            }

            for (row in tbl.rows) {
                val title = row.getOrNull(titleIdx)?.ifBlank { "Office" } ?: "Office"
                val tags = mutableListOf<CardTag>()

                tbl.headers.forEachIndexed { idx, header ->
                    val value = row.getOrNull(idx) ?: ""
                    if (value.isNotBlank()) {
                        val headerUpper = header.uppercase()
                        val colorType = when {
                            headerUpper.contains("FROM PIN") || (headerUpper.contains("PIN") && !headerUpper.contains("TO")) -> 0 // Yellow
                            headerUpper.contains("TO PIN") || headerUpper.contains("VILLAGE") -> 1 // Pink
                            headerUpper.contains("BO") || headerUpper.contains("L1") -> 2 // Cyan
                            headerUpper.contains("SO") || headerUpper.contains("L2") -> 3 // Mint Green
                            headerUpper.contains("HO") || headerUpper.contains("CIRC") || headerUpper.contains("DIST") -> 4 // Purple
                            else -> idx % 5
                        }
                        val label = if (headerUpper == "PIN" || headerUpper == "FROM PIN") "PIN $value" else "${formatTitleCase(header)}: $value"
                        tags.add(CardTag(label, colorType))
                    }
                }

                list.add(SearchCardItem(title, tbl.regionName, tags, (listOf(title) + row).joinToString(" ")))
            }
        }
        list
    }

    val filteredCardItems = remember(allCardItems, searchQuery) {
        if (searchQuery.isBlank()) allCardItems
        else allCardItems.filter { it.fullSearchString.contains(searchQuery, ignoreCase = true) }
    }

    // Specific Table Data
    val currentTable = remember(currentCategoryTables, selectedRegion) {
        currentCategoryTables.firstOrNull { it.regionName.equals(selectedRegion, ignoreCase = true) }
    }

    val filteredTableRows = remember(currentTable, searchQuery, selectedSearchColumn, sortColumnIndex, isSortAscending) {
        if (currentTable == null) emptyList()
        else {
            var list = currentTable.rows
            if (searchQuery.isNotBlank()) {
                list = list.filter { row ->
                    if (selectedSearchColumn == "All Columns") {
                        row.any { it.contains(searchQuery, ignoreCase = true) }
                    } else {
                        val colIdx = currentTable.headers.indexOfFirst { it.equals(selectedSearchColumn, ignoreCase = true) }
                        if (colIdx != -1 && colIdx < row.size) row[colIdx].contains(searchQuery, ignoreCase = true) else false
                    }
                }
            }
            val currentSort = sortColumnIndex
            if (currentSort != null && currentSort < currentTable.headers.size) {
                list = list.sortedWith { r1, r2 ->
                    val v1 = r1.getOrNull(currentSort) ?: ""
                    val v2 = r2.getOrNull(currentSort) ?: ""
                    if (isSortAscending) v1.compareTo(v2, ignoreCase = true) else v2.compareTo(v1, ignoreCase = true)
                }
            }
            list
        }
    }

    val isAllRegionsSelected = selectedFilterRegions.size == availableRegions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(outerBg)
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.White)
        ) {
            // Orange Header Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(saffronOrange)
            )

            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Department of Posts",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = darkHeading,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "भारतीय डाक • डाक सेवा जन सेवा",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = subHeadingHindi
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "ID Division Indore • Sorting Plan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = postalRed
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0L)))

            // Main Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomCenter)
                                .width(64.dp)
                                .height(3.5.dp)
                                .background(postalRed, RoundedCornerShape(3.dp))
                        )
                    }
                },
                divider = {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0L)))
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = (selectedTabIndex == index),
                        onClick = {
                            selectedTabIndex = index
                            selectedRegion = "Search All"
                        },
                        text = {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (selectedTabIndex == index) postalRed else tabInactiveColor
                            )
                        }
                    )
                }
            }

            // Region Filter Pills (Search All first)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillTab(
                    text = "Search All",
                    isSelected = (selectedRegion == "Search All"),
                    hasDot = false,
                    onClick = { selectedRegion = "Search All" }
                )

                availableRegions.forEach { region ->
                    val isSelected = selectedRegion.equals(region, ignoreCase = true)
                    PillTab(
                        text = region,
                        isSelected = isSelected,
                        hasDot = true,
                        onClick = { selectedRegion = region }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0L)))

            // Body Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFBFBFBL))
            ) {
                // Search Input Field with Clear ('✕') Button[span_3](start_span)[span_3](end_span)[span_4](start_span)[span_4](end_span)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD1D5DBL))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔍", fontSize = 15.sp, color = Color(0xFF64748BL))
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (selectedRegion == "Search All") "Search across selected regions..." else "Search in table...",
                                    color = Color(0xFF94A3B8L),
                                    fontSize = 15.sp
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E293BL),
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                color = postalRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                }

                // Sub-Controls: Search Regions Dropdown + Upload Button[span_5](start_span)[span_5](end_span)[span_6](start_span)[span_6](end_span)
                if (selectedRegion == "Search All") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { isDropdownOpen = !isDropdownOpen },
                                shape = RoundedCornerShape(50),
                                color = Color.White,
                                border = BorderStroke(1.5.dp, postalRed)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📁", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Search Regions",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = postalRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFFFCE8E8L),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = if (isAllRegionsSelected) "All" else "${selectedFilterRegions.size}",
                                            color = postalRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "▼", color = postalRed, fontSize = 10.sp)
                                }
                            }

                            Button(
                                onClick = { filePicker.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = postalRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("📁", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Filter Popup Menu
                        if (isDropdownOpen) {
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = androidx.compose.ui.unit.IntOffset(0, 110),
                                onDismissRequest = { isDropdownOpen = false }
                            ) {
                                Card(
                                    modifier = Modifier.width(260.dp).padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedFilterRegions = if (isAllRegionsSelected) emptySet() else availableRegions.toSet()
                                                }
                                                .padding(vertical = 4.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isAllRegionsSelected,
                                                onCheckedChange = { checked ->
                                                    selectedFilterRegions = if (checked) availableRegions.toSet() else emptySet()
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = postalRed, checkmarkColor = Color.White)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Select All", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = darkHeading)
                                        }

                                        Divider(color = Color(0xFFF1F5F9L), thickness = 1.dp)

                                        availableRegions.forEach { region ->
                                            val isChecked = selectedFilterRegions.contains(region)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedFilterRegions = if (isChecked) selectedFilterRegions - region else selectedFilterRegions + region
                                                    }
                                                    .padding(vertical = 2.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        selectedFilterRegions = if (checked) selectedFilterRegions + region else selectedFilterRegions - region
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = postalRed, checkmarkColor = Color.White)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(region, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = darkHeading)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Result count summary row[span_7](start_span)[span_7](end_span)[span_8](start_span)[span_8](end_span)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "${filteredCardItems.size} results for \"$searchQuery\"" else "${filteredCardItems.size} results",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748BL)
                        )
                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "Clear",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = postalRed,
                                modifier = Modifier.clickable { searchQuery = "" }
                            )
                        }
                    }

                    // Card List with Right Vertical A-Z Scroller[span_9](start_span)[span_9](end_span)[span_10](start_span)[span_10](end_span)
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 34.dp, top = 2.dp, bottom = 16.dp)
                        ) {
                            items(filteredCardItems) { card ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0L))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = card.title,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172AL)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Render tags in clean rows[span_11](start_span)[span_11](end_span)[span_12](start_span)[span_12](end_span)
                                        val shortTags = card.tags.filter { it.displayText.length <= 26 }
                                        val longTags = card.tags.filter { it.displayText.length > 26 }

                                        // Short badges side-by-side[span_13](start_span)[span_13](end_span)[span_14](start_span)[span_14](end_span)
                                        shortTags.chunked(2).forEach { tagPair ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                tagPair.forEach { tag ->
                                                    BadgeTagView(tag)
                                                }
                                            }
                                        }

                                        // Long badges on full-width lines[span_15](start_span)[span_15](end_span)
                                        longTags.forEach { tag ->
                                            Box(modifier = Modifier.padding(vertical = 3.dp)) {
                                                BadgeTagView(tag)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Right A-Z Jump Bar[span_16](start_span)[span_16](end_span)[span_17](start_span)[span_17](end_span)
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = "⌂",
                                color = postalRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { coroutineScope.launch { listState.animateScrollToItem(0) } }
                            )

                            ('A'..'W').forEach { letter ->
                                Text(
                                    text = letter.toString(),
                                    color = postalRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            val targetIndex = filteredCardItems.indexOfFirst {
                                                it.title.startsWith(letter, ignoreCase = true)
                                            }
                                            if (targetIndex != -1) {
                                                coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                            }
                                        }
                                )
                            }
                        }
                    }
                } else {
                    // Region Spreadsheet Table View
                    val searchInOptions = remember(currentTable) {
                        listOf("All Columns") + (currentTable?.headers?.map { formatTitleCase(it) } ?: emptyList())
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SEARCH IN:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF334155L)
                        )

                        searchInOptions.forEach { opt ->
                            val isSelected = selectedSearchColumn.equals(opt, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { selectedSearchColumn = opt },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) postalRed else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) postalRed else Color(0xFFE2E8F0L))
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF334155L),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (currentTable == null || currentTable.headers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No table data found for $selectedRegion.\nPlease ensure the CSV file is placed in assets.",
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val horizontalScroll = rememberScrollState()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScroll)
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFF8FAFCCL))
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                currentTable.headers.forEachIndexed { idx, header ->
                                    val isPinCol = header.contains("PIN")
                                    val colWidth = if (isPinCol) 120.dp else 180.dp

                                    Row(
                                        modifier = Modifier
                                            .width(colWidth)
                                            .clickable {
                                                if (sortColumnIndex == idx) isSortAscending = !isSortAscending else {
                                                    sortColumnIndex = idx
                                                    isSortAscending = true
                                                }
                                            }
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = header,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1E293BL),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (sortColumnIndex == idx) (if (isSortAscending) " ▲" else " ▼") else " ⇅",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8L)
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0L)))

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredTableRows) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        currentTable.headers.forEachIndexed { idx, header ->
                                            val isPinCol = header.contains("PIN")
                                            val colWidth = if (isPinCol) 120.dp else 180.dp
                                            val cellValue = row.getOrNull(idx) ?: ""

                                            Box(
                                                modifier = Modifier
                                                    .width(colWidth)
                                                    .padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (isPinCol && cellValue.isNotBlank()) {
                                                    Surface(
                                                        color = pinBgColor,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = cellValue,
                                                            color = pinTextColor,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = cellValue,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF1E293BL),
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9L)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeTagView(tag: CardTag) {
    val (bgColor, borderColor, txtColor) = when (tag.colorType) {
        0 -> Triple(Color(0xFFFEF9C3L), Color(0xFFFDE047L), Color(0xFF854D0EL)) // Yellow PIN[span_18](start_span)[span_18](end_span)[span_19](start_span)[span_19](end_span)
        1 -> Triple(Color(0xFFFFE4E6L), Color(0xFFFECDD3L), Color(0xFF9F1239L)) // Pink Village/To Pin[span_20](start_span)[span_20](end_span)[span_21](start_span)[span_21](end_span)
        2 -> Triple(Color(0xFFE0F2FEL), Color(0xFFBAE6FDL), Color(0xFF0369A1L)) // Cyan BO/L1[span_22](start_span)[span_22](end_span)[span_23](start_span)[span_23](end_span)
        3 -> Triple(Color(0xFFDCFCE7L), Color(0xFFBBF7D0L), Color(0xFF166534L)) // Mint SO/L2[span_24](start_span)[span_24](end_span)[span_25](start_span)[span_25](end_span)
        else -> Triple(Color(0xFFF3E8FFL), Color(0xFFE9D5FFL), Color(0xFF6B21A8L)) // Purple HO/Circle[span_26](start_span)[span_26](end_span)[span_27](start_span)[span_27](end_span)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = tag.displayText,
            color = txtColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PillTab(
    text: String,
    isSelected: Boolean,
    hasDot: Boolean,
    onClick: () -> Unit
) {
    val postalRed = Color(0xFF8B1515L)
    val dotGreen = Color(0xFF22C55EL)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = if (isSelected) postalRed else Color.White,
        border = BorderStroke(1.dp, if (isSelected) postalRed else Color(0xFFE2E8F0L))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color(0xFF1E293BL)
            )
            if (hasDot) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(dotGreen, shape = CircleShape)
                )
            }
        }
    }
}
