package pl.kserocki.scratchticket.ticket

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import pl.kserocki.scratchticket.motion.DeviceTilt

// Iridescent foil: a rainbow band whose phase follows the tilt, laid over brushed silver,
// with a moving specular sheen, fine grain and sparse glints that wink as the angle changes.
private const val HOLO_FOIL_AGSL = """
uniform float2 size;
uniform float2 tilt;
uniform float density;

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 p) {
    float2 uv = p / size;
    float aspect = size.x / size.y;

    float ripple = sin(uv.x * 19.0 + uv.y * 6.0) * 0.5 + sin(uv.x * -7.0 + uv.y * 23.0) * 0.5;
    float phase = uv.x * aspect * 0.35 + uv.y * 0.55 + tilt.x * 1.1 + tilt.y * 0.7 + ripple * 0.06;
    float3 rainbow = 0.5 + 0.5 * cos(6.28318 * (phase + float3(0.0, 0.33, 0.67)));

    float brushed = 0.04 * sin(p.y / density * 1.7 + hash(float2(floor(p.y / density), 3.0)) * 6.0);
    float3 silver = float3(0.76, 0.78, 0.83) + brushed;

    float sheenPos = 0.5 + tilt.x * 0.9 - tilt.y * 0.3;
    float sheen = smoothstep(0.22, 0.0, abs(uv.x - uv.y * 0.35 - sheenPos + 0.17));

    float3 color = mix(silver, rainbow, 0.34) + sheen * 0.38;
    color += (hash(floor(p / density)) - 0.5) * 0.07;

    float cellSize = 13.0 * density;
    float2 cell = floor(p / cellSize);
    float seed = hash(cell + 7.0);
    float d = length(p - (cell + 0.5) * cellSize) / density;
    float twinkle = pow(max(0.0, sin(seed * 50.0 + tilt.x * 14.0 + tilt.y * 10.0)), 16.0);
    color += step(0.8, seed) * twinkle * smoothstep(2.6, 0.0, d);

    return half4(half3(clamp(color, 0.0, 1.0)), 1.0);
}
"""

/** Draws the holographic foil over the whole draw area for the given [tilt]. */
fun interface HoloFoilPainter {
    fun DrawScope.paint(tilt: DeviceTilt, alpha: Float)
}

@Composable
fun rememberHoloFoilPainter(): HoloFoilPainter = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShaderFoilPainter() else GradientFoilPainter()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderFoilPainter : HoloFoilPainter {
    private val shader = RuntimeShader(HOLO_FOIL_AGSL)
    private val brush = ShaderBrush(shader)

    override fun DrawScope.paint(tilt: DeviceTilt, alpha: Float) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("tilt", tilt.roll, tilt.pitch)
        shader.setFloatUniform("density", density)
        drawRect(brush, alpha = alpha)
    }
}

private class GradientFoilPainter : HoloFoilPainter {
    private val stops = listOf(
        Color(0xFFC9CCD3), Color(0xFFE6B8E8), Color(0xFFB8D8F0),
        Color(0xFFC8F0D0), Color(0xFFF0E6B8), Color(0xFFC9CCD3),
    )

    override fun DrawScope.paint(tilt: DeviceTilt, alpha: Float) {
        val shift = size.width * tilt.roll
        drawRect(
            brush = Brush.linearGradient(
                colors = stops,
                start = Offset(-size.width + shift, 0f),
                end = Offset(size.width * 2 + shift, size.height),
            ),
            alpha = alpha,
        )
    }
}
