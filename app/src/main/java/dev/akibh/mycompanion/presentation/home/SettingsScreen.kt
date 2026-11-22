package dev.akibh.mycompanion.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionGap

/** Settings groups the existing tools; appearance follows Android's light/dark setting. */
@Composable
fun SettingsScreen(links: List<HomeLink>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val groups = links.distinctBy { it.route }.groupBy { groupOf(it.route) }.toSortedMap()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = PageMargin, top = 8.dp, end = PageMargin, bottom = SectionGap),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text("A little more yours.", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Monochrome appearance follows your phone's light or dark setting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        groups.forEach { (group, items) ->
            item(key = group.name) {
                Section(group.label) {
                    items.forEachIndexed { index, link ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LinkRow(link) { onOpen(link.route) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: HomeLink, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(link.title, style = MaterialTheme.typography.titleSmall)
            Text(
                link.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Where a link sits on the Settings page; the order here is the order on screen. */
private enum class SettingsGroup(val label: String) {
    YOUR_DATA("Your data"),
    PHONE("Phone"),
    MORE("More"),
    DEVELOPER("Developer"),
}

private fun groupOf(route: String): SettingsGroup = when (route) {
    "export" -> SettingsGroup.YOUR_DATA
    PERMISSIONS_ROUTE -> SettingsGroup.PHONE
    AI_ROUTE, "log" -> SettingsGroup.DEVELOPER
    else -> SettingsGroup.MORE
}
