package com.atlasglobe.app.ui.screens.register

import androidx.compose.foundation.background
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun LineField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, error: Boolean = false
) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder, color = Atlas.Grey) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        isError = error,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Atlas.Grey,
            errorIndicatorColor = Atlas.Alert, cursorColor = Color.White,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Bouton blanc rectangulaire. */
@Composable
fun WhiteButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White, contentColor = Color.Black,
            disabledContainerColor = Color(0xFF333333), disabledContentColor = Atlas.Grey),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 16.sp) }
}

@Composable
fun ListRow(text: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(text, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        HorizontalDivider(color = Atlas.Line)
    }
}

@Composable
fun Dots(visible: Int) {
    Row { repeat(3) { Text(".", color = if (it < visible) Color.White else Color.Transparent, fontSize = 22.sp) } }
}
