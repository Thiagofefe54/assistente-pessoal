package com.thiago.assistentepessoal.routine

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box

@Composable
fun FinanceCategoryPicker(selected:String,onSelect:(String)->Unit,includeAll:Boolean=false){
    var open by remember{mutableStateOf(false)}
    Box{
        TextButton(onClick={open=true}){Text("Categoria: ${financeLabel(selected)} ▾")}
        DropdownMenu(expanded=open,onDismissRequest={open=false}){
            if(includeAll)DropdownMenuItem(text={Text("Total do mês / todas")},onClick={onSelect("all");open=false})
            financeCategories.forEach{(id,label)->DropdownMenuItem(text={Text(label)},onClick={onSelect(id);open=false})}
        }
    }
}
