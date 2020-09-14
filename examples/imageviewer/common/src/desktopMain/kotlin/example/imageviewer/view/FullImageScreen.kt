/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.imageviewer.view

import java.awt.image.BufferedImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollableRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.graphics.asImageAsset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.foundation.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope.align
import androidx.compose.material.Surface
import androidx.compose.ui.unit.dp
import example.imageviewer.core.FilterType
import example.imageviewer.model.AppState
import example.imageviewer.model.ContentState
import example.imageviewer.model.ScreenType
import example.imageviewer.style.DarkGray
import example.imageviewer.style.DarkGreen
import example.imageviewer.style.Foreground
import example.imageviewer.style.MiniatureColor
import example.imageviewer.style.Transparent
import example.imageviewer.style.icBack
import example.imageviewer.style.icFilterGrayscaleOn
import example.imageviewer.style.icFilterGrayscaleOff
import example.imageviewer.style.icFilterPixelOn
import example.imageviewer.style.icFilterPixelOff
import example.imageviewer.style.icFilterBlurOn
import example.imageviewer.style.icFilterBlurOff
import example.imageviewer.style.icFilterUnknown
import example.imageviewer.utils.displayHeight
import example.imageviewer.utils.displayWidth
import example.imageviewer.utils.getDisplayBounds
import example.imageviewer.utils.toByteArray
import example.imageviewer.utils.cropImage
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.jetbrains.skija.Image
import org.jetbrains.skija.IRect
import java.awt.Rectangle

@Composable
fun setImageFullScreen(
    content: ContentState
) {
    if (content.isContentReady()) {
        Column {
            setToolBar(content.getSelectedImageName(), content)
            setImage(content)
        }
    } else {
        setLoadingScreen()
    }
}

@Composable
private fun setLoadingScreen() {

    Stack {
        Surface(color = MiniatureColor, modifier = Modifier.preferredHeight(44.dp)) {}
        Box(modifier = Modifier.align(Alignment.Center)) {
            Surface(color = DarkGray, elevation = 4.dp, shape = CircleShape) {
                CircularProgressIndicator(
                    modifier = Modifier.preferredSize(50.dp).padding(3.dp, 3.dp, 4.dp, 4.dp),
                    color = DarkGreen
                )
            }
        }
    }
}

@Composable
fun setToolBar(
    text: String,
    content: ContentState
) {

    Surface(color = MiniatureColor, modifier = Modifier.preferredHeight(44.dp)) {
        Row(modifier = Modifier.padding(end = 30.dp)) {
            Surface(
                color = Transparent,
                modifier = Modifier.padding(start = 20.dp).align(Alignment.CenterVertically),
                shape = CircleShape
            ) {
                Clickable(
                    onClick = {
                        if (content.isContentReady()) {
                            content.restoreMainImage()
                            AppState.screenState(ScreenType.Main)
                        }
                    }) {
                    Image(
                        icBack(),
                        modifier = Modifier.preferredSize(38.dp)
                    )
                }
            }
            Text(
                text,
                color = Foreground,
                maxLines = 1,
                modifier = Modifier.padding(start = 30.dp).weight(1f)
                    .align(Alignment.CenterVertically),
                style = MaterialTheme.typography.body1
            )

            Surface(
                color = Color(255, 255, 255, 40),
                modifier = Modifier.preferredSize(154.dp, 38.dp)
                    .align(Alignment.CenterVertically),
                shape = CircleShape
            ) {
                ScrollableRow {
                    Row {
                        for (type in FilterType.values()) {
                            FilterButton(content, type)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterButton(
    content: ContentState,
    type: FilterType,
    modifier: Modifier = Modifier.preferredSize(38.dp)
) {
    Surface(
        color = Transparent,
        modifier = Modifier.align(Alignment.CenterVertically),
        shape = CircleShape
    ) {
        Clickable(
            onClick = { content.toggleFilter(type)}
        ) {
            Image(
                getFilterImage(type = type, content = content),
                modifier
            )
        }
    }

    Spacer(Modifier.width(20.dp))
}

@Composable
fun getFilterImage(type: FilterType, content: ContentState): ImageAsset {

    return when (type) {
        FilterType.GrayScale -> if (content.isFilterEnabled(type)) icFilterGrayscaleOn() else icFilterGrayscaleOff()
        FilterType.Pixel -> if (content.isFilterEnabled(type)) icFilterPixelOn() else icFilterPixelOff()
        FilterType.Blur -> if (content.isFilterEnabled(type)) icFilterBlurOn() else icFilterBlurOff()
        else -> {
            icFilterUnknown()
        }
    }
}

@Composable
fun setImage(content: ContentState) {
    val drag = DragHandler()
    val scale = ScaleHandler()

    Surface(
        color = DarkGray,
        modifier = Modifier.fillMaxSize()
    ) {
        Draggable(onDrag = drag, modifier = Modifier.fillMaxSize()) {
            Scalable(onScale = scale, modifier = Modifier.fillMaxSize()) {
                val bitmap = imageByGesture(content, scale, drag)
                Image(
                    asset = bitmap.asImageAsset(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun imageByGesture(
    content: ContentState,
    scale: ScaleHandler,
    drag: DragHandler
): Image {
    val bitmap = cropBitmapByScale(content.getSelectedImage(), scale.factor.value, drag)
    val image = Image.makeFromEncoded(toByteArray(bitmap))
    if (scale.factor.value > 1f)
        return image

    if (abs(drag.getDistance().x) > displayWidth() / 10) {
        if (drag.getDistance().x < 0) {
            content.swipeNext()
        } else {
            content.swipePrevious()
        }
        drag.onCancel()
    }

    return image
}

private fun cropBitmapByScale(bitmap: BufferedImage, scale: Float, drag: DragHandler): BufferedImage {

    val crop = cropBitmapByBounds(
        bitmap,
        getDisplayBounds(bitmap),
        scale,
        drag
    )
    return cropImage(
        bitmap,
        Rectangle(crop.x, crop.y, crop.width - crop.x, crop.height - crop.y)
    )
}

private fun cropBitmapByBounds(
    bitmap: BufferedImage,
    bounds: Rectangle,
    scaleFactor: Float,
    drag: DragHandler
): Rectangle {

    if (scaleFactor <= 1f) {
        return Rectangle(0, 0, bitmap.width, bitmap.height)
    }

    var scale = scaleFactor.toDouble().pow(1.4)

    var boundW = (bounds.width / scale).roundToInt()
    var boundH = (bounds.height / scale).roundToInt()

    scale *= displayWidth() / bounds.width.toDouble()

    val offsetX = drag.getAmount().x / scale
    val offsetY = drag.getAmount().y / scale

    if (boundW > bitmap.width) {
        boundW = bitmap.width
    }
    if (boundH > bitmap.height) {
        boundH = bitmap.height
    }

    val invisibleW = bitmap.width - boundW
    var leftOffset = (invisibleW / 2.0 - offsetX).roundToInt()

    if (leftOffset > invisibleW) {
        leftOffset = invisibleW
        drag.getAmount().x = -((invisibleW / 2.0) * scale).roundToInt().toFloat()
    }
    if (leftOffset < 0) {
        drag.getAmount().x = ((invisibleW / 2.0) * scale).roundToInt().toFloat()
        leftOffset = 0
    }

    val invisibleH = bitmap.height - boundH
    var topOffset = (invisibleH / 2 - offsetY).roundToInt()

    if (topOffset > invisibleH) {
        topOffset = invisibleH
        drag.getAmount().y = -((invisibleH / 2.0) * scale).roundToInt().toFloat()
    }
    if (topOffset < 0) {
        drag.getAmount().y = ((invisibleH / 2.0) * scale).roundToInt().toFloat()
        topOffset = 0
    }

    return Rectangle(leftOffset, topOffset, leftOffset + boundW, topOffset + boundH)
}
