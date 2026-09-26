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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import java.io.BufferedReader
import java.io.InputStreamReader

data class PostalRecord(
    val pin: String,
    val officeName: String,
    val officeType: String,
    val district: String,
    val division: String,
    val routing: String,
    val category: String // "NSH" or "PH"
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

// Converts strings like "DHAR DEWAS" to "Dhar Dewas"
fun formatRegionTitle(name: String): String {
    return name.split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

// Loads and parses every CSV in assets/
fun loadAllAssetsCsv(context: Context): List<PostalRecord> {
    val recordList = mutableListOf<PostalRecord>()
    val assetManager = context.assets

    try {
        val fileNames = assetManager.list("")?.filter { it.trim().endsWith(".csv", ignoreCase = true) } ?: emptyList()

        for (fileName in fileNames) {
            // Ignore sample placeholder if other files exist
            if (fileName.contains("sample_data", ignoreCase = true) && fileNames.size > 1) continue

            val cleanName = fileName.trim()
            val lower = cleanName.lowercase()

            // 1. Determine tab category (NSH or PH)
            val fileCategory = when {
                lower.startsWith("ph") || lower.contains("- ph") -> "PH"
                else -> "NSH"
            }

            // 2. Extract Region from filename (e.g. "NSH - AIR .csv" -> "Air", "NSH - DHAR DEWAS.csv" -> "Dhar Dewas")
            val baseName = cleanName
                .replace(Regex("(?i)^(nsh|ph)\\s*[-_]?\\s*"), "")
                .replace(Regex("(?i)\\.csv$"), "")
                .trim()
            val fileRegion = if (baseName.isNotBlank()) formatRegionTitle(baseName) else ""

            assetManager.open(cleanName).bufferedReader().use { reader ->
                val lines = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size > 1) {
                    val headers = lines[0].split(",").map { it.trim().lowercase().removeSurrounding("\"") }

                    val pinIdx = headers.indexOfFirst { it.contains("pin") }
                    val officeIdx = headers.indexOfFirst { it.contains("office") || it.contains("name") }
                    val typeIdx = headers.indexOfFirst { it.contains("type") }
                    val distIdx = headers.indexOfFirst { it.contains("dist") }
                    val divIdx = headers.indexOfFirst { it.contains("div") }
                    val routingIdx = headers.indexOfFirst { 
                        it.contains("rout") || it.contains("set") || it.contains("hub") || it.contains("nsh") || it.contains("ph") 
                    }

                    for (line in lines.drop(1)) {
                        // Split while ignoring commas inside quotes
                        val cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                            .map { it.trim().removeSurrounding("\"") }
                        if (cols.isEmpty() || cols.all { it.isEmpty() }) continue

                        val pin = if (pinIdx != -1 && pinIdx < cols.size) cols[pinIdx] else (cols.getOrNull(0) ?: "")
                        val office = if (officeIdx != -1 && officeIdx < cols.size) cols[officeIdx] else (cols.getOrNull(1) ?: "")
                        val type = if (typeIdx != -1 && typeIdx < cols.size) cols[typeIdx] else "SO/BO"
                        val district = if (distIdx != -1 && distIdx < cols.size) cols[distIdx] else ""
                        
                        // Use column division, or fall back to the filename region
                        val division = if (divIdx != -1 && divIdx < cols.size && cols[divIdx].isNotBlank()) {
                            cols[divIdx]
                        } else if (fileRegion.isNotEmpty()) {
                            fileRegion
                        } else {
                            district
                        }

                        val routing = if (routingIdx != -1 && routingIdx < cols.size) {
                            cols[routingIdx]
                        } else {
                            cols.lastOrNull() ?: "Main Set"
                        }

                        recordList.add(
                            PostalRecord(
                                pin = pin,
                                officeName = office,
                                officeType = type,
                                district = district.ifEmpty { fileRegion },
                                division = division,
                                routing = routing,
                                category = fileCategory
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return recordList
}

@Composable
fun PostalSortingApp() {
    val context = LocalContext.current
    val allRecords = remember { loadAllAssetsCsv(context) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val currentTabCategory = if (selectedTabIndex == 0) "NSH" else "PH"
    val tabTitles = listOf("NSH", "PH", "Sorting Test")
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedDivisionChip by remember { mutableStateOf("ALL") }

    val currentTabRecords = remember(allRecords, selectedTabIndex) {
        if (selectedTabIndex == 2) allRecords else allRecords.filter { it.category == currentTabCategory }
    }

    // Dynamic regions from the current tab's CSV files
    val allRegions = remember(currentTabRecords) {
        val extracted = currentTabRecords.map { it.division }.filter { it.isNotBlank() }.distinct()
        if (extracted.isEmpty()) listOf("Dhar Dewas", "Khandwa Khargone", "Air", "Indore", "Indore MFL") else extracted
    }

    var selectedRegions by remember { mutableStateOf(allRegions.toSet()) }
    var isDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(allRegions) {
        selectedRegions = allRegions.toSet()
        selectedDivisionChip = "ALL"
    }

    val postalRed = Color(0xFF8B1515L)
    val saffronOrange = Color(0xFFE87A00L)
    val darkHeading = Color(0xFF1E293BL)
    val subHeadingHindi = Color(0xFF555555L)
    val tabInactiveColor = Color(0xFF64748BL)
    val outerBg = Color(0xFFEAEFF5L)

    val filteredRecords = currentTabRecords.filter { record ->
        val matchesPill = (selectedDivisionChip == "ALL") || record.division.equals(selectedDivisionChip, ignoreCase = true)
        val matchesDropdown = selectedRegions.isEmpty() || selectedRegions.any { record.division.contains(it, ignoreCase = true) }
        val matchesSearch = searchQuery.isEmpty() || 
            listOf(record.pin, record.officeName, record.district, record.division, record.routing)
                .any { it.contains(searchQuery, ignoreCase = true) }

        matchesPill && matchesDropdown && matchesSearch
    }

    val isAllSelected = selectedRegions.size == allRegions.size

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

            // Region Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(
                    text = "Search All",
                    isSelected = (selectedDivisionChip == "ALL"),
                    isSearchAll = true,
                    hasGreenDot = false,
                    onClick = { selectedDivisionChip = "ALL" }
                )

                allRegions.forEach { div ->
                    val showDot = div.contains("Dewas", ignoreCase = true) || 
                                  div.contains("Khargone", ignoreCase = true) || 
                                  div.equals("Air", ignoreCase = true)

                    PillButton(
                        text = div,
                        isSelected = (selectedDivisionChip == div),
                        isSearchAll = false,
                        hasGreenDot = showDot,
                        onClick = { selectedDivisionChip = div }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0L)))

            // Search Bar & Dropdown Controls
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFBFBFBL))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD1D5DBL))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search across selected regions...",
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

                // Action Bar: "Search Regions All ▼" + Record Count
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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
                                        text = if (isAllSelected) "All" else "${selectedRegions.size}",
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

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF1F5F9L)
                        ) {
                            Text(
                                text = "${filteredRecords.size} Records",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569L),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (isDropdownOpen) {
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = androidx.compose.ui.unit.IntOffset(0, 110),
                            onDismissRequest = { isDropdownOpen = false }
                        ) {
                            Card(
                                modifier = Modifier
                                    .width(260.dp)
                                    .padding(top = 4.dp),
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
                                                selectedRegions = if (isAllSelected) emptySet() else allRegions.toSet()
                                            }
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isAllSelected,
                                            onCheckedChange = { checked ->
                                                selectedRegions = if (checked) allRegions.toSet() else emptySet()
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = postalRed,
                                                checkmarkColor = Color.White
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Select All", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = darkHeading)
                                    }

                                    Divider(color = Color(0xFFF1F5F9L), thickness = 1.dp)

                                    allRegions.forEach { region ->
                                        val isChecked = selectedRegions.contains(region)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedRegions = if (isChecked) selectedRegions - region else selectedRegions + region
                                                }
                                                .padding(vertical = 2.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedRegions = if (checked) selectedRegions + region else selectedRegions - region
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = postalRed,
                                                    checkmarkColor = Color.White
                                                )
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

                Spacer(modifier = Modifier.height(6.dp))

                // Records List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    items(filteredRecords) { record ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${record.officeName} (${record.pin})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${record.officeType} • ${record.division}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                Surface(
                                    color = Color(0xFFFBEEEDL),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = record.routing,
                                        color = postalRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
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
fun PillButton(
    text: String,
    isSelected: Boolean,
    isSearchAll: Boolean,
    hasGreenDot: Boolean,
    onClick: () -> Unit
) {
    val postalRed = Color(0xFF8B1515L)
    val textDark = Color(0xFF2C3E50L)
    val borderLightGray = Color(0xFFE2E8F0L)
    val dotGreen = Color(0xFF22C55EL)

    val bgColor = when {
        isSelected && isSearchAll -> postalRed
        else -> Color.White
    }

    val borderColor = when {
        isSelected && !isSearchAll -> postalRed
        isSelected && isSearchAll -> postalRed
        else -> borderLightGray
    }

    val textColor = when {
        isSelected && isSearchAll -> Color.White
        isSelected && !isSearchAll -> postalRed
        else -> textDark
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(
            width = if (isSelected && !isSearchAll) 1.5.dp else 1.dp,
            color = borderColor
        ),
        shadowElevation = if (isSelected && isSearchAll) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
            if (hasGreenDot && !isSelected) {
                Spacer(modifier = Modifier.width(7.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotGreen, shape = CircleShape)
                )
            }
        }
    }
}
