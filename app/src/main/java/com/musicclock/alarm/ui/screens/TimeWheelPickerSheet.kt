package com.musicclock.alarm.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicclock.alarm.ui.UiTestTags
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeWheelPickerSheet(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    var draftHour by remember { mutableIntStateOf(initialHour) }
    var draftMinute by remember { mutableIntStateOf(initialMinute) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "设置时间",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "24 小时制",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Text(
                "%02d:%02d".format(draftHour, draftMinute),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .testTag(UiTestTags.TIME_PICKER_VALUE),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                NumberWheel(
                    values = (0..23).toList(),
                    initialValue = initialHour,
                    onValueChange = { draftHour = it },
                    modifier = Modifier
                        .width(112.dp)
                        .testTag(UiTestTags.HOUR_WHEEL),
                    contentDescription = "小时",
                )
                Text(
                    ":",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                NumberWheel(
                    values = (0..59).toList(),
                    initialValue = initialMinute,
                    onValueChange = { draftMinute = it },
                    modifier = Modifier
                        .width(112.dp)
                        .testTag(UiTestTags.MINUTE_WHEEL),
                    contentDescription = "分钟",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(UiTestTags.TIME_PICKER_CANCEL),
                ) {
                    Text("取消")
                }
                TextButton(
                    onClick = { onConfirm(draftHour, draftMinute) },
                    modifier = Modifier.testTag(UiTestTags.TIME_PICKER_CONFIRM),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    values: List<Int>,
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    val count = values.size
    val loops = 10_000
    val middleLoop = loops / 2
    val initialIndex = middleLoop * count + initialValue.coerceIn(values.indices)
    val itemHeight = 56.dp
    val wheelHeight = 280.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val scope = rememberCoroutineScope()
    val selectedIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset >= itemHeightPx / 2f) 1 else 0
        }
    }

    LaunchedEffect(listState, count) {
        snapshotFlow {
            if (listState.isScrollInProgress) null
            else values[selectedIndex.mod(count)]
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(onValueChange)
    }

    Box(
        modifier = modifier
            .height(wheelHeight)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp),
                ),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = (wheelHeight - itemHeight) / 2),
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                count = loops * count,
                key = { it },
            ) { index ->
                val value = values[index.mod(count)]
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(value),
                        fontSize = if (selected) 34.sp else 24.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                        },
                        modifier = Modifier.graphicsLayer {
                            val distance = abs(index - selectedIndex).coerceAtMost(3)
                            alpha = if (selected) 1f else (0.72f - distance * 0.12f).coerceAtLeast(0.35f)
                        },
                    )
                }
            }
        }
    }
}
