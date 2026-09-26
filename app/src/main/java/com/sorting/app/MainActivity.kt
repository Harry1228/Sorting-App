@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sorting.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.io.InputStream
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

// Formats "DHAR DEWAS" to "Dhar Dewas"
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

// Loads all CSV files from assets
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

            // Extract region name from file name (e.g. "NSH - AIR .csv" -> "Air")
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
    val loadedTables = remember { loadBundledTables(context) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val currentTabCategory = if (selectedTabIndex == 0) "NSH" else "PH"
    val tabTitles = listOf("NSH", "PH", "Sorting Test")

    // Filter tables matching current tab
    val currentCategoryTables = remember(loadedTables, currentTabCategory) {
        loadedTables.filter { it.category == currentTabCategory }
    }

    // Default regions shown in tabs
    val defaultRegions = listOf("Dhar Dewas", "Khandwa Khargone", "Air")
    val availableRegions = remember(currentCategoryTables) {
        val extracted = currentCategoryTables.map { it.regionName }.distinct()
        if (extracted.isEmpty()) defaultRegions else (defaultRegions + extracted).distinct()
    }

    // First selected pill defaults to "Search All"
    var selectedRegion by remember { mutableStateOf("Search All") }

    // Active Table: Combines all sheets if "Search All" is selected, else shows the specific sheet
    val currentTable = remember(currentCategoryTables, selectedRegion) {
        if (selectedRegion == "Search All") {
            val headers = listOf("REGION", "PIN / RANGE", "OFFICE / VILLAGE", "ROUTING / BO")
            val rows = mutableListOf<List<String>>()

            for (tbl in currentCategoryTables) {
                val hUpper = tbl.headers.map { it.uppercase() }
                val pinIdx = hUpper.indexOfFirst { it.contains("PIN") && !it.contains("FROM") && !it.contains("TO") }
                val fromPinIdx = hUpper.indexOfFirst { it.contains("FROM") }
                val toPinIdx = hUpper.indexOfFirst { it.contains("TO") }
                val officeIdx = hUpper.indexOfFirst { it.contains("OFFICE") || it.contains("VILLAGE") || it.contains("NAME") }
                val routingIdx = hUpper.indexOfFirst { it.contains("BO") || it.contains("ROUT") || it.contains("SET") || it.contains("HUB") }

                for (row in tbl.rows) {
                    val pinVal = when {
                        pinIdx != -1 && pinIdx < row.size -> row[pinIdx]
                        fromPinIdx != -1 && toPinIdx != -1 && fromPinIdx < row.size && toPinIdx < row.size -> {
                            val f = row[fromPinIdx]
                            val t = row[toPinIdx]
                            if (f == t) f else "$f - $t"
                        }
                        fromPinIdx != -1 && fromPinIdx < row.size -> row[fromPinIdx]
                        else -> row.getOrNull(0) ?: ""
                    }

                    val officeVal = if (officeIdx != -1 && officeIdx < row.size) row[officeIdx] else (row.getOrNull(1) ?: "")
                    val routingVal = if (routingIdx != -1 && routingIdx < row.size) row[routingIdx] else (row.getOrNull(2) ?: "")

                    rows.add(listOf(tbl.regionName, pinVal, officeVal, routingVal))
                }
            }
            CsvTable(currentTabCategory, "Search All", headers, rows)
        } else {
            currentCategoryTables.firstOrNull { it.regionName.equals(selectedRegion, ignoreCase = true) }
        }
    }

    // Search and Column Filtering State
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchColumn by remember { mutableStateOf("All Columns") }

    LaunchedEffect(currentTable) {
        selectedSearchColumn = "All Columns"
    }

    // Sorting state
    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var isSortAscending by remember { mutableStateOf(true) }

    // Design Colors
    val postalRed = Color(0xFF8B1515L)
    val saffronOrange = Color(0xFFE87A00L)
    val darkHeading = Color(0xFF1E293BL)
    val subHeadingHindi = Color(0xFF555555L)
    val tabInactiveColor = Color(0xFF64748BL)
    val outerBg = Color(0xFFEAEFF5L)
    val pinBgColor = Color(0xFFEEF2FFL)
    val pinTextColor = Color(0xFF3730A3L)

    // Filter Rows
    val filteredRows = remember(currentTable, searchQuery, selectedSearchColumn, sortColumnIndex, isSortAscending) {
        if (currentTable == null) emptyList()
        else {
            var list = currentTable.rows

            if (searchQuery.isNotBlank()) {
                list = list.filter { row ->
                    if (selectedSearchColumn == "All Columns") {
                        row.any { it.contains(searchQuery, ignoreCase = true) }
                    } else {
                        val colIdx = currentTable.headers.indexOfFirst { it.equals(selectedSearchColumn, ignoreCase = true) }
                        if (colIdx != -1 && colIdx < row.size) {
                            row[colIdx].contains(searchQuery, ignoreCase = true)
                        } else {
                            false
                        }
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

            // Region Filter Pills with "Search All" at the beginning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. "Search All" Pill (first in row)
                PillTab(
                    text = "Search All",
                    isSelected = (selectedRegion == "Search All"),
                    hasDot = false,
                    onClick = { selectedRegion = "Search All" }
                )

                // 2. Individual Region Pills
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
                // 1. "Search in table..." Input Field
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

                // 2. SEARCH IN: Dynamic Column Filter Chips
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

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Interactive Table
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
                        // Table Header Row
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
