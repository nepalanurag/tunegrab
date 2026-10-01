package com.tunegrab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.theme.TuneGrabTheme

/**
 * The Now Playing seek bar: thick tonal track, large thumb, and a time
 * bubble that follows the thumb while dragging.
 *
 * [value] is 0..1. Pass [formatTime] to show the drag bubble (e.g. seek
 * position); without it the bubble is hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    formatTime: ((Float) -> String)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> dragging = true
                is DragInteraction.Stop -> dragging = false
                is DragInteraction.Cancel -> dragging = false
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val trackWidth = maxWidth
        if (dragging && formatTime != null) {
            // Thumb travel is inset by roughly the thumb radius on each side.
            val fraction = value.coerceIn(0f, 1f)
            val thumbX = 12.dp + (trackWidth - 24.dp) * fraction
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = thumbX - 28.dp, y = (-6).dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = formatTime(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    thumbSize = DpSize(22.dp, 22.dp),
                )
            },
            track = {
                SliderDefaults.Track(
                    sliderState = it,
                    modifier = Modifier.height(10.dp),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.Center),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpressiveSliderPreview() {
    TuneGrabTheme(darkTheme = true) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            ExpressiveSlider(
                value = 0.35f,
                onValueChange = {},
                formatTime = { "1:12" },
            )
        }
    }
}
