package com.example.smartdosing.ui.screens.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTheme

/**
 * 配方管理页面
 * 显示配方列表、搜索、分类等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onNavigateToRecipeDetail: (String) -> Unit = {},
    onNavigateToCreateRecipe: () -> Unit = {},
    onNavigateToDosingOperation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 页面标题和搜索栏
        RecipesHeader(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            onCreateRecipe = onNavigateToCreateRecipe
        )

        // 配方分类筛选
        RecipeCategoryFilter()

        // 配方列表
        RecipesList(
            onRecipeClick = onNavigateToRecipeDetail,
            onStartDosing = onNavigateToDosingOperation
        )
    }
}

/**
 * 配方页面头部
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesHeader(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onCreateRecipe: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题和新建按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "配方管理",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )

            Button(
                onClick = onCreateRecipe,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建配方",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "新建配方",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 搜索栏
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "搜索配方名称、材料...",
                    color = Color(0xFF757575)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = Color(0xFF757575)
                )
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchTextChange("") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除搜索",
                            tint = Color(0xFF757575)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1976D2),
                unfocusedBorderColor = Color(0xFFE0E0E0)
            ),
            singleLine = true
        )
    }
}

/**
 * 配方分类筛选
 */
@Composable
fun RecipeCategoryFilter() {
    val categories = listOf("全部", "香精", "酸类", "甜味剂", "其他")
    var selectedCategory by remember { mutableStateOf("全部") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selected = selectedCategory == category,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1976D2),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF757575)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == category
                        )
                    )
                }
            }
        }
    }
}

/**
 * 配方列表
 */
@Composable
fun RecipesList(
    onRecipeClick: (String) -> Unit,
    onStartDosing: (String) -> Unit
) {
    // 模拟配方数据
    val sampleRecipes = listOf(
        RecipeItemData("1", "苹果香精配方", "香精类", 5, "1.5KG", "2024-01-15"),
        RecipeItemData("2", "柠檬酸配方", "酸类", 3, "2.3KG", "2024-01-14"),
        RecipeItemData("3", "甜蜜素配方", "甜味剂", 4, "0.8KG", "2024-01-13"),
        RecipeItemData("4", "综合调味配方", "其他", 8, "5.2KG", "2024-01-12"),
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sampleRecipes.size) { index ->
            RecipeCard(
                recipe = sampleRecipes[index],
                onRecipeClick = onRecipeClick,
                onStartDosing = onStartDosing
            )
        }
    }
}

/**
 * 配方数据类
 */
data class RecipeItemData(
    val id: String,
    val name: String,
    val category: String,
    val materialCount: Int,
    val totalWeight: String,
    val lastUsed: String
)

/**
 * 配方卡片组件
 */
@Composable
fun RecipeCard(
    recipe: RecipeItemData,
    onRecipeClick: (String) -> Unit,
    onStartDosing: (String) -> Unit
) {
    Card(
        onClick = { onRecipeClick(recipe.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 配方基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = recipe.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recipe.category,
                            fontSize = 14.sp,
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${recipe.materialCount} 种材料",
                            fontSize = 14.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }

                // 开始投料按钮
                Button(
                    onClick = { onStartDosing(recipe.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "开始投料",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "投料",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 配方统计信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    label = "总重量",
                    value = recipe.totalWeight,
                    icon = Icons.Default.Build
                )
                InfoItem(
                    label = "最后使用",
                    value = recipe.lastUsed,
                    icon = Icons.Default.Info
                )
                InfoItem(
                    label = "材料数",
                    value = "${recipe.materialCount}种",
                    icon = Icons.Default.List
                )
            }
        }
    }
}

/**
 * 信息项组件
 */
@Composable
fun InfoItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF757575),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF263238)
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun RecipesScreenPreview() {
    SmartDosingTheme {
        RecipesScreen()
    }
}