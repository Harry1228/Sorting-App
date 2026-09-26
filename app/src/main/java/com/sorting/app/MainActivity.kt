@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sorting.app

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader

data class PostalRecord(
    val pin: String,
    val officeName: String,
    val officeType: String,
    val district: String,
    val division: String,
    val nshRouting: String,
    val phRouting: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContent {
            PostalSortingApp(onPickCsv = { uri -> parseCsv(uri) })
        }
    }

    private fun parseCsv(uri: Uri): List<PostalRecord> {
        val list = mutableListOf<PostalRecord>()
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val lines = reader.readLines()
                    if (lines.isNotEmpty()) {
                        for (line in lines.drop(1)) {
                            val cols = line.split(",").map { it.trim() }
                            if (cols.size >= 7) {
                                list.add(
                                    PostalRecord(
                                        pin = cols[0],
                                        officeName = cols[1],
                                        officeType = cols[2],
                                        district = cols[3],
                                        division = cols[4],
                                        nshRouting = cols[5],
                                        phRouting = cols[6]
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

@Composable
fun PostalSortingApp(onPickCsv: (Uri) -> List<PostalRecord>) {
    var records by remember { mutableStateOf(listOf<PostalRecord>()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("NSH", "PH", "Sorting Test")
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedDivision by remember { mutableStateOf("ALL") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { records = onPickCsv(it) }
    }

    val postalRed = Color(0xFF8B1515L)
    val saffronOrange = Color(0xFFE87A00L)
    val darkHeading = Color(0xFF1E293BL)
    val subHeadingHindi = Color(0xFF555555L)
    val tabInactiveColor = Color(0xFF64748BL)
    val outerBg = Color(0xFFEAEFF5L)

    // Default divisions to display immediately (and merged when CSV is loaded)
    val defaultDivisions = listOf("Dhar Dewas", "Khandwa Khargone", "Air", "Indore", "Indore MFL")
    val divisions = remember(records) {
        val csvDivs = records.map { it.division.ifEmpty { it.district } }.distinct().filter { it.isNotEmpty() }
        if (csvDivs.isEmpty()) defaultDivisions else csvDivs
    }

    val filteredRecords = records.filter { record ->
        val divMatch = (selectedDivision == "ALL") || 
            (record.division.equals(selectedDivision, ignoreCase = true) || record.district.equals(selectedDivision, ignoreCase = true))
        val textMatch = searchQuery.isEmpty() || 
            listOf(record.pin, record.officeName, record.district, record.nshRouting, record.phRouting)
                .any { it.contains(searchQuery, ignoreCase = true) }
        divMatch && textMatch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(outerBg)
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Main Card Container with rounded top edges
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color.White)
        ) {
            // Top orange accent band
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

            // Divider above tabs
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0L)))

            // Tabs (NSH, PH, Sorting Test)
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

            // Body Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FAL))
            ) {
                if (selectedTabIndex == 2) {
                    // Sorting Test Mode
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Sorting Test Mode",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = darkHeading
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (records.isEmpty()) "Load a CSV file first to begin testing." else "Ready to test with ${records.size} loaded PIN records.",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { filePicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = postalRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Load CSV")
                                }
                            }
                        }
                    }
                } else {
                    // Custom Filter Pills Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. "Search All" Pill
                        PillButton(
                            text = "Search All",
                            isSelected = (selectedDivision == "ALL"),
                            isSearchAll = true,
                            hasGreenDot = false,
                            onClick = { selectedDivision = "ALL" }
                        )

                        // 2. Division Pills
                        divisions.forEach { div ->
                            // Show the green dot for route groups like Dhar Dewas, Khandwa Khargone, Air
                            val showDot = div.contains("Dewas", ignoreCase = true) || 
                                          div.contains("Khargone", ignoreCase = true) || 
                                          div.equals("Air", ignoreCase = true)

                            PillButton(
                                text = div,
                                isSelected = (selectedDivision == div),
                                isSearchAll = false,
                                hasGreenDot = showDot,
                                onClick = { selectedDivision = div }
                            )
                        }
                    }

                    // Search Input & Upload Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search PIN or Office...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = postalRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Load CSV")
                        }
                    }

                    // Records List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        items(filteredRecords) { record ->
                            val routing = if (selectedTabIndex == 1) record.phRouting else record.nshRouting
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
                                            text = "${record.officeType} • ${record.district}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Surface(
                                        color = Color(0xFFFBEEEDL),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = routing,
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

    // State styling matching the references
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
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
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
