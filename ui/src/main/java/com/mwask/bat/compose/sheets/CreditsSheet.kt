package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.backend.model.Credits
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreSpacers
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSheet(
    isOpen: Boolean,
    title: String,
    subtitle: String,
    imageUrl: String?,
    credits: Credits?,
    onDismiss: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = BackNotDismissSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextSecondary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EncoreArtworkImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.credits),
                        style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                    )
                    Text(
                        text = title,
                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (credits == null) {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                Text(
                    text = stringResource(R.string.loading_credits),
                    style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                )
            } else if (credits.isEmpty) {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                Text(
                    text = stringResource(R.string.no_credits_for_this_track),
                    style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                )
            } else {
                credits.groups.forEach { group ->
                    Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                    Text(
                        text = group.name,
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))
                    group.people.forEach { person ->
                        val opens = person.uri.startsWith("spotify:artist:")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (opens) {
                                        Modifier.clickable { onArtistClick(person.uri, person.name) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = person.name,
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EncoreColors.TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (person.role.isNotBlank()) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = person.role,
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        color = EncoreColors.TextSecondary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (credits.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                    HorizontalDivider(color = EncoreColors.PureBlack.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                    Text(
                        text = stringResource(R.string.sources),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))
                    credits.sources.forEach { source ->
                        Text(
                            text = source,
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
