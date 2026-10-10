package com.thiago.assistentepessoal

import android.animation.ValueAnimator
import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.*

object KoiColors {
    val Purple = Color(0xFFB18AFF)
    val Blue = Color(0xFF81ABFF)
    val Red = Color(0xFFFF5278)
    val Green = Color(0xFF56E39F)
    val Ink = Color(0xFF090B15)
    val Card = Color(0xFF181C2E)
    val Muted = Color(0xFFADB4CC)
}

val LocalKoiMotion = staticCompositionLocalOf { true }

@Composable
fun MotionEnvironment(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("koiwai-preferences", 0) }
    var reduced by remember { mutableStateOf(prefs.getBoolean("reduce-motion", false)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var systemEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(prefs, lifecycle) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "reduce-motion") reduced = prefs.getBoolean(key, false)
        }
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            systemEnabled = ValueAnimator.areAnimatorsEnabled()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalKoiMotion provides (!reduced && resumed && systemEnabled), content = content)
}

@Composable
fun motionPhase(duration: Int = 24000): State<Float> {
    if (!LocalKoiMotion.current) return remember { mutableFloatStateOf(0.25f) }
    return rememberInfiniteTransition(label = "koi ambience").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing)), label = "drift")
}

// Motion is read in the draw phase: backgrounds do not recompose the screen every frame.
@Composable
fun KoiBackdrop(scene: String) {
    val phase = motionPhase()
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(KoiColors.Ink, Color(0xFF111327), Color(0xFF090B15))))
        val p = phase.value * 2f * PI.toFloat()
        val w = size.width; val h = size.height
        val primary = when(scene) {"routine" -> KoiColors.Red; "memory", "facts", "reports" -> KoiColors.Blue; else -> KoiColors.Purple}
        val glow = Offset(w * (0.62f + sin(p) * 0.08f), h * 0.22f)
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = .19f), Color.Transparent), glow, w * .78f), w * .78f, glow)
        val blue = Offset(w * (.08f + cos(p) * .08f), h * .65f)
        drawCircle(Brush.radialGradient(listOf(KoiColors.Blue.copy(alpha = .12f), Color.Transparent), blue, w * .7f), w * .7f, blue)
        if (scene == "routine") {
            for (i in -3..10) {
                val x = i * w / 6f + phase.value * w / 6f
                drawLine(KoiColors.Red.copy(alpha = .055f), Offset(x, 0f), Offset(x - w, h), 1.dp.toPx())
            }
        } else if (scene == "settings" || scene == "account") {
            for (i in 0..3) drawCircle(KoiColors.Blue.copy(alpha = .065f), w * (.42f + i * .18f), Offset(w * .92f, h * .12f), style = Stroke(1.dp.toPx()))
        } else {
            for (i in 0..2) {
                val path = Path()
                path.moveTo(-w * .1f, h * (.24f + i * .025f))
                path.cubicTo(w * .35f, h * (.08f + sin(p + i) * .025f), w * .6f, h * .47f, w * 1.1f, h * .23f)
                drawPath(path, (if (i == 2) KoiColors.Blue else KoiColors.Purple).copy(alpha = .12f), style = Stroke(1.dp.toPx()))
            }
        }
        repeat(22) { i ->
            val x = ((i * .618034f) % 1f) * w + sin(p + i) * 7.dp.toPx()
            val y = ((i * .381966f + phase.value * .045f) % 1f) * h
            drawCircle((if (i % 4 == 0) KoiColors.Red else Color(0xFFB5A0FF)).copy(alpha = .15f + (sin(p + i) + 1f) * .12f), if (i % 3 == 0) 1.5.dp.toPx() else .8.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
fun KoiGlyph(kind: String, tint: Color = KoiColors.Purple, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(tint, Offset(w*x,h*y), Offset(w*xx,h*yy), 1.8.dp.toPx(), StrokeCap.Round)
        when(kind) {
            "home" -> { path.moveTo(w*.15f,h*.44f); path.lineTo(w*.5f,h*.13f); path.lineTo(w*.85f,h*.44f); path.lineTo(w*.85f,h*.87f); path.lineTo(w*.15f,h*.87f); path.close(); drawPath(path,tint,style=stroke); line(.42f,.87f,.42f,.62f); line(.42f,.62f,.59f,.62f) }
            "chat" -> { drawRoundRect(tint,Offset(w*.13f,h*.18f), androidx.compose.ui.geometry.Size(w*.74f,h*.56f), androidx.compose.ui.geometry.CornerRadius(w*.15f),style=stroke); line(.3f,.75f,.3f,.9f); repeat(3){drawCircle(tint,w*.035f,Offset(w*(.32f+it*.18f),h*.46f))} }
            "memory", "notes" -> { path.moveTo(w*.2f,h*.15f);path.lineTo(w*.75f,h*.15f);path.lineTo(w*.83f,h*.25f);path.lineTo(w*.83f,h*.85f);path.lineTo(w*.2f,h*.85f);path.close();drawPath(path,tint,style=stroke);line(.35f,.15f,.35f,.85f);line(.48f,.39f,.68f,.39f);line(.48f,.58f,.68f,.58f) }
            "routine", "tasks" -> { drawCircle(tint,w*.36f,Offset(w*.5f,h*.5f),style=stroke);line(.3f,.5f,.44f,.64f);line(.44f,.64f,.72f,.34f) }
            "settings" -> { repeat(3){val y=.25f+it*.25f;line(.14f,y,.86f,y);drawCircle(KoiColors.Ink,w*.085f,Offset(w*(if(it==1).65f else .35f),h*y));drawCircle(tint,w*.085f,Offset(w*(if(it==1).65f else .35f),h*y),style=stroke)} }
            "agenda" -> { drawRoundRect(tint,Offset(w*.16f,h*.23f), androidx.compose.ui.geometry.Size(w*.68f,h*.63f), androidx.compose.ui.geometry.CornerRadius(w*.08f),style=stroke);line(.32f,.12f,.32f,.32f);line(.68f,.12f,.68f,.32f);line(.16f,.43f,.84f,.43f) }
            "training" -> { line(.18f,.35f,.18f,.65f);line(.32f,.22f,.32f,.78f);line(.32f,.5f,.68f,.5f);line(.68f,.22f,.68f,.78f);line(.82f,.35f,.82f,.65f) }
            "finance" -> { line(.22f,.82f,.22f,.51f);line(.5f,.82f,.5f,.34f);line(.78f,.82f,.78f,.16f) }
            "back" -> {line(.7f,.5f,.25f,.5f);line(.25f,.5f,.46f,.29f);line(.25f,.5f,.46f,.71f)}
            "voice" -> {drawRoundRect(tint,Offset(w*.37f,h*.12f),androidx.compose.ui.geometry.Size(w*.26f,h*.46f),androidx.compose.ui.geometry.CornerRadius(w*.13f),style=stroke);drawArc(tint,0f,180f,false,Offset(w*.23f,h*.24f),androidx.compose.ui.geometry.Size(w*.54f,h*.48f),style=stroke);line(.5f,.72f,.5f,.88f);line(.35f,.88f,.65f,.88f)}
            "arrow" -> {line(.25f,.5f,.75f,.5f);line(.75f,.5f,.54f,.29f);line(.75f,.5f,.54f,.71f)}
            else -> { path.moveTo(w*.5f,h*.08f);path.lineTo(w*.61f,h*.39f);path.lineTo(w*.92f,h*.5f);path.lineTo(w*.61f,h*.61f);path.lineTo(w*.5f,h*.92f);path.lineTo(w*.39f,h*.61f);path.lineTo(w*.08f,h*.5f);path.lineTo(w*.39f,h*.39f);path.close();drawPath(path,tint) }
        }
    }
}

@Composable
fun KoiPanel(modifier: Modifier = Modifier, accent: Color = KoiColors.Purple, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, if (LocalKoiMotion.current) spring(stiffness=700f) else snap(), label="press")
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared=true }
    val entrance by animateFloatAsState(if(appeared) 1f else 0f, if(LocalKoiMotion.current) tween(380,easing=FastOutSlowInEasing) else snap(),label="card entrance")
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha=entrance; translationY=(1f-entrance)*12.dp.toPx() }.clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF20243B), KoiColors.Card.copy(alpha=.98f))))
        .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha=.23f), Color.White.copy(alpha=.055f))), RoundedCornerShape(24.dp))
        .then(if (onClick != null) Modifier.clickable(interactionSource=interaction, indication=androidx.compose.material3.ripple(), role=Role.Button, onClick=onClick) else Modifier)
        .padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp), content=content)
}

@Composable
fun Eyebrow(text: String, color: Color = KoiColors.Muted) {
    Text(text, color=color, fontSize=10.sp, letterSpacing=2.sp, fontWeight=FontWeight.Bold)
}

@Composable
fun KoiChip(text: String, color: Color = KoiColors.Purple) {
    Text(text, color=color, fontSize=11.sp, fontWeight=FontWeight.Medium,
        modifier=Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha=.12f)).border(1.dp,color.copy(alpha=.2f),RoundedCornerShape(50)).padding(horizontal=11.dp,vertical=6.dp))
}

@Composable
fun KoiAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, if (LocalKoiMotion.current) spring() else snap(), label="button")
    Button(onClick=onClick, enabled=enabled, interactionSource=interaction,
        modifier=modifier.heightIn(min=54.dp).graphicsLayer { scaleX=scale; scaleY=scale }
            .clip(RoundedCornerShape(18.dp)).background(if(enabled) Brush.horizontalGradient(listOf(Color(0xFF8548ED),Color(0xFF704CEC),Color(0xFF466CEA))) else Brush.horizontalGradient(listOf(KoiColors.Card,KoiColors.Card))),
        shape=RoundedCornerShape(18.dp), colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=Color.Transparent)) {
        Text(text, fontWeight=FontWeight.SemiBold)
    }
}

@Composable
fun KoiPageHeading(kicker:String, title:String, subtitle:String, accent:Color=KoiColors.Purple,kind:String="spark") {
    val phase=motionPhase(22000)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha=.17f),Color(0xFF151A2B))))
        .border(1.dp,accent.copy(alpha=.22f),RoundedCornerShape(28.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val w=size.width;val h=size.height
            repeat(7){i->drawLine(accent.copy(alpha=.07f),Offset(w*.55f+i*18.dp.toPx(),0f),Offset(w*.25f+i*24.dp.toPx(),h),1.dp.toPx())}
            drawCircle(accent.copy(alpha=.1f),h*.72f,Offset(w,h*(.6f+sin(phase.value*6.283f)*.04f)),style=Stroke(1.dp.toPx()))
        }
        Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Eyebrow(kicker,accent)
                Text(title,fontSize=27.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
                Text(subtitle,color=KoiColors.Muted,fontSize=13.sp,lineHeight=20.sp)
            }
            KoiSceneArtwork(kind,accent,Modifier.size(68.dp))
        }
    }
}

@Composable fun KoiSceneArtwork(kind:String,accent:Color,modifier:Modifier=Modifier.size(80.dp)){
    val phase=motionPhase(18000)
    Box(modifier,contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val w=size.width;val h=size.height;val stroke=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round)
            when(kind){
                "finance"->{repeat(4){i->val height=h*(.18f+i*.15f);drawRoundRect(accent.copy(alpha=.18f+i*.13f),Offset(w*(.1f+i*.2f),h*.86f-height),androidx.compose.ui.geometry.Size(w*.13f,height),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))};drawLine(Color.White.copy(alpha=.5f),Offset(w*.06f,h*.89f),Offset(w*.92f,h*.89f),1.dp.toPx())}
                "memory","notes"->{repeat(3){i->drawRoundRect(accent.copy(alpha=.18f+i*.2f),Offset(w*(.12f+i*.08f),h*(.08f+i*.1f)),androidx.compose.ui.geometry.Size(w*.62f,h*.6f),androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),style=stroke)};repeat(3){i->drawLine(Color.White.copy(alpha=.5f),Offset(w*.4f,h*(.44f+i*.11f)),Offset(w*.7f,h*(.44f+i*.11f)),1.dp.toPx())}}
                "training"->{repeat(3){i->drawArc(accent.copy(alpha=.3f+i*.2f),-90f,210f+sin(phase.value*6.283f)*35f,false,Offset(w*(.08f+i*.12f),h*(.08f+i*.12f)),androidx.compose.ui.geometry.Size(w*(.84f-i*.24f),h*(.84f-i*.24f)),style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))}}
                "settings"->{drawCircle(accent.copy(alpha=.5f),w*.32f,style=stroke);repeat(6){i->val angle=(i/6f+phase.value)*6.283f;val p=center+Offset(cos(angle)*w*.32f,sin(angle)*h*.32f);drawCircle(if(i%2==0)KoiColors.Blue else accent,4.dp.toPx(),p)};drawCircle(Color.White,w*.07f)}
                "tasks","agenda"->{drawRoundRect(accent.copy(alpha=.12f),Offset(w*.12f,h*.12f),androidx.compose.ui.geometry.Size(w*.76f,h*.76f),androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),style=stroke);drawLine(accent,Offset(w*.3f,h*.52f),Offset(w*.45f,h*.67f),3.dp.toPx(),StrokeCap.Round);drawLine(accent,Offset(w*.45f,h*.67f),Offset(w*.76f,h*.34f),3.dp.toPx(),StrokeCap.Round)}
                else->{drawCircle(accent.copy(alpha=.14f),w*.45f);drawCircle(accent.copy(alpha=.7f),w*.35f,style=stroke);drawArc(KoiColors.Blue,phase.value*360f,110f,false,Offset(w*.06f,h*.06f),androidx.compose.ui.geometry.Size(w*.88f,h*.88f),style=stroke)}
            }
        }
    }
}

@Composable
fun KoiMenuRow(title:String, subtitle:String, icon:String, accent:Color=KoiColors.Purple, onClick:()->Unit) {
    KoiPanel(Modifier.fillMaxWidth(),accent,onClick) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha=.1f)),contentAlignment=Alignment.Center) {
                KoiGlyph(icon,accent)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                Text(subtitle,fontSize=12.sp,lineHeight=17.sp,color=KoiColors.Muted)
            }
            KoiGlyph("arrow",accent,Modifier.size(18.dp))
        }
    }
}

@Composable
fun KoiDisclosure(title:String, subtitle:String, icon:String="spark", accent:Color=KoiColors.Purple, content:@Composable ColumnScope.()->Unit) {
    var open by rememberSaveable {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        KoiMenuRow(title,if(open) "Toque para recolher" else subtitle,icon,accent){open=!open}
        val motion=LocalKoiMotion.current
        AnimatedVisibility(open,enter=expandVertically(tween(if(motion)240 else 0))+fadeIn(tween(if(motion)180 else 0)),exit=shrinkVertically(tween(if(motion)180 else 0))+fadeOut(tween(if(motion)120 else 0))) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}

@Composable
fun OrbitEmblem(kind: String, accent: Color, modifier: Modifier = Modifier.size(100.dp)) {
    val phase = motionPhase(16000)
    Box(modifier, contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius=size.minDimension*.4f
            drawCircle(accent.copy(alpha=.09f),radius)
            drawCircle(accent.copy(alpha=.2f),radius,style=Stroke(1.dp.toPx()))
            drawArc(accent.copy(alpha=.75f),phase.value*360f,85f,false,Offset(size.width*.1f,size.height*.1f),androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
            val angle=phase.value*2*PI
            drawCircle(Color.White,2.5.dp.toPx(),center+Offset(cos(angle).toFloat()*radius,sin(angle).toFloat()*radius))
        }
        KoiGlyph(kind,accent,Modifier.fillMaxSize(.35f))
    }
}
