package com.golfv.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.golfv.launcher.R
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val hiddenPackages = setOf(
    "com.android.launcher",
    "com.android.launcher3",
    "com.kyhero.car.myhost",
    "com.kyhero.car.myhost2",
    "com.xygala.backcar",
    "com.txznet.txz",
    "com.txznet.smartadapter",
    "com.txznet.aipal",
    "com.txznet.weather",
)

private data class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
)

@Composable
internal fun AppDrawerScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apps by produceState<List<AppEntry>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090C10))
            .padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.app_drawer),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 72.dp),
        )

        when (val loadedApps = apps) {
            null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF368DD0),
            )

            emptyList<AppEntry>() -> Text(
                text = stringResource(R.string.no_apps),
                color = Color(0xFF9BA8B5),
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 62.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = loadedApps,
                    key = { it.component.flattenToString() },
                ) { app ->
                    AppGridItem(
                        app = app,
                        onClick = {
                            launchIntent(
                                context,
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setComponent(app.component)
                                    .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                            )
                        },
                    )
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(56.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun AppGridItem(app: AppEntry, onClick: () -> Unit) {
    val icon = remember(app.component) {
        app.icon.toBitmap(width = 144, height = 144).asImageBitmap()
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.height(154.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x6618212B),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(76.dp),
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    }
    val excluded = hiddenPackages + context.packageName
    val collator = Collator.getInstance(Locale.getDefault())

    return activities
        .asSequence()
        .filterNot { it.activityInfo.packageName in excluded }
        .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        .mapNotNull { resolveInfo ->
            runCatching { resolveInfo.toAppEntry(packageManager) }.getOrNull()
        }
        .sortedWith { first, second -> collator.compare(first.label, second.label) }
        .toList()
}

private fun ResolveInfo.toAppEntry(packageManager: PackageManager): AppEntry = AppEntry(
    label = loadLabel(packageManager).toString().ifBlank { activityInfo.packageName },
    component = ComponentName(activityInfo.packageName, activityInfo.name),
    icon = loadIcon(packageManager),
)
