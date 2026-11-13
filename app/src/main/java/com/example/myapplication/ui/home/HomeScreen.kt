package com.example.myapplication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val folders by viewModel.folders.collectAsState()
    val black = colorResource(R.color.black)
    val white = colorResource(R.color.white)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "glass",
                color = white,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::addFolder) {
                Text(text = "+", color = white, fontSize = 24.sp)
            }
        }

        HorizontalDivider(color = white)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(folders, key = { it.path }) { folder ->
                Text(
                    text = folder.name,
                    color = white,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = white.copy(alpha = 0.2f))
            }
        }
    }
}
