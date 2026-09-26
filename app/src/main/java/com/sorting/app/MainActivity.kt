package com.sorting.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        setContent {
            PostalSortingApp(onPickCsv = { uri -> parseCsv(uri) })
        }
    }

    private fun parseCsv(uri: Uri): List<PostalRecord> {
        val list = mutableListOf<PostalRecord>()
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
        return list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostalSortingApp(onPickCsv: (Uri) -> List<PostalRecord>) {
    var records by remember { mutableStateOf(listOf<PostalRecord>()) }
    var selectedTab by remember { mutableStateOf("NSH") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDivision by remember { mutableStateOf("ALL") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { records = onPickCsv(it) }
    }

    val primaryRed = Color(0xFF9E1B1B)
    val divisions = remember(records) {
        listOf("ALL") + records.map { it.division.ifEmpty { it.district } }.distinct().filter { it.isNotEmpty() }
    }

    val filteredRecords = records.filter { record ->
        val divMatch = (selectedDivision == "ALL") || (record.division == selectedDivision || record.district == selectedDivision)
        val textMatch = searchQuery.isEmpty() || 
            listOf(record.pin, record.officeName, record.district, record.nshRouting, record.phRouting)
                .any { it.contains(searchQuery, ignoreCase = true) }
        divMatch && textMatch
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                Text("Department of Posts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text("भारतीय डाक • ID Division Indore", fontSize = 13.sp, color = primaryRed, fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFFF8F9FA))) {
            // Tab Selection Row
            TabRow(selectedTabIndex = if (selectedTab == "NSH") 0 else 1) {
                Tab(selected = selectedTab == "NSH", onClick = { selectedTab = "NSH" }, text = { Text("NSH") })
                Tab(selected = selectedTab == "PH", onClick = { selectedTab = "PH" }, text = { Text("PH") })
            }

            // Division Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                divisions.forEach { div ->
                    FilterChip(
                        selected = selectedDivision == div,
                        onClick = { selectedDivision = div },
                        label = { Text(if (div == "ALL") "Search All" else div) }
                    )
                }
            }

            // Search Bar & CSV Upload Button
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    colors = ButtonDefaults.buttonColors(containerColor = primaryRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Load CSV")
                }
            }

            // Results List
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(filteredRecords) { record ->
                    val routing = if (selectedTab == "PH") record.phRouting else record.nshRouting
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${record.officeName} (${record.pin})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${record.officeType} • ${record.district}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Surface(
                                color = Color(0xFFFBEEED),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = routing,
                                    color = primaryRed,
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
```[span_0](start_span)[span_0](end_span)

---

### 3. Add the Cloud Compiler (GitHub Actions)

Create this workflow file so GitHub automatically builds the `.apk` whenever code is saved:

**File 6: `.github/workflows/build.yml`**
```yaml
name: Build Android APK

on: [push, workflow_dispatch]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Generate Gradle Wrapper
        run: gradle wrapper

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload APK File
        uses: actions/upload-artifact@v4
        with:
          name: postal-sorting-app
          path: app/build/outputs/apk/debug/*.apk
          
