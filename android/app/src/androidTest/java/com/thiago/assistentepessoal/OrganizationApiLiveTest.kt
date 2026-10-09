package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.routine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

class OrganizationApiLiveTest{
    @Test fun categoryTotalsUseOnlyTheirOwnExpenses(){
        val today=LocalDate.parse("2026-10-08")
        fun expense(id:String,cents:Long,category:String,day:String="2026-10-08",archived:Boolean=false)=PersonalRecord(id,"expense","Teste","",cents,0,day,archived,"v",JSONObject().put("finance_category",category).toString())
        val rows=listOf(expense("food",1800,"food"),expense("transport",900,"transport"),expense("future",9999,"food","2026-10-09"),expense("archived",9999,"food",archived=true))
        assertEquals(1800L,monthlySpent(rows,today,"food"));assertEquals(2700L,monthlySpent(rows,today))
        assertEquals("other",financeCategory(PersonalRecord("old","expense","Teste","",500,0,"2026-10-08",false,"v")))
    }

    @Test fun seededFixturesAndNewPanelsWorkWithoutGeneration()=runBlocking(Dispatchers.IO){
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("koiSeedDemo")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val owner=requireNotNull(app.auth.account.value){"Entre na conta antes do teste."}.id
        val first=assistantRequest(app,owner,"demo")
        assertEquals(12,first.getInt("count"))
        val second=assistantRequest(app,owner,"demo")
        assertEquals(first.toString(),second.toString())
        app.getSharedPreferences("koi-demo",0).edit().putString("manifest-$owner",first.toString()).commit()
        val search=assistantRequest(app,owner,"search",JSONObject().put("query","Teste Lanche"))
        val foodId=first.getJSONArray("items").getJSONObject(2).getString("id")
        assertTrue((0 until search.getJSONArray("results").length()).any{search.getJSONArray("results").getJSONObject(it).getString("id")==foodId})
        val plan=assistantRequest(app,owner,"plan")
        assertTrue(plan.getInt("eligible_count")>=2)
        assertTrue(plan.getJSONArray("scheduled").length()<=12)
        assertTrue(plan.getJSONArray("priorities").length()<=8)
        assertEquals(owner,app.auth.account.value?.id)
    }
}
