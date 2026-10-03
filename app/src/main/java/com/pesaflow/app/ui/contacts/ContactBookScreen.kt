package com.pesaflow.app.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.ledger.ContactBook
import com.pesaflow.app.data.ledger.ContactEntry
import com.pesaflow.app.data.ledger.normalizeContact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactBookScreen(
    contacts: List<ContactEntry>,
    reprocessStatus: String? = null,
    rescanActive: Boolean = false,
    onCancelRescan: () -> Unit = {},
    onSave: (name: String, display: String, rel: String, cat: String, scope: String, notes: String, matchTerms: String) -> Unit,
    onDelete: (name: String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ContactEntry?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val filtered = remember(contacts, search) {
        if (search.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(search, ignoreCase = true) ||
                it.relationship.contains(search, ignoreCase = true) ||
                it.category.contains(search, ignoreCase = true)
        }
    }.sortedByDescending { it.lastSeen }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Contact Book") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Set a relationship and optional matching names here. Saving a rule immediately rescans your SMS history and updates matching transaction categories.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            reprocessStatus?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rescanActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (rescanActive) {
                        TextButton(onClick = onCancelRescan) { Text("Cancel") }
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            Text(
                "${filtered.size} contacts",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(filtered, key = { it.name }) { c ->
                    ContactRow(
                        entry = c,
                        onEdit = { editing = c },
                        onDelete = { onDelete(c.name) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        ContactEditDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, display, rel, cat, scope, notes, matchTerms ->
                onSave(name, display, rel, cat, scope, notes, matchTerms)
                showAdd = false
            }
        )
    }
    editing?.let { e ->
        ContactEditDialog(
            initial = e,
            onDismiss = { editing = null },
            onSave = { name, display, rel, cat, scope, notes, matchTerms ->
                onSave(name, display, rel, cat, scope, notes, matchTerms)
                editing = null
            }
        )
    }
}

@Composable
private fun ContactRow(
    entry: ContactEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.displayName, fontWeight = FontWeight.Bold)
                if (entry.relationship.isNotBlank()) {
                    Text(entry.relationship, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.category.isNotBlank()) {
                    Text("→ ${entry.category}", style = MaterialTheme.typography.bodySmall)
                }
                if (entry.matchTerms.isNotBlank()) {
                    Text("Also matches: ${entry.matchTerms}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${entry.transactionCount} tx · last ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(entry.lastSeen))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactEditDialog(
    initial: ContactEntry?,
    onDismiss: () -> Unit,
    onSave: (name: String, display: String, rel: String, cat: String, scope: String, notes: String, matchTerms: String) -> Unit
) {
    var display by remember { mutableStateOf(initial?.displayName ?: "") }
    var rel by remember { mutableStateOf(initial?.relationship ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "") }
    var scope by remember { mutableStateOf(initial?.scope ?: "BOTH") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var matchTerms by remember { mutableStateOf(initial?.matchTerms ?: "") }
    var relExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Contact" else "Edit Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = display,
                    onValueChange = { display = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = relExpanded,
                    onExpandedChange = { relExpanded = it }
                ) {
                    OutlinedTextField(
                        value = rel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Relationship") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = relExpanded,
                        onDismissRequest = { relExpanded = false }
                    ) {
                        ContactBook.relationships().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = { rel = r; relExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = matchTerms,
                    onValueChange = { matchTerms = it },
                    label = { Text("Also match parsed names") },
                    placeholder = { Text("Nancy, Daniel Mayhvjh") },
                    supportingText = { Text("Comma-separated names or phrases; matched as whole words after parsing.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cat,
                    onValueChange = { cat = it },
                    label = { Text("Auto-category (e.g. Food, Rent, Upkeep)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf("IN", "OUT", "BOTH").forEach { s ->
                        FilterChip(
                            selected = scope == s,
                            onClick = { scope = s },
                            label = { Text(s) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = if (initial != null) initial.name else normalizeContact(display)
                    onSave(n, display, rel, cat, scope, notes, matchTerms)
                },
                enabled = display.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
