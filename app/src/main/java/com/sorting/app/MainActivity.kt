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
import java.io.BufferedReader
import java.io.InputStreamReader

data class CsvTable(
    val category: String, // "NSH" or "PH"
    val regionName: String, // "Dhar Dewas", "Air", etc.
    val headers: List<String>,
    val rows: List<List<String>>
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

// Parse lines into clean table data
fun parseCsvToTable(category: String, regionName: String, lines: List<String>): CsvTable? {
    val cleanLines = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanLines.isEmpty()) return null

    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
    val headers = cleanLines[0].split(regex).map { it.trim().removeSurrounding("\"").uppercase() }
    val rows = cleanLines.drop(1).map { line ->
        line.split(regex).map { it.trim().removeSurrounding("\"") }
    }
    return CsvTable(category, regionName, headers, rows)
}

// Loads all CSVs from assets
fun loadBundledTables(context: Context): List<CsvTable> {
    val tables = mutableListOf<CsvTable>()
    val assetManager = context.assets

    try {
        val files = assetManager.list("")?.filter { it.trim().endsWith(".csv", ignoreCase = true) } ?: emptyList()

        for (fileName in files) {
            val cleanName = fileName.trim()
            val lower = cleanName.lowercase()

            val category = when {
                lower.startsWith("ph") || lower.contains("- ph") -> "PH"
                else -> "NSH"
            }

            val baseName = cleanName
                .replace(Regex("(?i)^(nsh|ph)\\s*[-_]?\\s*"), "")
                .replace(Regex("(?i)\\.csv$"), "")
                .trim()
            val regionName = if (baseName.isNotBlank()) formatTitleCase(baseName) else "General"

            assetManager.open(cleanName).bufferedReader().use { reader ->
                val lines = reader.readLines()
                val table = parseCsvToTable(category, regionName, lines)
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

    // Filter tables for current tab
    val currentCategoryTables = remember(loadedTables, currentTabCategory) {
        loadedTables.filter { it.category == currentTabCategory }
    }

    // Default regions list
    val defaultRegions = listOf("Dhar Dewas", "Khandwa Khargone", "Air")
    val availableRegions = remember(currentCategoryTables) {
        val extracted = currentCategoryTables.map { it.regionName }.distinct()
        if (extracted.isEmpty()) defaultRegions else extracted
    }

    var selectedRegion by remember { mutableStateOf(availableRegions.firstOrNull() ?: "Dhar Dewas") }

    // Keep active region in sync when changing tabs
    LaunchedEffect(availableRegions) {
        if (!availableRegions.contains(selectedRegion) && availableRegions.isNotEmpty()) {
            selectedRegion = availableRegions.first()
        }
    }

    // Active Table
    val activeTable = remember(currentCategoryTables, selectedRegion) {
        currentCategoryTables.firstOrNull { it.regionName.equals(selectedRegion, ignoreCase = true) }
    }

    // Search and Column Filtering State
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchColumn by remember { mutableStateOf("All Columns") }

    // Reset search column if table headers change
    LaunchedEffect(activeTable) {
        selectedSearchColumn = "All Columns"
    }

    // Sorting state
    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var isSortAscending by remember { mutableStateOf(true) }

    // Upload Sheets Picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val newTables = mutableListOf<CsvTable>()
        uris.forEach { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        val lines = reader.readLines()
                        val fileName = uri.lastPathSegment ?: "Imported"
                        val table = parseCsvToTable(currentTabCategory, formatTitleCase(fileName.replace(".csv", "")), lines)
                        if (table != null) newTables.add(table)
                    }
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

    // Filter Rows
    val filteredRows = remember(activeTable, searchQuery, selectedSearchColumn, sortColumnIndex, isSortAscending) {
        if (activeTable == null) emptyList()
        else {
            var list = activeTable.rows

            if (searchQuery.isNotBlank()) {
                list = list.filter { row ->
                    if (selectedSearchColumn == "All Columns") {
                        row.any { it.contains(searchQuery, ignoreCase = true) }
                    } else {
                        val colIdx = activeTable.headers.indexOfFirst { it.equals(selectedSearchColumn, ignoreCase = true) }
                        if (colIdx != -1 && colIdx < row.size) {
                            row[colIdx].contains(searchQuery, ignoreCase = true)
                        } else {
                            false
                        }
                    }
                }
            }

            if (sortColumnIndex != null && sortColumnIndex!! < activeTable.headers.size) {
                list = list.sortedWith { r1, r2 ->
                    val v1 = r1.getOrNull(sortColumnIndex!!) ?: ""
                    val v2 = r2.getOrNull(sortColumnIndex!!) ?: ""
                    if (isSortAscending) v1.compareTo(v2, ignoreCase = true) else v2.compareTo(v1, ignoreCase = true)
                }
            }
            list
        }
    }

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
                        onClick = { selectedTabIndex = index },
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

            // Region Filter Pills (Red capsule when active, green dot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    .background(Color.White)
            ) {
                // 1. "Search in table..." Box
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0L))
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
                                    text = "Search in table...",
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
                    }
                }

                // 2. SEARCH IN: Filter Chips Row
                val searchInOptions = remember(activeTable) {
                    listOf("All Columns") + (activeTable?.headers?.map { formatTitleCase(it) } ?: emptyList())
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

                // 3. "📁 Upload Sheets" Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = postalRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("📁", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Upload Sheets",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. Interactive Table with Horizontal and Vertical Scrolling
                if (activeTable == null || activeTable.headers.isEmpty()) {
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
                        // Table Header Row
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF8FAFCCL))
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeTable.headers.forEachIndexed { idx, header ->
                                val isPinCol = header.contains("PIN")
                                val colWidth = if (isPinCol) 115.dp else 170.dp

                                Row(
                                    modifier = Modifier
                                        .width(colWidth)
                                        .clickable {
                                            if (sortColumnIndex == idx) {
                                                isSortAscending = !isSortAscending
                                            } else {
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

                        // Table Rows
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredRows) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    activeTable.headers.forEachIndexed { idx, header ->
                                        val isPinCol = header.contains("PIN")
                                        val colWidth = if (isPinCol) 115.dp else 170.dp
                                        val cellValue = row.getOrNull(idx) ?: ""

                                        Box(
                                            modifier = Modifier
                                                .width(colWidth)
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (isPinCol && cellValue.isNotBlank()) {
                                                // Lavender/Blue Badge for PINs
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
