package com.example.aichat.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons

/**
 * 项目使用的 Material 扩展图标。
 * 从 material-icons-extended 依赖中提取，避免引入整个 2-3MB 的图标库。
 *
 * 图标数据来源：https://fonts.google.com/icons (Apache 2.0)
 */

val Icons.Extended: ExtendedIcons
    get() = ExtendedIcons

object ExtendedIcons {
    val Psychology: ImageVector
        get() = _psychology

    val Language: ImageVector
        get() = _language

    val CameraAlt: ImageVector
        get() = _cameraAlt

    val PhotoLibrary: ImageVector
        get() = _photoLibrary

    val Description: ImageVector
        get() = _description

    val ArrowForward: ImageVector
        get() = _arrowForward

    val BrokenImage: ImageVector
        get() = _brokenImage

    val ContentCopy: ImageVector
        get() = _contentCopy

    val PushPin: ImageVector
        get() = _pushPin

    val Stop: ImageVector
        get() = _stop

    val Visibility: ImageVector
        get() = _visibility

    val VisibilityOff: ImageVector
        get() = _visibilityOff
}

private val _psychology = Builder(
    name = "Psychology",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(440f, 560f)
        lineTo(520f, 560f)
        quadTo(520f, 520f, 545f, 490f)
        quadTo(570f, 460f, 600f, 440f)
        quadTo(630f, 420f, 650f, 390f)
        quadTo(670f, 360f, 670f, 320f)
        quadTo(670f, 260f, 625f, 225f)
        quadTo(580f, 190f, 520f, 190f)
        quadTo(470f, 190f, 430f, 217.5f)
        quadTo(390f, 245f, 370f, 290f)
        lineTo(440f, 320f)
        quadTo(450f, 295f, 472.5f, 277.5f)
        quadTo(495f, 260f, 520f, 260f)
        quadTo(550f, 260f, 570f, 277.5f)
        quadTo(590f, 295f, 590f, 320f)
        quadTo(590f, 345f, 570f, 365f)
        quadTo(550f, 385f, 525f, 405f)
        quadTo(500f, 425f, 480f, 455f)
        quadTo(460f, 485f, 460f, 530f)
        lineTo(440f, 560f)
        close()
        moveTo(480f, 680f)
        quadTo(497f, 680f, 508.5f, 668.5f)
        quadTo(520f, 657f, 520f, 640f)
        quadTo(520f, 623f, 508.5f, 611.5f)
        quadTo(497f, 600f, 480f, 600f)
        quadTo(463f, 600f, 451.5f, 611.5f)
        quadTo(440f, 623f, 440f, 640f)
        quadTo(440f, 657f, 451.5f, 668.5f)
        quadTo(463f, 680f, 480f, 680f)
        close()
        moveTo(480f, 880f)
        quadTo(397f, 880f, 325f, 848.5f)
        quadTo(253f, 817f, 198f, 762f)
        quadTo(143f, 707f, 111.5f, 635f)
        quadTo(80f, 563f, 80f, 480f)
        quadTo(80f, 397f, 111.5f, 325f)
        quadTo(143f, 253f, 198f, 198f)
        quadTo(253f, 143f, 325f, 111.5f)
        quadTo(397f, 80f, 480f, 80f)
        quadTo(563f, 80f, 635f, 111.5f)
        quadTo(707f, 143f, 762f, 198f)
        quadTo(817f, 253f, 848.5f, 325f)
        quadTo(880f, 397f, 880f, 480f)
        quadTo(880f, 563f, 848.5f, 635f)
        quadTo(817f, 707f, 762f, 762f)
        quadTo(707f, 817f, 635f, 848.5f)
        quadTo(563f, 880f, 480f, 880f)
        close()
        moveTo(480f, 800f)
        quadTo(613f, 800f, 706.5f, 706.5f)
        quadTo(800f, 613f, 800f, 480f)
        quadTo(800f, 347f, 706.5f, 253.5f)
        quadTo(613f, 160f, 480f, 160f)
        quadTo(347f, 160f, 253.5f, 253.5f)
        quadTo(160f, 347f, 160f, 480f)
        quadTo(160f, 613f, 253.5f, 706.5f)
        quadTo(347f, 800f, 480f, 800f)
        close()
    }
}.build()

private val _language = Builder(
    name = "Language",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(480f, 880f)
        quadTo(397f, 880f, 325f, 848.5f)
        quadTo(253f, 817f, 198f, 762f)
        quadTo(143f, 707f, 111.5f, 635f)
        quadTo(80f, 563f, 80f, 480f)
        quadTo(80f, 397f, 111.5f, 325f)
        quadTo(143f, 253f, 198f, 198f)
        quadTo(253f, 143f, 325f, 111.5f)
        quadTo(397f, 80f, 480f, 80f)
        quadTo(563f, 80f, 635f, 111.5f)
        quadTo(707f, 143f, 762f, 198f)
        quadTo(817f, 253f, 848.5f, 325f)
        quadTo(880f, 397f, 880f, 480f)
        quadTo(880f, 563f, 848.5f, 635f)
        quadTo(817f, 707f, 762f, 762f)
        quadTo(707f, 817f, 635f, 848.5f)
        quadTo(563f, 880f, 480f, 880f)
        close()
        moveTo(480f, 800f)
        quadTo(535f, 800f, 585f, 785f)
        quadTo(635f, 770f, 680f, 740f)
        quadTo(635f, 710f, 585f, 695f)
        quadTo(535f, 680f, 480f, 680f)
        quadTo(425f, 680f, 375f, 695f)
        quadTo(325f, 710f, 280f, 740f)
        quadTo(325f, 770f, 375f, 785f)
        quadTo(425f, 800f, 480f, 800f)
        close()
        moveTo(480f, 600f)
        quadTo(545f, 600f, 603f, 620f)
        quadTo(661f, 640f, 710f, 680f)
        quadTo(745f, 640f, 762.5f, 590f)
        quadTo(780f, 540f, 780f, 480f)
        quadTo(780f, 347f, 686.5f, 253.5f)
        quadTo(593f, 160f, 480f, 160f)
        quadTo(367f, 160f, 273.5f, 253.5f)
        quadTo(180f, 347f, 180f, 480f)
        quadTo(180f, 540f, 197.5f, 590f)
        quadTo(215f, 640f, 250f, 680f)
        quadTo(299f, 640f, 357f, 620f)
        quadTo(415f, 600f, 480f, 600f)
        close()
    }
}.build()

private val _cameraAlt = Builder(
    name = "CameraAlt",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(480f, 700f)
        quadTo(555f, 700f, 607.5f, 647.5f)
        quadTo(660f, 595f, 660f, 520f)
        quadTo(660f, 445f, 607.5f, 392.5f)
        quadTo(555f, 340f, 480f, 340f)
        quadTo(405f, 340f, 352.5f, 392.5f)
        quadTo(300f, 445f, 300f, 520f)
        quadTo(300f, 595f, 352.5f, 647.5f)
        quadTo(405f, 700f, 480f, 700f)
        close()
        moveTo(480f, 640f)
        quadTo(430f, 640f, 395f, 605f)
        quadTo(360f, 570f, 360f, 520f)
        quadTo(360f, 470f, 395f, 435f)
        quadTo(430f, 400f, 480f, 400f)
        quadTo(530f, 400f, 565f, 435f)
        quadTo(600f, 470f, 600f, 520f)
        quadTo(600f, 570f, 565f, 605f)
        quadTo(530f, 640f, 480f, 640f)
        close()
        moveTo(320f, 840f)
        lineTo(280f, 760f)
        lineTo(200f, 740f)
        quadTo(167f, 733f, 143.5f, 707f)
        quadTo(120f, 681f, 120f, 648f)
        lineTo(120f, 240f)
        quadTo(120f, 207f, 143.5f, 181f)
        quadTo(167f, 155f, 200f, 148f)
        lineTo(280f, 128f)
        lineTo(320f, 48f)
        lineTo(640f, 48f)
        lineTo(680f, 128f)
        lineTo(760f, 148f)
        quadTo(793f, 155f, 816.5f, 181f)
        quadTo(840f, 207f, 840f, 240f)
        lineTo(840f, 648f)
        quadTo(840f, 681f, 816.5f, 707f)
        quadTo(793f, 733f, 760f, 740f)
        lineTo(680f, 760f)
        lineTo(640f, 840f)
        close()
        moveTo(360f, 760f)
        lineTo(600f, 760f)
        lineTo(640f, 680f)
        lineTo(740f, 656f)
        lineTo(760f, 240f)
        lineTo(740f, 224f)
        lineTo(640f, 200f)
        lineTo(600f, 120f)
        lineTo(360f, 120f)
        lineTo(320f, 200f)
        lineTo(220f, 224f)
        lineTo(200f, 640f)
        lineTo(220f, 656f)
        lineTo(320f, 680f)
        close()
    }
}.build()

private val _photoLibrary = Builder(
    name = "PhotoLibrary",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(320f, 640f)
        lineTo(800f, 640f)
        lineTo(660f, 440f)
        lineTo(560f, 580f)
        lineTo(500f, 500f)
        close()
        moveTo(280f, 760f)
        quadTo(247f, 760f, 223.5f, 736.5f)
        quadTo(200f, 713f, 200f, 680f)
        lineTo(200f, 120f)
        quadTo(200f, 87f, 223.5f, 63.5f)
        quadTo(247f, 40f, 280f, 40f)
        lineTo(840f, 40f)
        quadTo(873f, 40f, 896.5f, 63.5f)
        quadTo(920f, 87f, 920f, 120f)
        lineTo(920f, 680f)
        quadTo(920f, 713f, 896.5f, 736.5f)
        quadTo(873f, 760f, 840f, 760f)
        close()
        moveTo(280f, 680f)
        lineTo(840f, 680f)
        lineTo(840f, 120f)
        lineTo(280f, 120f)
        close()
        moveTo(120f, 920f)
        quadTo(87f, 920f, 63.5f, 896.5f)
        quadTo(40f, 873f, 40f, 840f)
        lineTo(40f, 200f)
        lineTo(120f, 200f)
        lineTo(120f, 840f)
        lineTo(760f, 840f)
        lineTo(760f, 920f)
        close()
    }
}.build()

private val _description = Builder(
    name = "Description",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(320f, 720f)
        lineTo(640f, 720f)
        lineTo(640f, 640f)
        lineTo(320f, 640f)
        close()
        moveTo(320f, 560f)
        lineTo(640f, 560f)
        lineTo(640f, 480f)
        lineTo(320f, 480f)
        close()
        moveTo(240f, 880f)
        quadTo(207f, 880f, 183.5f, 856.5f)
        quadTo(160f, 833f, 160f, 800f)
        lineTo(160f, 160f)
        quadTo(160f, 127f, 183.5f, 103.5f)
        quadTo(207f, 80f, 240f, 80f)
        lineTo(560f, 80f)
        lineTo(800f, 320f)
        lineTo(800f, 800f)
        quadTo(800f, 833f, 776.5f, 856.5f)
        quadTo(753f, 880f, 720f, 880f)
        close()
        moveTo(520f, 360f)
        lineTo(520f, 160f)
        lineTo(240f, 160f)
        lineTo(240f, 800f)
        lineTo(720f, 800f)
        lineTo(720f, 360f)
        close()
        moveTo(240f, 160f)
        lineTo(240f, 160f)
        lineTo(520f, 160f)
        lineTo(520f, 360f)
        lineTo(720f, 360f)
        lineTo(720f, 800f)
        lineTo(240f, 800f)
        close()
    }
}.build()

private val _arrowForward = Builder(
    name = "ArrowForward",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(647f, 520f)
        lineTo(243f, 520f)
        quadTo(226f, 520f, 213f, 507f)
        quadTo(200f, 494f, 200f, 477f)
        quadTo(200f, 460f, 213f, 447f)
        quadTo(226f, 434f, 243f, 434f)
        lineTo(647f, 434f)
        lineTo(483f, 270f)
        quadTo(471f, 258f, 471.5f, 241.5f)
        quadTo(472f, 225f, 484f, 212f)
        quadTo(497f, 200f, 514f, 200f)
        quadTo(531f, 200f, 544f, 212f)
        lineTo(748f, 416f)
        quadTo(761f, 429f, 766.5f, 445f)
        quadTo(772f, 461f, 772f, 480f)
        quadTo(772f, 499f, 766.5f, 515f)
        quadTo(761f, 531f, 748f, 544f)
        lineTo(544f, 748f)
        quadTo(531f, 761f, 514.5f, 760.5f)
        quadTo(498f, 760f, 484f, 748f)
        quadTo(471f, 735f, 471f, 718.5f)
        quadTo(471f, 702f, 484f, 689f)
        close()
    }
}.build()

private val _brokenImage = Builder(
    name = "BrokenImage",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(200f, 840f)
        quadTo(167f, 840f, 143.5f, 816.5f)
        quadTo(120f, 793f, 120f, 760f)
        lineTo(120f, 200f)
        quadTo(120f, 167f, 143.5f, 143.5f)
        quadTo(167f, 120f, 200f, 120f)
        lineTo(760f, 120f)
        quadTo(793f, 120f, 816.5f, 143.5f)
        quadTo(840f, 167f, 840f, 200f)
        lineTo(840f, 760f)
        quadTo(840f, 793f, 816.5f, 816.5f)
        quadTo(793f, 840f, 760f, 840f)
        close()
        moveTo(200f, 760f)
        lineTo(360f, 760f)
        lineTo(360f, 680f)
        lineTo(280f, 600f)
        lineTo(360f, 520f)
        lineTo(360f, 440f)
        lineTo(200f, 440f)
        lineTo(200f, 760f)
        close()
        moveTo(600f, 760f)
        lineTo(760f, 760f)
        lineTo(760f, 440f)
        lineTo(600f, 440f)
        lineTo(600f, 520f)
        lineTo(680f, 600f)
        lineTo(600f, 680f)
        close()
        moveTo(200f, 360f)
        lineTo(360f, 360f)
        lineTo(360f, 280f)
        lineTo(280f, 200f)
        lineTo(360f, 120f)
        lineTo(200f, 120f)
        lineTo(200f, 360f)
        close()
        moveTo(600f, 360f)
        lineTo(760f, 360f)
        lineTo(760f, 120f)
        lineTo(600f, 120f)
        lineTo(600f, 200f)
        lineTo(680f, 280f)
        lineTo(600f, 360f)
        close()
    }
}.build()

private val _contentCopy = Builder(
    name = "ContentCopy",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(360f, 840f)
        quadTo(327f, 840f, 303.5f, 816.5f)
        quadTo(280f, 793f, 280f, 760f)
        lineTo(280f, 280f)
        quadTo(280f, 247f, 303.5f, 223.5f)
        quadTo(327f, 200f, 360f, 200f)
        lineTo(760f, 200f)
        quadTo(793f, 200f, 816.5f, 223.5f)
        quadTo(840f, 247f, 840f, 280f)
        lineTo(840f, 760f)
        quadTo(840f, 793f, 816.5f, 816.5f)
        quadTo(793f, 840f, 760f, 840f)
        close()
        moveTo(360f, 760f)
        lineTo(760f, 760f)
        lineTo(760f, 280f)
        lineTo(360f, 280f)
        close()
        moveTo(200f, 680f)
        lineTo(200f, 200f)
        quadTo(200f, 167f, 223.5f, 143.5f)
        quadTo(247f, 120f, 280f, 120f)
        lineTo(680f, 120f)
        lineTo(680f, 200f)
        lineTo(280f, 200f)
        lineTo(280f, 680f)
        close()
    }
}.build()

private val _pushPin = Builder(
    name = "PushPin",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(640f, 478f)
        lineTo(640f, 200f)
        quadTo(640f, 183f, 628.5f, 171.5f)
        quadTo(617f, 160f, 600f, 160f)
        lineTo(360f, 160f)
        quadTo(343f, 160f, 331.5f, 171.5f)
        quadTo(320f, 183f, 320f, 200f)
        lineTo(320f, 478f)
        quadTo(320f, 502f, 306f, 521f)
        quadTo(292f, 540f, 270f, 550f)
        quadTo(240f, 564f, 220f, 590f)
        quadTo(200f, 616f, 200f, 648f)
        lineTo(200f, 680f)
        lineTo(420f, 680f)
        lineTo(450f, 840f)
        quadTo(454f, 856f, 468f, 864f)
        quadTo(482f, 872f, 498f, 868f)
        quadTo(514f, 864f, 522f, 850f)
        quadTo(530f, 836f, 526f, 820f)
        lineTo(500f, 680f)
        lineTo(760f, 680f)
        lineTo(760f, 648f)
        quadTo(760f, 616f, 740f, 590f)
        quadTo(720f, 564f, 690f, 550f)
        quadTo(668f, 540f, 654f, 521f)
        quadTo(640f, 502f, 640f, 478f)
        close()
    }
}.build()

private val _stop = Builder(
    name = "Stop",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(200f, 760f)
        quadTo(167f, 760f, 143.5f, 736.5f)
        quadTo(120f, 713f, 120f, 680f)
        lineTo(120f, 280f)
        quadTo(120f, 247f, 143.5f, 223.5f)
        quadTo(167f, 200f, 200f, 200f)
        lineTo(760f, 200f)
        quadTo(793f, 200f, 816.5f, 223.5f)
        quadTo(840f, 247f, 840f, 280f)
        lineTo(840f, 680f)
        quadTo(840f, 713f, 816.5f, 736.5f)
        quadTo(793f, 760f, 760f, 760f)
        close()
        moveTo(200f, 680f)
        lineTo(760f, 680f)
        lineTo(760f, 280f)
        lineTo(200f, 280f)
        close()
    }
}.build()

private val _visibility = Builder(
    name = "Visibility",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(480f, 640f)
        quadTo(546f, 640f, 593f, 593f)
        quadTo(640f, 546f, 640f, 480f)
        quadTo(640f, 414f, 593f, 367f)
        quadTo(546f, 320f, 480f, 320f)
        quadTo(414f, 320f, 367f, 367f)
        quadTo(320f, 414f, 320f, 480f)
        quadTo(320f, 546f, 367f, 593f)
        quadTo(414f, 640f, 480f, 640f)
        close()
        moveTo(480f, 580f)
        quadTo(439f, 580f, 409.5f, 550.5f)
        quadTo(380f, 521f, 380f, 480f)
        quadTo(380f, 439f, 409.5f, 409.5f)
        quadTo(439f, 380f, 480f, 380f)
        quadTo(521f, 380f, 550.5f, 409.5f)
        quadTo(580f, 439f, 580f, 480f)
        quadTo(580f, 521f, 550.5f, 550.5f)
        quadTo(521f, 580f, 480f, 580f)
        close()
        moveTo(480f, 760f)
        quadTo(340f, 760f, 223f, 687f)
        quadTo(106f, 614f, 40f, 480f)
        quadTo(106f, 346f, 223f, 273f)
        quadTo(340f, 200f, 480f, 200f)
        quadTo(620f, 200f, 737f, 273f)
        quadTo(854f, 346f, 920f, 480f)
        quadTo(854f, 614f, 737f, 687f)
        quadTo(620f, 760f, 480f, 760f)
        close()
    }
}.build()

private val _visibilityOff = Builder(
    name = "VisibilityOff",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(634f, 554f)
        lineTo(582f, 502f)
        quadTo(590f, 478f, 585f, 454f)
        quadTo(580f, 430f, 565f, 415f)
        quadTo(550f, 400f, 526f, 395f)
        quadTo(502f, 390f, 478f, 398f)
        lineTo(426f, 346f)
        quadTo(450f, 334f, 476f, 327f)
        quadTo(502f, 320f, 530f, 320f)
        quadTo(596f, 320f, 643f, 367f)
        quadTo(690f, 414f, 690f, 480f)
        quadTo(690f, 508f, 683f, 534f)
        quadTo(676f, 560f, 634f, 554f)
        close()
        moveTo(480f, 760f)
        quadTo(416f, 760f, 358f, 743f)
        quadTo(300f, 726f, 248f, 694f)
        lineTo(306f, 636f)
        quadTo(346f, 658f, 389f, 669f)
        quadTo(432f, 680f, 480f, 680f)
        quadTo(596f, 680f, 693f, 614f)
        quadTo(790f, 548f, 834f, 440f)
        quadTo(814f, 390f, 782f, 347f)
        quadTo(750f, 304f, 708f, 270f)
        lineTo(762f, 216f)
        quadTo(816f, 256f, 858f, 308f)
        quadTo(900f, 360f, 920f, 440f)
        quadTo(868f, 572f, 754f, 656f)
        quadTo(640f, 740f, 480f, 760f)
        close()
        moveTo(480f, 200f)
        quadTo(544f, 200f, 602f, 217f)
        quadTo(660f, 234f, 712f, 266f)
        lineTo(654f, 324f)
        quadTo(614f, 302f, 571f, 291f)
        quadTo(528f, 280f, 480f, 280f)
        quadTo(364f, 280f, 267f, 346f)
        quadTo(170f, 412f, 126f, 520f)
        quadTo(146f, 570f, 178f, 613f)
        quadTo(210f, 656f, 252f, 690f)
        lineTo(198f, 744f)
        quadTo(144f, 704f, 102f, 652f)
        quadTo(60f, 600f, 40f, 520f)
        quadTo(92f, 388f, 206f, 304f)
        quadTo(320f, 220f, 480f, 200f)
        close()
        moveTo(792f, 864f)
        lineTo(248f, 320f)
        lineTo(306f, 262f)
        lineTo(850f, 806f)
        close()
    }
}.build()
