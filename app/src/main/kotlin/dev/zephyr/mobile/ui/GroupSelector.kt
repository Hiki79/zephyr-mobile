package dev.zephyr.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zephyr.mobile.data.ProxyItem
import dev.zephyr.mobile.groupLatency
import dev.zephyr.mobile.layoutGroups

/** The full picker stays visible even after scrolling the quick-access row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelector(groups: List<ProxyItem>, proxies: Map<String, ProxyItem>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rowState = rememberLazyListState()
    // Groups that merely follow another one are summarised rather than listed.
    val layout = remember(groups, proxies, selected) { layoutGroups(groups, proxies, selected) }
    val quick = layout.primary
    LaunchedEffect(selected, quick.map { it.name }) {
        val index = quick.indexOfFirst { it.name == selected }
        if (index >= 0) rowState.animateScrollToItem(index)
    }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (layout.folded > 0) "出口分组 · ${quick.size} / ${groups.size}" else "策略分组 · ${groups.size}",
                color = Z.muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            ZButton("全部分组", onClick = { expanded = true }, icon = ZIcon.ListChecks, small = true)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            items(quick, key = { it.name }) { group ->
                GroupChoice(group, proxies, group.name == selected, { onSelect(group.name) }, Modifier.width(146.dp))
            }
        }
        if (layout.folded > 0) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Z.radiusSm))
                    .background(Z.hover)
                    .clickable(role = Role.Button) { expanded = true }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "另有 " + layout.summary(),
                    fontSize = 12.sp, color = Z.muted, lineHeight = 16.sp, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Icon(ZIcon.ChevronRight, "查看全部分组", tint = Z.muted, modifier = Modifier.size(13.dp))
            }
        }
    }
    if (expanded) {
        var query by rememberSaveable { mutableStateOf("") }
        val filtered = remember(groups, query) { groups.filter { it.name.contains(query.trim(), ignoreCase = true) } }
        val grid = rememberLazyGridState()
        LaunchedEffect(query) { grid.scrollToItem(0) }
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Z.paper,
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("选择策略组", fontSize = 18.sp, color = Z.ink, fontWeight = FontWeight.SemiBold)
                        Text("${filtered.size} / ${groups.size} 个分组 · 点按进入", fontSize = 12.sp, color = Z.muted)
                    }
                    IconButton(onClick = { expanded = false }) { Icon(ZIcon.Close, "关闭分组选择") }
                }
                Spacer(Modifier.height(12.dp))
                ZTextField(query, { query = it }, "搜索分组，例如 油管、自动选择", leading = ZIcon.Search,
                    rounded = true, modifier = Modifier.fillMaxWidth(), clearable = true)
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Text("没有匹配的分组", color = Z.muted, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    // Large font settings get wider cells rather than truncated controls.
                    val minWidth = 144.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minWidth), state = grid,
                        modifier = Modifier.weight(1f).fillMaxWidth().selectableGroup(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.name }) { group ->
                            GroupChoice(group, proxies, group.name == selected, {
                                onSelect(group.name)
                                expanded = false
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChoice(group: ProxyItem, proxies: Map<String, ProxyItem>, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(Z.radiusSm)
    Column(modifier.clip(shape).background(if (selected) Z.bluePale else Z.card)
        .border(1.dp, if (selected) Z.blue else Z.line, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) Z.blueDark else Z.ink, minLines = 2, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(ZIcon.Check, "当前分组", tint = Z.blue, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(group.now?.let { "→ $it" } ?: group.type, fontSize = 11.sp, color = Z.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${group.all?.size ?: 0} 个节点", fontSize = 10.5.sp, color = Z.faint, modifier = Modifier.weight(1f))
            DelayDot(groupLatency(proxies, group))
        }
    }
}
