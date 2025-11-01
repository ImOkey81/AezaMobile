package aeza.hostmaster.mobile.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import aeza.hostmaster.mobile.presentation.viewmodel.CheckViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CheckScreen(type: String, viewModel: CheckViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsState().value
    var target by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Проверка: ${type.uppercase()}", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Введите ссылку") })
        Button(onClick = { viewModel.submit(target, type) }) {
            Text("Проверить")
        }

        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> Text("Ошибка: ${state.error}")
            state.result != null -> Text("Результат:\n${state.result}")
        }
    }
}
