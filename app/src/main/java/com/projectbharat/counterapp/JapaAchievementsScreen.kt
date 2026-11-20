package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectbharat.counterapp.data.CounterLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.NumberFormat

private data class JapaBadge(val titleMr:String,val titleEn:String,val descMr:String,val descEn:String,val threshold:Int,val group:String)
private val japaBadgeList = listOf(
 JapaBadge("जप आरंभ","Japa Begin","१०८ जप पूर्ण","Complete 108 repetitions",108,"JAPA"),
 JapaBadge("नियमित जप","Regular Japa","११ माळा पूर्ण","Complete 11 malas",1188,"JAPA"),
 JapaBadge("जप निष्ठा","Japa Devotion","२१ माळा पूर्ण","Complete 21 malas",2268,"JAPA"),
 JapaBadge("जप संकल्प","Japa Sankalp","५१ माळा पूर्ण","Complete 51 malas",5508,"JAPA"),
 JapaBadge("साधना पथ","Sadhana Path","१०८ माळा पूर्ण","Complete 108 malas",11664,"JAPA"),
 JapaBadge("साधना समर्पण","Sadhana Dedication","५०१ माळा पूर्ण","Complete 501 malas",54108,"JAPA"),
 JapaBadge("महासाधना","Maha Sadhana","१००८ माळा पूर्ण","Complete 1,008 malas",108864,"JAPA"),
 JapaBadge("महासंकल्प","Maha Sankalp","१०,००८ माळा पूर्ण","Complete 10,008 malas",1080864,"JAPA"),
 JapaBadge("तीन दिवस सातत्य","3-Day Streak","सलग ३ दिवस जप","Chant on 3 consecutive days",3,"STREAK"),
 JapaBadge("सप्ताह साधना","7-Day Streak","सलग ७ दिवस जप","Chant on 7 consecutive days",7,"STREAK"),
 JapaBadge("एकवीस दिवस","21-Day Streak","सलग २१ दिवस जप","Chant on 21 consecutive days",21,"STREAK"),
 JapaBadge("चाळीस दिवस","40-Day Streak","सलग ४० दिवस जप","Chant on 40 consecutive days",40,"STREAK")
)
private fun longestStreak(days:List<LocalDate>):Int {
 if(days.isEmpty()) return 0
 val sorted=days.distinct().sorted(); var best=1; var current=1
 for(i in 1 until sorted.size) { current=if(sorted[i]==sorted[i-1].plusDays(1)) current+1 else 1; best=maxOf(best,current) }
 return best
}
private fun dateEarned(logs:List<CounterLog>,goal:Int):LocalDate? {
 var sum=0
 logs.filter{it.note?.startsWith("MANTRA:")==true}.sortedBy{it.date}.forEach { log ->
  sum+=log.count
  if(sum>=goal) return runCatching{LocalDate.parse(log.date)}.getOrNull()
 }
 return null
}
@Composable
fun JapaAchievementsScreen(logs:List<CounterLog>,english:Boolean,ink:Color,onBack:()->Unit) {
 val entries=logs.filter{it.note?.startsWith("MANTRA:")==true&&it.count>0}
 val total=entries.sumOf{it.count}
 val activeDates=entries.mapNotNull{runCatching{LocalDate.parse(it.date)}.getOrNull()}.distinct()
 val streak=longestStreak(activeDates)
 val badges=japaBadgeList.map{b->b to (if(b.group=="JAPA") total>=b.threshold else streak>=b.threshold)}
 val earned=badges.count{it.second}
 val fmt=NumberFormat.getIntegerInstance()
 val dateFmt=DateTimeFormatter.ofPattern("dd MMM yyyy")
 val next=japaBadgeList.firstOrNull{it.group=="JAPA"&&total<it.threshold}
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,contentDescription=if(english)"Back" else "मागे",tint=ink)}
   Text(if(english)"Japa Achievements" else "जप उपलब्धी",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=ink)
  }
  LazyColumn(contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item {
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
     Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
      Icon(Icons.Default.EmojiEvents,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
      Text(if(english)"Your Japa Journey" else "तुमची जप साधना",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
      Text(fmt.format(total),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
      Text(if(english)"repetitions completed" else "जप पूर्ण")
      Spacer(Modifier.height(8.dp))
      Text(if(english)"$earned of ${japaBadgeList.size} achievements unlocked" else "${japaBadgeList.size} पैकी $earned उपलब्धी मिळवल्या")
      LinearProgressIndicator(progress={earned.toFloat()/japaBadgeList.size},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
      Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
       Column(horizontalAlignment=Alignment.CenterHorizontally){Text(streak.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(if(english)"Best streak" else "सर्वोत्तम सलग दिवस")}
       Column(horizontalAlignment=Alignment.CenterHorizontally){Text(activeDates.size.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(if(english)"Active days" else "जप केलेले दिवस")}
      }
     }
    }
   }
   if(next!=null)item {
    Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(14.dp)){
     Text(if(english)"Next milestone: ${next.titleEn}" else "पुढील टप्पा: ${next.titleMr}",fontWeight=FontWeight.Bold)
     Text(if(english)"${fmt.format(next.threshold-total)} repetitions to go" else "आणखी ${fmt.format(next.threshold-total)} जप बाकी")
     LinearProgressIndicator(progress={ (total.toFloat()/next.threshold).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
    }}
   }
   item{Text(if(english)"Japa Milestones" else "जप टप्पे",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=ink)}
   items(badges.filter{it.first.group=="JAPA"}){(b,unlocked)->AchievementTile(b,unlocked,if(unlocked)dateEarned(entries,b.threshold) else null,english,ink,dateFmt)}
   item{Text(if(english)"Consistency Streaks" else "सातत्य उपलब्धी",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=ink)}
   items(badges.filter{it.first.group=="STREAK"}){(b,unlocked)->AchievementTile(b,unlocked,null,english,ink,dateFmt)}
  }
 }
}
@Composable
private fun AchievementTile(b:JapaBadge,unlocked:Boolean,date:LocalDate?,english:Boolean,ink:Color,dateFmt:DateTimeFormatter) {
 Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(unlocked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f))) {
  Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
   Icon(Icons.Default.EmojiEvents,null,Modifier.size(36.dp),tint=if(unlocked)MaterialTheme.colorScheme.secondary else ink.copy(alpha=.3f))
   Spacer(Modifier.width(12.dp))
   Column(Modifier.weight(1f)) {
    Text(if(english)b.titleEn else b.titleMr,fontWeight=FontWeight.Bold,color=ink)
    Text(if(english)b.descEn else b.descMr,style=MaterialTheme.typography.bodySmall,color=ink.copy(alpha=.7f))
    if(unlocked&&date!=null)Text((if(english)"Earned " else "मिळवले: ")+date.format(dateFmt),style=MaterialTheme.typography.labelSmall,color=ink.copy(alpha=.65f))
   }
   Text(if(unlocked)"✓" else "🔒",style=MaterialTheme.typography.titleMedium,color=ink.copy(alpha=if(unlocked)1f else .55f))
  }
 }
}
